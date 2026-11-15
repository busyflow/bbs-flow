package ans.bedrock;

import ans.molang.MolangScope;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Evaluates Bedrock {@code RenderController} rules and renders animated {@link BedrockGeometry}
 * meshes using Minecraft {@link MatrixStack} and {@link VertexConsumerProvider}.
 */
public final class BedrockModelRenderer {

    public record ResolvedPass(
        BedrockRenderController controller,
        BedrockGeometry geometry,
        Identifier textureId,
        String texturePath,
        Map<String, String> defMaterials,
        float r,
        float g,
        float b,
        float a,
        float uvOffU,
        float uvOffV,
        float uvScaleU,
        float uvScaleV,
        boolean ignoreLighting
    ) {
    }

    private static final float[] TMP_RGBA = new float[4];

    public List<ResolvedPass> evaluateRenderPasses(BedrockEntityDef entityDef,
                                                   BedrockEntityState state,
                                                   BedrockPackManager pack) {
        return evaluatePassesInternal(
            entityDef.renderControllers,
            entityDef.geometry,
            entityDef.textures,
            entityDef.materials,
            state,
            pack
        );
    }

    public List<ResolvedPass> evaluateAttachablePasses(BedrockAttachableDef attDef,
                                                       BedrockEntityState state,
                                                       BedrockPackManager pack) {
        return evaluatePassesInternal(
            attDef.renderControllers,
            attDef.geometry,
            attDef.textures,
            attDef.materials,
            state,
            pack
        );
    }

    private List<ResolvedPass> evaluatePassesInternal(List<BedrockEntityDef.RenderControllerRef> rcRefs,
                                                      Map<String, String> geoMap,
                                                      Map<String, String> texMap,
                                                      Map<String, String> matMap,
                                                      BedrockEntityState state,
                                                      BedrockPackManager pack) {
        if (rcRefs.isEmpty()) return List.of();
        List<ResolvedPass> passes = new ArrayList<>(rcRefs.size());
        MolangScope scope = state.scope;

        for (BedrockEntityDef.RenderControllerRef ref : rcRefs) {
            if (!ref.condition().evaluateBoolean(scope)) continue;
            BedrockRenderController rc = pack.renderControllers.get(ref.controllerLower());
            if (rc == null) continue;

            rc.populateArrays(scope);

            String geoSymbol = rc.resolveGeometrySymbol(scope);
            String fullGeoId = geoMap.getOrDefault(geoSymbol, geoSymbol);
            BedrockGeometry geo = pack.geometries.get(fullGeoId);
            if (geo == null && !geoMap.isEmpty()) {
                String fallbackGeo = geoMap.getOrDefault("default", geoMap.values().iterator().next());
                geo = pack.geometries.get(fallbackGeo);
            }
            if (geo == null) continue;

            List<String> texSymbols = rc.resolveTextureSymbols(scope);
            String rawTex = "";
            for (String sym : texSymbols) {
                String mapped = texMap.getOrDefault(sym, sym);
                if (!mapped.isEmpty()) {
                    rawTex = mapped;
                    break;
                }
            }
            if (rawTex.isEmpty() && !texMap.isEmpty()) {
                rawTex = texMap.getOrDefault("default", texMap.values().iterator().next());
            }
            String resolvedTexPath = pack.resolveTexturePath(rawTex);
            if (resolvedTexPath.isEmpty()) continue;
            Identifier texId = Identifier.tryParse("minecraft:" + resolvedTexPath);
            if (texId == null) continue;

            float r = 1f, g = 1f, b = 1f, a = 1f;
            if (rc.color != null) {
                rc.color.eval(scope, TMP_RGBA);
                r = clamp01(TMP_RGBA[0]);
                g = clamp01(TMP_RGBA[1]);
                b = clamp01(TMP_RGBA[2]);
                a = clamp01(TMP_RGBA[3]);
            }
            if (rc.overlayColor != null) {
                rc.overlayColor.eval(scope, TMP_RGBA);
                float oa = clamp01(TMP_RGBA[3]);
                if (oa > 0.001f) {
                    r = lerp(r, clamp01(TMP_RGBA[0]), oa);
                    g = lerp(g, clamp01(TMP_RGBA[1]), oa);
                    b = lerp(b, clamp01(TMP_RGBA[2]), oa);
                }
            }
            if (state.queryContext.hurtTime > 0 && rc.isHurtColor != null) {
                rc.isHurtColor.eval(scope, TMP_RGBA);
                float ha = clamp01(TMP_RGBA[3]);
                if (ha > 0.001f) {
                    r = lerp(r, clamp01(TMP_RGBA[0]), ha);
                    g = lerp(g, clamp01(TMP_RGBA[1]), ha);
                    b = lerp(b, clamp01(TMP_RGBA[2]), ha);
                }
            }
            if (state.queryContext.onFire && rc.onFireColor != null) {
                rc.onFireColor.eval(scope, TMP_RGBA);
                float fa = clamp01(TMP_RGBA[3]);
                if (fa > 0.001f) {
                    r = lerp(r, clamp01(TMP_RGBA[0]), fa);
                    g = lerp(g, clamp01(TMP_RGBA[1]), fa);
                    b = lerp(b, clamp01(TMP_RGBA[2]), fa);
                }
            }
            if (a <= 0.003f) continue;

            float offU = rc.uvOffsetU != null ? rc.uvOffsetU.evaluateFloat(scope) : 0f;
            float offV = rc.uvOffsetV != null ? rc.uvOffsetV.evaluateFloat(scope) : 0f;
            float sclU = rc.uvScaleU != null ? rc.uvScaleU.evaluateFloat(scope) : 1f;
            float sclV = rc.uvScaleV != null ? rc.uvScaleV.evaluateFloat(scope) : 1f;
            if (!Float.isFinite(offU)) offU = 0f;
            if (!Float.isFinite(offV)) offV = 0f;
            if (!Float.isFinite(sclU) || sclU == 0f) sclU = 1f;
            if (!Float.isFinite(sclV) || sclV == 0f) sclV = 1f;

            passes.add(new ResolvedPass(
                rc, geo, texId, resolvedTexPath, matMap,
                r, g, b, a, offU, offV, sclU, sclV, rc.ignoreLighting
            ));
        }
        return passes;
    }

    public boolean renderEntity(BedrockEntityDef entityDef,
                                BedrockEntityState state,
                                BedrockPackManager pack,
                                float bodyYawDeg,
                                MatrixStack matrices,
                                VertexConsumerProvider consumers,
                                int light,
                                int overlay) {
        return renderEntity(null, entityDef, state, pack, bodyYawDeg, matrices, consumers, light, overlay);
    }

    public boolean renderEntity(Entity entity,
                                BedrockEntityDef entityDef,
                                BedrockEntityState state,
                                BedrockPackManager pack,
                                float bodyYawDeg,
                                MatrixStack matrices,
                                VertexConsumerProvider consumers,
                                int light,
                                int overlay) {
        List<ResolvedPass> passes = evaluateRenderPasses(entityDef, state, pack);
        if (passes.isEmpty()) return false;

        float scale = sanitizeScale(entityDef.scaleExpr.evaluateFloat(state.scope), 1f);
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - bodyYawDeg));
        if (scale != 1f) {
            matrices.scale(scale, scale, scale);
        }

        // Build primary skeleton once so attachables & bone queries match current pass
        BedrockGeometry firstGeo = resolveEntityGeometry(entity, passes.get(0).geometry(), pack);
        state.buildBoneMatrices(firstGeo, null);

        BedrockGeometry lastBuiltGeo = firstGeo;
        for (ResolvedPass pass : passes) {
            BedrockGeometry passGeo = resolveEntityGeometry(entity, pass.geometry(), pack);
            if (passGeo != lastBuiltGeo) {
                state.buildBoneMatrices(passGeo, null);
                lastBuiltGeo = passGeo;
            }
            renderPass(entity, pass, passGeo, state, pack, matrices, consumers, light, overlay);
        }

        matrices.pop();
        return true;
    }

    private static BedrockGeometry resolveEntityGeometry(Entity entity, BedrockGeometry geo, BedrockPackManager pack) {
        if (entity instanceof AbstractClientPlayerEntity player
            && geo != null
            && "geometry.humanoid.custom".equalsIgnoreCase(geo.identifier)
            && "slim".equals(player.getModel())) {
            BedrockGeometry slim = pack.geometries.get("geometry.humanoid.customslim");
            if (slim != null) {
                return slim;
            }
        }
        return geo;
    }

    public boolean renderAttachable(BedrockAttachableDef attDef,
                                    BedrockEntityState attState,
                                    BedrockEntityState ownerState,
                                    BedrockPackManager pack,
                                    MatrixStack matrices,
                                    VertexConsumerProvider consumers,
                                    int light,
                                    int overlay) {
        List<ResolvedPass> passes = evaluateAttachablePasses(attDef, attState, pack);
        if (passes.isEmpty()) return false;

        float scale = sanitizeScale(attDef.scaleExpr.evaluateFloat(attState.scope), 1f);
        matrices.push();
        if (scale != 1f) {
            matrices.scale(scale, scale, scale);
        }

        for (ResolvedPass pass : passes) {
            attState.buildBoneMatrices(pass.geometry(), ownerState);
            renderPass(null, pass, pass.geometry(), attState, pack, matrices, consumers, light, overlay);
        }

        matrices.pop();
        return true;
    }

    private void renderPass(Entity entity,
                            ResolvedPass pass,
                            BedrockGeometry geo,
                            BedrockEntityState state,
                            BedrockPackManager pack,
                            MatrixStack matrices,
                            VertexConsumerProvider consumers,
                            int light,
                            int overlay) {
        MolangScope scope = state.scope;
        MatrixStack.Entry baseEntry = matrices.peek();
        Matrix4f basePosMat = baseEntry.getPositionMatrix();
        Matrix3f baseNormMat = baseEntry.getNormalMatrix();

        Matrix4f workPos = new Matrix4f();
        Matrix3f workNorm = new Matrix3f();

        for (BedrockGeometry.Bone bone : geo.allBones) {
            if (bone.cubes.isEmpty()) continue;
            if (!pass.controller().isBoneVisible(bone.nameLower, scope)) continue;

            Matrix4f boneMat = state.getBoneMatrix(bone.nameLower);
            if (boneMat == null) continue;

            String matAlias = pass.controller().resolveMaterialForBone(bone.nameLower, scope);
            String fullMatName = pass.defMaterials().getOrDefault(matAlias,
                pass.defMaterials().getOrDefault("default", matAlias));
            BedrockMaterialDef matDef = pack.getMaterial(fullMatName);
            if (matDef.isColorWriteDisabled()) continue;

            Identifier texId = pass.textureId();
            if (entity instanceof AbstractClientPlayerEntity player) {
                if (pass.texturePath().contains("cape_invisible")) {
                    Identifier capeTex = player.getCapeTexture();
                    if (capeTex == null) continue;
                    texId = capeTex;
                } else if (pass.texturePath().contains("textures/entity/steve")
                    || pass.texturePath().contains("textures/entity/alex")) {
                    texId = player.getSkinTexture();
                }
            }

            boolean useUvAnim = matDef.isUseUvAnim();
            boolean cull = !matDef.isDisableCulling();
            RenderLayer layer = selectRenderLayer(matDef, texId, cull, pass.uvOffU(), pass.uvOffV());
            VertexConsumer vc = consumers.getBuffer(layer);

            int effLight = (pass.ignoreLighting() || matDef.isEmissive())
                ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light;
            int effOverlay = (matDef.isEmissive() || matDef.isAdditive())
                ? OverlayTexture.DEFAULT_UV : overlay;

            int ir = Math.round(pass.r() * 255f);
            int ig = Math.round(pass.g() * 255f);
            int ib = Math.round(pass.b() * 255f);
            int ia = Math.round(pass.a() * 255f);

            float offU = useUvAnim ? pass.uvOffU() : 0f;
            float offV = useUvAnim ? pass.uvOffV() : 0f;
            float sclU = useUvAnim ? pass.uvScaleU() : 1f;
            float sclV = useUvAnim ? pass.uvScaleV() : 1f;

            for (BedrockGeometry.Cube cube : bone.cubes) {
                workPos.set(basePosMat).mul(boneMat);
                if (cube.rotation[0] != 0f || cube.rotation[1] != 0f || cube.rotation[2] != 0f) {
                    float cpx = cube.pivot[0] / 16f;
                    float cpy = cube.pivot[1] / 16f;
                    float cpz = cube.pivot[2] / 16f;
                    workPos.translate(cpx, cpy, cpz);
                    if (cube.rotation[2] != 0f) workPos.rotateZ((float) Math.toRadians(cube.rotation[2]));
                    if (cube.rotation[1] != 0f) workPos.rotateY((float) Math.toRadians(cube.rotation[1]));
                    if (cube.rotation[0] != 0f) workPos.rotateX((float) Math.toRadians(cube.rotation[0]));
                    workPos.translate(-cpx, -cpy, -cpz);
                }
                workNorm.set(baseNormMat);
                Matrix3f boneNorm = new Matrix3f();
                workPos.normal(boneNorm);
                if (!Float.isFinite(boneNorm.m00)) {
                    boneNorm.set(baseNormMat);
                }
                emitCube(vc, workPos, boneNorm, cube, offU, offV, sclU, sclV, ir, ig, ib, ia, effOverlay, effLight);
            }
        }
    }

    private static RenderLayer selectRenderLayer(BedrockMaterialDef mat, Identifier tex, boolean cull, float uOff, float vOff) {
        return switch (mat.getRenderMode()) {
            case GLINT -> RenderLayer.getEntityGlint();
            case ADDITIVE -> RenderLayer.getEnergySwirl(tex, uOff, vOff);
            case EMISSIVE -> RenderLayer.getEyes(tex);
            case EMISSIVE_ALPHA -> RenderLayer.getEntityTranslucentEmissive(tex);
            case ALPHA_BLEND -> cull ? RenderLayer.getEntityTranslucentCull(tex) : RenderLayer.getEntityTranslucent(tex);
            case ALPHA_TEST -> cull ? RenderLayer.getEntityCutout(tex) : RenderLayer.getEntityCutoutNoCull(tex);
            case OPAQUE -> cull ? RenderLayer.getEntityCutout(tex) : RenderLayer.getEntityCutoutNoCull(tex);
        };
    }

    private static void emitCube(VertexConsumer vc,
                                 Matrix4f posMat,
                                 Matrix3f normMat,
                                 BedrockGeometry.Cube cube,
                                 float offU, float offV, float sclU, float sclV,
                                 int r, int g, int b, int a,
                                 int overlay, int light) {
        float x0 = cube.minX / 16f;
        float y0 = cube.minY / 16f;
        float z0 = cube.minZ / 16f;
        float x1 = cube.maxX / 16f;
        float y1 = cube.maxY / 16f;
        float z1 = cube.maxZ / 16f;

        emitFace(vc, posMat, normMat, cube.faces.get(BedrockGeometry.FaceDir.NORTH),
            x1, y1, z0,  x1, y0, z0,  x0, y0, z0,  x0, y1, z0,
            0f, 0f, -1f, offU, offV, sclU, sclV, r, g, b, a, overlay, light);

        emitFace(vc, posMat, normMat, cube.faces.get(BedrockGeometry.FaceDir.SOUTH),
            x0, y1, z1,  x0, y0, z1,  x1, y0, z1,  x1, y1, z1,
            0f, 0f, 1f, offU, offV, sclU, sclV, r, g, b, a, overlay, light);

        emitFace(vc, posMat, normMat, cube.faces.get(BedrockGeometry.FaceDir.EAST),
            x1, y1, z1,  x1, y0, z1,  x1, y0, z0,  x1, y1, z0,
            1f, 0f, 0f, offU, offV, sclU, sclV, r, g, b, a, overlay, light);

        emitFace(vc, posMat, normMat, cube.faces.get(BedrockGeometry.FaceDir.WEST),
            x0, y1, z0,  x0, y0, z0,  x0, y0, z1,  x0, y1, z1,
            -1f, 0f, 0f, offU, offV, sclU, sclV, r, g, b, a, overlay, light);

        emitFace(vc, posMat, normMat, cube.faces.get(BedrockGeometry.FaceDir.UP),
            x1, y1, z1,  x1, y1, z0,  x0, y1, z0,  x0, y1, z1,
            0f, 1f, 0f, offU, offV, sclU, sclV, r, g, b, a, overlay, light);

        emitFace(vc, posMat, normMat, cube.faces.get(BedrockGeometry.FaceDir.DOWN),
            x1, y0, z0,  x1, y0, z1,  x0, y0, z1,  x0, y0, z0,
            0f, -1f, 0f, offU, offV, sclU, sclV, r, g, b, a, overlay, light);
    }

    private static void emitFace(VertexConsumer vc,
                                 Matrix4f posMat,
                                 Matrix3f normMat,
                                 BedrockGeometry.FaceUv uv,
                                 float x0, float y0, float z0,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 float x3, float y3, float z3,
                                 float nx, float ny, float nz,
                                 float offU, float offV, float sclU, float sclV,
                                 int r, int g, int b, int a,
                                 int overlay, int light) {
        if (uv == null) return;
        float uA = offU + uv.u0 * sclU;
        float vA = offV + uv.v0 * sclV;
        float uB = offU + uv.u1 * sclU;
        float vB = offV + uv.v1 * sclV;

        float[] cu = {uA, uA, uB, uB};
        float[] cv = {vA, vB, vB, vA};
        int shift = Math.floorMod(uv.rotationDeg / 90, 4);

        Vector3f n = normMat.transform(new Vector3f(nx, ny, nz)).normalize();
        Vector4f p = new Vector4f();

        p.set(x0, y0, z0, 1f).mul(posMat);
        vc.vertex(p.x, p.y, p.z).color(r, g, b, a).texture(cu[shift], cv[shift]).overlay(overlay).light(light).normal(n.x, n.y, n.z).next();

        p.set(x1, y1, z1, 1f).mul(posMat);
        int i1 = (1 + shift) & 3;
        vc.vertex(p.x, p.y, p.z).color(r, g, b, a).texture(cu[i1], cv[i1]).overlay(overlay).light(light).normal(n.x, n.y, n.z).next();

        p.set(x2, y2, z2, 1f).mul(posMat);
        int i2 = (2 + shift) & 3;
        vc.vertex(p.x, p.y, p.z).color(r, g, b, a).texture(cu[i2], cv[i2]).overlay(overlay).light(light).normal(n.x, n.y, n.z).next();

        p.set(x3, y3, z3, 1f).mul(posMat);
        int i3 = (3 + shift) & 3;
        vc.vertex(p.x, p.y, p.z).color(r, g, b, a).texture(cu[i3], cv[i3]).overlay(overlay).light(light).normal(n.x, n.y, n.z).next();
    }

    private static float clamp01(float v) {
        return Float.isFinite(v) ? Math.max(0f, Math.min(1f, v)) : 1f;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float sanitizeScale(float v, float fallback) {
        return (Float.isFinite(v) && v > 0.0001f && v < 100f) ? v : fallback;
    }
}
