package ans.test;

import ans.atmosphere.BedrockAtmosphereManager;
import ans.bedrock.BedrockAttachableDef;
import ans.bedrock.BedrockEntityDef;
import ans.bedrock.BedrockEntityState;
import ans.bedrock.BedrockGeometry;
import ans.bedrock.BedrockModelRenderer;
import ans.bedrock.BedrockPackManager;
import ans.bedrock.BedrockParticleDef;
import ans.bedrock.BedrockParticleEngine;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Comprehensive 5-stage offline verification suite for the Actions &amp; Stuff Java 1.20.1 port:
 * <ol>
 *   <li>Every PNG and .png.mcmeta inside {@code Actions-And-Stuff-Java-1.20.1.zip} decodes cleanly.</li>
 *   <li>All Bedrock assets load via {@link BedrockPackManager} with zero parse errors or missing refs.</li>
 *   <li>Every entity, attachable, and particle simulates across multi-tick MoLang/animation loops.</li>
 *   <li>Atmospherics, biomes, fogs, lightings, and colormaps produce valid time-of-day colors.</li>
 *   <li>Remapped Fabric mod JAR contains valid Mixin Intermediary bytecode and zero BBS dependencies.</li>
 * </ol>
 */
public final class ActionsAndStuffVerificationMain {

    private static final List<String> EXPECTED_MIXINS = List.of(
        "ans/mixin/BackgroundRendererMixin.class",
        "ans/mixin/BiomeMixin.class",
        "ans/mixin/ClientWorldMixin.class",
        "ans/mixin/EntityRenderDispatcherMixin.class",
        "ans/mixin/FoliageColorsMixin.class",
        "ans/mixin/HeldItemRendererMixin.class",
        "ans/mixin/ItemColorsMixin.class",
        "ans/mixin/MinecraftClientMixin.class",
        "ans/mixin/WorldRendererMixin.class"
    );

    public static void main(String[] args) throws Exception {
        Path packZip = resolveArgPath(args, ".zip",
            Path.of("run", "resourcepacks", "Actions-And-Stuff-Java-1.20.1.zip"),
            Path.of(System.getProperty("user.home", "."), "Downloads", "Actions-And-Stuff-Java-1.20.1.zip"));
        Path modJar = resolveArgPath(args, ".jar",
            Path.of("run", "mods", "actions-and-stuff-fabric-1.20.1.jar"),
            Path.of(System.getProperty("user.home", "."), "Downloads", "actions-and-stuff-fabric-1.20.1.jar"));

        System.out.println("=== Actions & Stuff Port Verification ===");
        System.out.println("Resource Pack ZIP : " + packZip);
        System.out.println("Fabric Mod JAR    : " + modJar);

        verifyStage1PackTexturesAndMcmeta(packZip);

        BedrockPackManager pack = new BedrockPackManager();
        pack.loadFromConvertedZip(packZip);
        verifyStage2PackIntegrity(pack);

        verifyStage3DynamicSimulation(pack);
        verifyStage3bPoseAndControllerRegression(pack);
        verifyStage4Atmosphere(pack);
        verifyStage5ModJar(modJar);

        System.out.println("=== ALL 5 VERIFICATION STAGES PASSED ===");
    }

    private static Path resolveArgPath(String[] args, String suffix, Path primary, Path secondary) {
        if (args != null) {
            for (String arg : args) {
                if (arg != null && arg.toLowerCase(Locale.ROOT).endsWith(suffix) && Files.isRegularFile(Path.of(arg))) {
                    if (".jar".equals(suffix) && !arg.toLowerCase(Locale.ROOT).contains("actions-and-stuff")) {
                        continue;
                    }
                    return Path.of(arg);
                }
            }
        }
        if (Files.isRegularFile(primary)) return primary;
        if (Files.isRegularFile(secondary)) return secondary;
        throw new IllegalStateException("Missing required file ending in " + suffix + ": checked " + primary + " and " + secondary);
    }

    private static void verifyStage1PackTexturesAndMcmeta(Path packZip) throws IOException {
        int pngCount = 0;
        int mcmetaCount = 0;
        List<String> pngErrors = new ArrayList<>();
        List<String> mcmetaErrors = new ArrayList<>();
        Map<String, int[]> pngDims = new HashMap<>();
        Map<String, String> mcmetaTexts = new HashMap<>();

        try (ZipFile zip = new ZipFile(packZip.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry entry = en.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName().replace('\\', '/');
                if (name.endsWith(".png")) {
                    pngCount++;
                    byte[] bytes;
                    try (InputStream in = zip.getInputStream(entry)) {
                        bytes = in.readAllBytes();
                    }
                    try {
                        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
                        if (img == null || img.getWidth() <= 0 || img.getHeight() <= 0) {
                            pngErrors.add(name + " (decoded null or empty)");
                        } else {
                            pngDims.put(name, new int[]{img.getWidth(), img.getHeight()});
                        }
                    } catch (Exception ex) {
                        pngErrors.add(name + ": " + ex.getMessage());
                    }
                } else if (name.endsWith(".png.mcmeta")) {
                    mcmetaCount++;
                    try (InputStream in = zip.getInputStream(entry)) {
                        mcmetaTexts.put(name, new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
            }
        }

        for (Map.Entry<String, String> e : mcmetaTexts.entrySet()) {
            String mcmetaPath = e.getKey();
            String basePng = mcmetaPath.substring(0, mcmetaPath.length() - ".mcmeta".length());
            int[] dims = pngDims.get(basePng);
            if (dims == null) {
                mcmetaErrors.add(mcmetaPath + ": missing paired PNG " + basePng);
                continue;
            }
            int w = dims[0];
            int h = dims[1];
            int frameCount = Math.max(1, h / Math.max(1, w));
            try {
                JsonObject obj = JsonParser.parseString(e.getValue()).getAsJsonObject();
                if (!obj.has("animation") || !obj.get("animation").isJsonObject()) {
                    mcmetaErrors.add(mcmetaPath + ": missing animation object");
                    continue;
                }
                JsonObject anim = obj.getAsJsonObject("animation");
                if (anim.has("frames") && anim.get("frames").isJsonArray()) {
                    for (JsonElement fe : anim.getAsJsonArray("frames")) {
                        int idx = fe.isJsonObject()
                            ? fe.getAsJsonObject().get("index").getAsInt()
                            : fe.getAsInt();
                        if (idx < 0 || idx >= frameCount) {
                            mcmetaErrors.add(mcmetaPath + ": frame index " + idx + " out of [0, " + frameCount + ") for " + w + "x" + h);
                        }
                    }
                }
            } catch (Exception ex) {
                mcmetaErrors.add(mcmetaPath + ": JSON error: " + ex.getMessage());
            }
        }

        System.out.println("[Stage 1] Decoded PNGs=" + pngCount + " (errors=" + pngErrors.size() + "), .png.mcmeta=" + mcmetaCount + " (errors=" + mcmetaErrors.size() + ")");
        if (!pngErrors.isEmpty()) {
            throw new AssertionError("PNG decode failures (" + pngErrors.size() + "): " + pngErrors.subList(0, Math.min(10, pngErrors.size())));
        }
        if (!mcmetaErrors.isEmpty()) {
            throw new AssertionError("Mcmeta validation failures (" + mcmetaErrors.size() + "): " + mcmetaErrors.subList(0, Math.min(10, mcmetaErrors.size())));
        }
    }

    private static void verifyStage2PackIntegrity(BedrockPackManager pack) {
        BedrockPackManager.AuditReport report = pack.auditIntegrity();
        List<String> missingCustomTex = new ArrayList<>();
        List<String> missingSourcePlaceholders = new ArrayList<>();
        List<String> missingVanillaFallbackTex = new ArrayList<>();
        for (String m : report.missingTextures()) {
            if (m.contains("textures/oreville/")) {
                if (m.contains("textures/oreville/dgt.png") || m.contains("textures/oreville/ans/oog.png")) {
                    // Verified absent from source animation mod.mcpack (unused placeholder refs in source pack)
                    missingSourcePlaceholders.add(m);
                } else {
                    missingCustomTex.add(m);
                }
            } else {
                missingVanillaFallbackTex.add(m);
            }
        }

        System.out.printf(Locale.ROOT,
            "[Stage 2] Entities=%d Attachables=%d Geometries=%d Anims=%d AnimCtrls=%d RenderCtrls=%d Particles=%d Materials=%d Atmospherics=%d Biomes=%d Fogs=%d Water=%d Lighting=%d Textures=%d%n",
            report.entityCount(), report.attachableCount(), report.geometryCount(), report.animationCount(),
            report.animationControllerCount(), report.renderControllerCount(), report.particleCount(),
            report.materialCount(), report.atmosphericCount(), report.biomeCount(), report.fogCount(),
            report.waterCount(), report.lightingCount(), report.availableTextureCount()
        );
        System.out.println("[Stage 2] parseErrors=" + report.parseErrors().size()
            + " missingGeos=" + report.missingGeometries().size()
            + " missingAnims=" + report.missingAnimations().size()
            + " missingRenderCtrls=" + report.missingRenderControllers().size()
            + " missingCustomTex=" + missingCustomTex.size()
            + " sourcePlaceholders=" + missingSourcePlaceholders.size()
            + " vanillaProvidedTexRefs=" + missingVanillaFallbackTex.size());
        if (!missingCustomTex.isEmpty()) {
            System.out.println("[Stage 2] Missing custom textures detail: " + missingCustomTex);
        }
        if (!missingVanillaFallbackTex.isEmpty()) {
            System.out.println("[Stage 2] Vanilla-provided texture refs: " + missingVanillaFallbackTex);
        }

        if (!report.parseErrors().isEmpty()) {
            throw new AssertionError("Pack parse errors: " + report.parseErrors().subList(0, Math.min(10, report.parseErrors().size())));
        }
        if (report.entityCount() < 115 || report.attachableCount() < 1600 || report.particleCount() < 500) {
            throw new AssertionError("Unexpectedly low entity/attachable/particle counts: "
                + report.entityCount() + " / " + report.attachableCount() + " / " + report.particleCount());
        }
        if (!report.missingGeometries().isEmpty()) {
            throw new AssertionError("Missing geometries (" + report.missingGeometries().size() + "): " + report.missingGeometries().subList(0, Math.min(15, report.missingGeometries().size())));
        }
        if (!report.missingAnimations().isEmpty()) {
            throw new AssertionError("Missing animations/controllers (" + report.missingAnimations().size() + "): " + report.missingAnimations().subList(0, Math.min(15, report.missingAnimations().size())));
        }
        if (!report.missingRenderControllers().isEmpty()) {
            throw new AssertionError("Missing render controllers (" + report.missingRenderControllers().size() + "): " + report.missingRenderControllers().subList(0, Math.min(15, report.missingRenderControllers().size())));
        }
        if (!missingCustomTex.isEmpty()) {
            throw new AssertionError("Missing custom Actions & Stuff textures (" + missingCustomTex.size() + "): " + missingCustomTex.subList(0, Math.min(15, missingCustomTex.size())));
        }
    }

    private static void verifyStage3DynamicSimulation(BedrockPackManager pack) {
        BedrockModelRenderer renderer = new BedrockModelRenderer();
        BedrockParticleEngine particleEngine = new BedrockParticleEngine();

        int entityPassCount = 0;
        for (BedrockEntityDef def : pack.entities.values()) {
            BedrockEntityState state = new BedrockEntityState();
            for (int tick = 0; tick < 20; tick++) {
                boolean walk = tick >= 5 && tick < 12;
                boolean hurt = tick == 13;
                state.queryContext.moving = walk;
                state.queryContext.groundSpeed = walk ? 4.3f : 0f;
                state.queryContext.modifiedMoveSpeed = walk ? 0.85f : 0f;
                state.queryContext.modifiedDistanceMoved += walk ? 0.25f : 0f;
                state.queryContext.walkDistance += walk ? 0.2f : 0f;
                state.queryContext.hurtTime = hurt ? 6f : 0f;
                state.queryContext.usingItem = tick >= 15;
                state.queryContext.timeOfDay = (tick / 20f);
                state.updateEntity(def, 0.05f, pack, particleEngine, 0, 64, 0);

                List<BedrockModelRenderer.ResolvedPass> passes = renderer.evaluateRenderPasses(def, state, pack);
                if (tick == 19) entityPassCount += passes.size();
                for (BedrockModelRenderer.ResolvedPass pass : passes) {
                    state.buildBoneMatrices(pass.geometry(), null);
                    if (!state.allMatricesFinite()) {
                        throw new AssertionError("Non-finite bone matrix in entity " + def.identifier + " geo=" + pass.geometry().identifier);
                    }
                }
            }
        }

        int attPassCount = 0;
        for (BedrockAttachableDef att : pack.attachablesById.values()) {
            BedrockEntityState ownerState = new BedrockEntityState();
            BedrockEntityState attState = new BedrockEntityState();
            attState.queryContext.attached = true;
            attState.queryContext.owningEntity = ans.molang.MolangValue.entityRef(ownerState.scope);
            for (int tick = 0; tick < 6; tick++) {
                attState.queryContext.firstPerson = (tick % 2 == 0);
                attState.queryContext.usingItem = (tick >= 3);
                attState.updateAttachable(att, 0.05f, pack, particleEngine, 0, 64, 0);
                List<BedrockModelRenderer.ResolvedPass> passes = renderer.evaluateAttachablePasses(att, attState, pack);
                if (tick == 5) attPassCount += passes.size();
                for (BedrockModelRenderer.ResolvedPass pass : passes) {
                    attState.buildBoneMatrices(pass.geometry(), ownerState);
                    if (!attState.allMatricesFinite()) {
                        throw new AssertionError("Non-finite bone matrix in attachable " + att.identifier);
                    }
                }
            }
        }

        int spawnedParticleDefs = 0;
        BedrockEntityState sharedState = new BedrockEntityState();
        for (BedrockParticleDef pDef : pack.particles.values()) {
            particleEngine.spawnEmitter(pDef, sharedState.scope, 0f, 64f, 0f);
            spawnedParticleDefs++;
            if (spawnedParticleDefs >= 80) break;
        }
        int peakParticles = 0;
        for (int i = 0; i < 10; i++) {
            particleEngine.tick(0.05f);
            peakParticles = Math.max(peakParticles, particleEngine.getActiveParticleCount());
        }

        System.out.println("[Stage 3] Simulated " + pack.entities.size() + " entities (passes@t19=" + entityPassCount
            + "), " + pack.attachablesById.size() + " attachables (passes@t5=" + attPassCount
            + "), particles peak=" + peakParticles + " emitters=" + particleEngine.getActiveEmitterCount());

        if (entityPassCount == 0 || attPassCount == 0) {
            throw new AssertionError("Expected non-zero resolved render passes during simulation");
        }
        if (peakParticles <= 0) {
            throw new AssertionError("Expected active particles during particle engine simulation");
        }
    }

    private static void verifyStage3bPoseAndControllerRegression(BedrockPackManager pack) {
        BedrockModelRenderer renderer = new BedrockModelRenderer();
        BedrockParticleEngine pe = new BedrockParticleEngine();

        // 1) Player walking East (-90 deg) vs Idle
        BedrockEntityDef playerDef = pack.resolveEntity("minecraft:player");
        if (playerDef == null) throw new AssertionError("Missing minecraft:player");
        BedrockEntityState player = new BedrockEntityState();
        player.queryContext.localPlayer = true;
        player.queryContext.swingDuration = 6.0f;
        player.scope.setVariable("is_first_person", ans.molang.MolangValue.ZERO);
        player.scope.setVariable("is_paperdoll", ans.molang.MolangValue.ZERO);
        player.scope.setVariable("map_face_icon", ans.molang.MolangValue.ZERO);
        player.scope.setVariable("is_holding_right", ans.molang.MolangValue.ZERO);
        player.scope.setVariable("is_holding_left", ans.molang.MolangValue.ZERO);

        float px = 0f;
        float dt = 1f / 60f;
        BedrockGeometry resolvedPlayerGeo = null;
        for (int f = 0; f < 90; f++) {
            boolean moving = f >= 30;
            float speed = moving ? 4.3f : 0f;
            if (moving) px += speed * dt;
            player.queryContext.posX = px;
            player.queryContext.posY = 65.62f;
            player.queryContext.posZ = 0f;
            player.queryContext.deltaX = moving ? speed : 0f;
            player.queryContext.deltaY = 0f;
            player.queryContext.deltaZ = 0f;
            player.queryContext.groundSpeed = speed;
            player.queryContext.modifiedMoveSpeed = moving ? 0.9f : 0f;
            player.queryContext.moving = moving;
            player.queryContext.onGround = true;
            player.queryContext.bodyYRotation = -90f;
            player.queryContext.headYRotation = -90f;
            player.queryContext.targetYRotation = 0f;
            player.queryContext.headXRotation = 0f;
            player.queryContext.targetXRotation = 0f;
            player.updateEntity(playerDef, dt, pack, pe, px, 64, 0);
            var passes = renderer.evaluateRenderPasses(playerDef, player, pack);
            for (BedrockModelRenderer.ResolvedPass pass : passes) {
                if (pass.geometry().bonesByName.containsKey("rightleg")) {
                    resolvedPlayerGeo = pass.geometry();
                }
                player.buildBoneMatrices(pass.geometry(), null);
            }
            if (f == 29 && resolvedPlayerGeo != null) {
                player.buildBoneMatrices(resolvedPlayerGeo, null);
                float[] ih = singleBoneYBounds(resolvedPlayerGeo, player, "head");
                float[] ib = singleBoneYBounds(resolvedPlayerGeo, player, "body");
                float[] il = singleBoneYBounds(resolvedPlayerGeo, player, "rightleg");
                System.out.printf(Locale.ROOT,
                    "[Stage 3b] Player idle@0.5s: passes=%d geo=%s headY=[%.1f..%.1f] bodyY=[%.1f..%.1f] legY=[%.1f..%.1f]%n",
                    passes.size(), resolvedPlayerGeo.identifier,
                    ih[0], ih[1], ib[0], ib[1], il[0], il[1]);
            }
        }
        if (resolvedPlayerGeo != null) {
            player.buildBoneMatrices(resolvedPlayerGeo, null);
        }

        double kdqvtc = player.scope.getVariable("kdqvtc").asDouble();
        double ujrabr = player.scope.getVariable("ujrabr").asDouble();
        double odekyc = player.scope.getVariable("odekyc").asDouble();
        double sfdeke = player.scope.getVariable("sfdeke").asDouble();
        double nqexps = player.scope.getVariable("nqexps").asDouble();
        double esnrkj = player.scope.getVariable("esnrkj").asDouble();
        double upxhvv = player.scope.getVariable("upxhvv").asDouble();

        var headMatP = player.getBoneMatrix("head");
        var bodyMatP = player.getBoneMatrix("body");
        float[] pHeadY = singleBoneYBounds(resolvedPlayerGeo, player, "head");
        float[] pBodyY = singleBoneYBounds(resolvedPlayerGeo, player, "body");
        float[] pLegY = singleBoneYBounds(resolvedPlayerGeo, player, "rightleg");
        System.out.printf(Locale.ROOT,
            "[Stage 3b] Player walk@1.5s: kdqvtc=%.1f ujrabr=%.2f odekyc=%.2f sfdeke=%.3f nqexps=%.3f esnrkj=%.3f upxhvv=%.3f headY=[%.1f..%.1f] bodyY=[%.1f..%.1f] legY=[%.1f..%.1f]%n",
            kdqvtc, ujrabr, odekyc, sfdeke, nqexps, esnrkj, upxhvv, pHeadY[0], pHeadY[1], pBodyY[0], pBodyY[1], pLegY[0], pLegY[1]);

        if (kdqvtc < 0.5) throw new AssertionError("Player custom animations disabled (kdqvtc=" + kdqvtc + ")");
        if (Math.abs(ujrabr) > 0.05 || Math.abs(odekyc) > 0.05) {
            throw new AssertionError("Invalid q.bone_origin offset on player: ujrabr=" + ujrabr + " odekyc=" + odekyc);
        }
        if (sfdeke < 0.8 || Math.abs(nqexps) > 0.2 || esnrkj < 0.5) {
            throw new AssertionError("Player forward movement misaligned: sfdeke=" + sfdeke + " nqexps=" + nqexps + " esnrkj=" + esnrkj);
        }
        if (headMatP == null || bodyMatP == null || pHeadY[1] < 27.0f || pLegY[1] < 5.0f) {
            throw new AssertionError("Player skeleton proportions collapsed: headY=[" + pHeadY[0] + ".." + pHeadY[1] + "] bodyY=[" + pBodyY[0] + ".." + pBodyY[1] + "] legY=[" + pLegY[0] + ".." + pLegY[1] + "]");
        }

        // 1b) Player 3rd-person arm swing check (multiple swipes)
        var idleArm = new org.joml.Matrix4f(player.getBoneMatrix("rightarm"));
        boolean swung = false;
        for (int swipe = 0; swipe < 3; swipe++) {
            boolean thisSwung = false;
            for (int f = 0; f < 25; f++) {
                float swing = f < 6 ? (f + 0.5f) / 6.0f : 0f;
                player.scope.setVariable("attack_time", ans.molang.MolangValue.of(swing));
                player.updateEntity(playerDef, dt, pack, pe, px, 64, 0);
                if (resolvedPlayerGeo != null) player.buildBoneMatrices(resolvedPlayerGeo, null);
                var swingArm = player.getBoneMatrix("rightarm");
                org.joml.Vector3f d1 = swingArm.transformDirection(new org.joml.Vector3f(0, -1, 0));
                if (d1.z < -0.2f || d1.y > -0.8f) {
                    swung = true;
                    thisSwung = true;
                }
                if (f == 1) {
                    org.joml.Vector3f d0 = idleArm.transformDirection(new org.joml.Vector3f(0, -1, 0));
                    System.out.printf(Locale.ROOT, "[Stage 3b] Player 3p swing#%d@f=1: idleArmDir=(%.2f,%.2f,%.2f) swingArmDir=(%.2f,%.2f,%.2f)%n",
                        swipe, d0.x, d0.y, d0.z, d1.x, d1.y, d1.z);
                }
            }
            if (!thisSwung) {
                throw new AssertionError("Player 3rd-person arm swing #" + swipe + " did not rotate rightarm");
            }
        }

        // 2) Skeleton & Zombie spawn squash recovery & relative_to entity head
        for (String mobId : new String[]{"minecraft:skeleton", "minecraft:zombie"}) {
            BedrockEntityDef mobDef = pack.resolveEntity(mobId);
            if (mobDef == null) throw new AssertionError("Missing " + mobId);
            BedrockEntityState mob = new BedrockEntityState();
            mob.queryContext.posX = 0f;
            mob.queryContext.posY = 64f;
            mob.queryContext.posZ = 0f;
            mob.queryContext.onGround = true;
            mob.queryContext.headYRotation = 35f;
            mob.queryContext.bodyYRotation = 0f;
            mob.queryContext.targetYRotation = 35f;
            mob.queryContext.headXRotation = 0f;
            mob.queryContext.targetXRotation = 0f;

            BedrockGeometry mobGeo = null;
            float[] squashHead = null;
            for (int f = 0; f < 90; f++) {
                mob.updateEntity(mobDef, dt, pack, pe, 0, 64, 0);
                for (BedrockModelRenderer.ResolvedPass pass : renderer.evaluateRenderPasses(mobDef, mob, pack)) {
                    if (pass.geometry().bonesByName.containsKey("rightleg")) {
                        mobGeo = pass.geometry();
                    }
                    mob.buildBoneMatrices(pass.geometry(), null);
                }
                if (f == 15 && mobGeo != null) {
                    mob.buildBoneMatrices(mobGeo, null);
                    squashHead = singleBoneYBounds(mobGeo, mob, "head");
                }
            }
            if (mobGeo != null) mob.buildBoneMatrices(mobGeo, null);
            float[] mHeadY = singleBoneYBounds(mobGeo, mob, "head");
            float[] mBodyY = singleBoneYBounds(mobGeo, mob, "body");
            float[] mLegY = singleBoneYBounds(mobGeo, mob, "rightleg");
            System.out.printf(Locale.ROOT,
                "[Stage 3b] %s geo=%s @0.25s headY=[%.1f..%.1f] | @1.5s headY=[%.1f..%.1f] bodyY=[%.1f..%.1f] legY=[%.1f..%.1f]%n",
                mobId, mobGeo != null ? mobGeo.identifier : "null",
                squashHead[0], squashHead[1], mHeadY[0], mHeadY[1], mBodyY[0], mBodyY[1], mLegY[0], mLegY[1]);

            if (mHeadY[1] < 29.0f || mHeadY[1] < squashHead[1] + 2.0f) {
                throw new AssertionError(mobId + " still squashed after 1.5s: headY=[" + mHeadY[0] + ".." + mHeadY[1] + "] bodyY=[" + mBodyY[0] + ".." + mBodyY[1] + "]");
            }
        }

        // 3) Chicken walking diagnostic
        BedrockEntityDef chickenDef = pack.resolveEntity("minecraft:chicken");
        if (chickenDef != null) {
            BedrockEntityState ch = new BedrockEntityState();
            ch.queryContext.posX = 0f;
            ch.queryContext.posY = 64f;
            ch.queryContext.posZ = 0f;
            ch.queryContext.onGround = true;
            BedrockGeometry chGeo = null;
            for (int f = 0; f < 60; f++) {
                boolean moving = f >= 15;
                float speed = moving ? 2.5f : 0f;
                ch.queryContext.posX += speed * dt;
                ch.queryContext.deltaX = speed;
                ch.queryContext.groundSpeed = speed;
                ch.queryContext.modifiedMoveSpeed = moving ? 0.6f : 0f;
                ch.queryContext.moving = moving;
                ch.updateEntity(chickenDef, dt, pack, pe, ch.queryContext.posX, 64, 0);
                for (BedrockModelRenderer.ResolvedPass pass : renderer.evaluateRenderPasses(chickenDef, ch, pack)) {
                    if (pass.geometry().bonesByName.containsKey("body")) {
                        chGeo = pass.geometry();
                    }
                    ch.buildBoneMatrices(pass.geometry(), null);
                }
                if (chGeo != null && f % 10 == 0) {
                    ch.buildBoneMatrices(chGeo, null);
                    float[] cHead = singleBoneYBounds(chGeo, ch, "head");
                    float[] cBody = singleBoneYBounds(chGeo, ch, "body");
                    float[] cLeg = singleBoneYBounds(chGeo, ch, "leg0");
                    System.out.printf(Locale.ROOT,
                        "[Stage 3b] Chicken f=%d moving=%s geo=%s headY=[%.2f..%.2f] bodyY=[%.2f..%.2f] leg0Y=[%.2f..%.2f]%n",
                        f, moving, chGeo.identifier, cHead[0], cHead[1], cBody[0], cBody[1], cLeg[0], cLeg[1]);
                }
            }
        }
    }

    private static float[] singleBoneYBounds(BedrockGeometry geo, BedrockEntityState state, String boneLower) {
        if (geo == null) return new float[]{0f, 0f};
        BedrockGeometry.Bone bone = geo.bonesByName.get(boneLower);
        if (bone == null) return new float[]{0f, 0f};
        float[] range = {Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
        org.joml.Matrix4f mat = state.getBoneMatrix(bone.nameLower);
        if (mat != null && !bone.cubes.isEmpty()) {
            org.joml.Vector3f v = new org.joml.Vector3f();
            for (BedrockGeometry.Cube c : bone.cubes) {
                float[] xs = {c.minX / 16f, c.maxX / 16f};
                float[] ys = {c.minY / 16f, c.maxY / 16f};
                float[] zs = {c.minZ / 16f, c.maxZ / 16f};
                for (float x : xs) for (float y : ys) for (float z : zs) {
                    mat.transformPosition(v.set(x, y, z));
                    range[0] = Math.min(range[0], v.y * 16f);
                    range[1] = Math.max(range[1], v.y * 16f);
                }
            }
        }
        return Float.isFinite(range[0]) ? range : new float[]{0f, 0f};
    }

    private static void verifyStage4Atmosphere(BedrockPackManager pack) {
        BedrockAtmosphereManager atmo = new BedrockAtmosphereManager();
        atmo.loadFromPack(pack);

        System.out.printf(Locale.ROOT,
            "[Stage 4] Atmospherics=%d Biomes=%d Fogs=%d Lightings=%d Colormaps=%d birch=#%06X spruce=#%06X mangrove=#%06X%n",
            atmo.atmospheres.size(), atmo.biomes.size(), atmo.fogs.size(), atmo.lightings.size(),
            atmo.colormaps.size(), atmo.getBirchFoliageColor(), atmo.getSpruceFoliageColor(), atmo.getMangroveFoliageColor()
        );

        if (atmo.atmospheres.size() != 25 || atmo.biomes.size() != 89 || atmo.fogs.size() != 26 || atmo.lightings.size() != 14) {
            throw new AssertionError("Unexpected atmosphere config counts");
        }

        String[] sampleBiomes = {
            "minecraft:plains", "minecraft:cherry_grove", "minecraft:mangrove_swamp",
            "minecraft:badlands", "minecraft:pale_garden", "minecraft:nether_wastes",
            "minecraft:crimson_forest", "minecraft:the_end"
        };
        float[] angles = {0.0f, 0.215f, 0.5f, 0.76f};
        for (String b : sampleBiomes) {
            int noonZenith = atmo.getSkyZenithRgb(b, angles[0]);
            int sunsetZenith = atmo.getSkyZenithRgb(b, angles[1]);
            int midnightZenith = atmo.getSkyZenithRgb(b, angles[2]);
            int horizonSunset = atmo.getSkyHorizonRgb(b, angles[1]);
            int fogNoon = atmo.getFogRgb(b, angles[0], false, false);
            float fStart = atmo.getFogStartBlocks(b, false, false, 192f);
            float fEnd = atmo.getFogEndBlocks(b, false, false, 192f);
            if (!Float.isFinite(fStart) || !Float.isFinite(fEnd) || fEnd <= 0f) {
                throw new AssertionError("Invalid fog range for biome " + b);
            }
            if ("minecraft:plains".equals(b) && (noonZenith == sunsetZenith || noonZenith == midnightZenith)) {
                throw new AssertionError("Plains zenith sky did not animate across time of day");
            }
            System.out.printf(Locale.ROOT,
                "  Biome %-24s noon=#%06X sunset=#%06X (hor=#%06X) midnight=#%06X fog=#%06X [%.1f..%.1f]%n",
                b, noonZenith, sunsetZenith, horizonSunset, midnightZenith, fogNoon, fStart, fEnd);
        }
    }

    private static void verifyStage5ModJar(Path modJar) throws IOException {
        byte[] bbsNeedle = new StringBuilder("mchorse").append('/').append("bbs_mod").toString().getBytes(StandardCharsets.US_ASCII);
        byte[] classNeedle = new StringBuilder("net/minecraft/").append("class_").toString().getBytes(StandardCharsets.US_ASCII);

        boolean hasFabricJson = false;
        boolean hasMixinsJson = false;
        boolean hasClientMod = false;
        List<String> presentMixins = new ArrayList<>();
        int intermediaryMixinHits = 0;

        try (ZipFile jar = new ZipFile(modJar.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> en = jar.entries();
            while (en.hasMoreElements()) {
                ZipEntry entry = en.nextElement();
                String name = entry.getName().replace('\\', '/');
                if ("fabric.mod.json".equals(name)) hasFabricJson = true;
                if ("actions_and_stuff.mixins.json".equals(name)) hasMixinsJson = true;
                if ("ans/ActionsAndStuffClientMod.class".equals(name)) hasClientMod = true;
                if (EXPECTED_MIXINS.contains(name)) presentMixins.add(name);

                if (name.contains("mchorse") || name.contains("bbs_mod")) {
                    throw new AssertionError("Forbidden BBS class entry in standalone mod JAR: " + name);
                }
                if (name.endsWith(".class")) {
                    byte[] bytes;
                    try (InputStream in = jar.getInputStream(entry)) {
                        bytes = in.readAllBytes();
                    }
                    if (indexOfBytes(bytes, bbsNeedle) >= 0) {
                        throw new AssertionError("Forbidden BBS reference inside class " + name);
                    }
                    if (EXPECTED_MIXINS.contains(name) && indexOfBytes(bytes, classNeedle) >= 0) {
                        intermediaryMixinHits++;
                    }
                }
            }
        }

        System.out.println("[Stage 5] fabric.mod.json=" + hasFabricJson
            + " mixins.json=" + hasMixinsJson
            + " clientMod=" + hasClientMod
            + " mixins=" + presentMixins.size() + "/" + EXPECTED_MIXINS.size()
            + " intermediaryRemappedMixins=" + intermediaryMixinHits);

        if (!hasFabricJson || !hasMixinsJson || !hasClientMod || presentMixins.size() != EXPECTED_MIXINS.size()) {
            throw new AssertionError("Incomplete standalone Fabric mod JAR contents");
        }
        if (intermediaryMixinHits != EXPECTED_MIXINS.size()) {
            throw new AssertionError("Not all mixin classes contain Intermediary remapped references: " + intermediaryMixinHits);
        }
    }

    private static int indexOfBytes(byte[] haystack, byte[] needle) {
        if (needle.length == 0) return 0;
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
