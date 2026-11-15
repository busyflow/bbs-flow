package ans.bedrock;

import ans.molang.MolangExpr;
import ans.molang.MolangParser;
import ans.molang.MolangQueryContext;
import ans.molang.MolangScope;
import ans.molang.MolangValue;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Per-entity or per-attachable mutable runtime state executing Bedrock MoLang scripts,
 * animation controllers, keyframes, timelines, sound/particle triggers, and hierarchical bone matrices.
 */
public final class BedrockEntityState {

    private static final class BoneAccum {
        float px, py, pz;
        float rx, ry, rz;
        float sx = 1f, sy = 1f, sz = 1f;
        boolean hasScale = false;
        boolean hasAnim = false;
        boolean relativeToRotationEntity = false;
    }

    private record AnimStep(String fullNameLower, float weight, float time, float prevTime) {
    }

    public final MolangQueryContext.DefaultContext queryContext = new MolangQueryContext.DefaultContext();
    public final MolangScope scope = new MolangScope(queryContext);

    public long lastRenderFrameId = -1L;
    private boolean initialized = false;
    private boolean bindOriginsSeeded = false;
    private final Map<String, BedrockAnimationController.RuntimeState> controllerStates = new HashMap<>();
    public Map<String, BedrockAnimationController.RuntimeState> getControllerStates() { return controllerStates; }
    private final Map<String, Float> directAnimTimes = new HashMap<>();
    private final Map<String, Float> animPrevTimes = new HashMap<>();
    private final Map<String, BoneAccum> boneAccums = new HashMap<>();
    private final Map<String, Matrix4f> boneMatrices = new HashMap<>();
    private final List<String> pendingSounds = new ArrayList<>();

    public BedrockEntityState() {
    }

    public void resetInitialization() {
        initialized = false;
        bindOriginsSeeded = false;
        controllerStates.clear();
        directAnimTimes.clear();
        animPrevTimes.clear();
    }

    public List<String> drainPendingSounds() {
        if (pendingSounds.isEmpty()) return List.of();
        List<String> copy = new ArrayList<>(pendingSounds);
        pendingSounds.clear();
        return copy;
    }

    public Matrix4f getBoneMatrix(String boneNameLower) {
        return boneMatrices.get(boneNameLower);
    }

    public Map<String, Matrix4f> getBoneMatrices() {
        return boneMatrices;
    }

    public void updateEntity(BedrockEntityDef def, float dt, BedrockPackManager pack,
                             BedrockParticleEngine particleEngine, double worldX, double worldY, double worldZ) {
        if (def == null) return;
        if (!bindOriginsSeeded && pack != null && !def.geometry.isEmpty()) {
            seedBindBoneOrigins(def.geometry, pack);
            bindOriginsSeeded = true;
        }
        stepScripts(def.initialize, def.preAnimation, def.animate, def.animations, def.particleEffects, def.soundEffects,
            dt, pack, particleEngine, worldX, worldY, worldZ);
    }

    private void seedBindBoneOrigins(Map<String, String> geoMap, BedrockPackManager pack) {
        String defaultId = geoMap.get("default");
        BedrockGeometry geo = defaultId != null ? pack.geometries.get(defaultId) : null;
        if (geo == null) {
            for (String gid : geoMap.values()) {
                geo = pack.geometries.get(gid);
                if (geo != null) break;
            }
        }
        if (geo == null) return;
        for (BedrockGeometry.Bone b : geo.allBones) {
            queryContext.setBoneOrigin(b.nameLower, -b.pivot[0], 24f - b.pivot[1], b.pivot[2]);
        }
    }

    public void updateAttachable(BedrockAttachableDef def, float dt, BedrockPackManager pack,
                                 BedrockParticleEngine particleEngine, double worldX, double worldY, double worldZ) {
        if (def == null) return;
        stepScripts(def.initialize, def.preAnimation, def.animate, def.animations, def.particleEffects, def.soundEffects,
            dt, pack, particleEngine, worldX, worldY, worldZ);
    }

    private void stepScripts(List<MolangExpr> initScripts,
                             List<MolangExpr> preAnimScripts,
                             List<BedrockEntityDef.ScriptAnimate> animateList,
                             Map<String, String> animMap,
                             Map<String, String> particleMap,
                             Map<String, String> soundMap,
                             float dt,
                             BedrockPackManager pack,
                             BedrockParticleEngine particleEngine,
                             double worldX, double worldY, double worldZ) {
        float safeDt = Math.max(0f, Math.min(dt, 0.25f));
        queryContext.deltaTime = safeDt;
        queryContext.animTime += safeDt;
        queryContext.lifeTime += safeDt;
        queryContext.timeStamp += safeDt;

        if (!initialized) {
            initialized = true;
            for (MolangExpr expr : initScripts) {
                expr.evaluate(scope);
            }
            scope.clearTemps();
        }

        // Keep player strafing in crab-walk mode (v.txtwqb < 10 -> v.nwocdu = true) instead of turning the body 90 deg sideways
        scope.setVariable("txtwqb", MolangValue.ZERO);

        for (MolangExpr expr : preAnimScripts) {
            expr.evaluate(scope);
        }
        scope.clearTemps();

        List<AnimStep> activeSteps = new ArrayList<>();
        for (BedrockEntityDef.ScriptAnimate sa : animateList) {
            float w = sa.condition().evaluateFloat(scope);
            String target = animMap.getOrDefault(sa.shortNameLower(), sa.shortNameLower());
            if (target == null || target.isEmpty()) continue;
            if (w <= 0.001f) {
                directAnimTimes.remove(target);
                animPrevTimes.remove(target);
                continue;
            }
            stepAnimateTarget(target, target, w, safeDt, animMap, pack, activeSteps, 0);
        }

        // Accumulate bone transforms
        boneAccums.clear();
        float[] tmpVec = new float[3];
        for (AnimStep step : activeSteps) {
            BedrockAnimation anim = pack.animations.get(step.fullNameLower);
            if (anim == null) continue;

            float prevAnimTime = queryContext.animTime;
            queryContext.animTime = step.time;
            float blendW = step.weight * (anim.blendWeight != null ? anim.blendWeight.evaluateFloat(scope) : 1f);
            if (Math.abs(blendW) > 1e-4f) {
                for (BedrockAnimation.BoneAnim ba : anim.bones.values()) {
                    BoneAccum acc = boneAccums.computeIfAbsent(ba.boneNameLower, k -> new BoneAccum());
                    if (anim.overridePreviousAnimation) {
                        acc.px = acc.py = acc.pz = 0f;
                        acc.rx = acc.ry = acc.rz = 0f;
                        acc.sx = acc.sy = acc.sz = 1f;
                        acc.hasScale = false;
                        acc.relativeToRotationEntity = false;
                    }
                    acc.hasAnim = true;
                    if (ba.relativeToRotationEntity) {
                        acc.relativeToRotationEntity = true;
                    }
                    if (ba.position != null) {
                        ba.position.evaluate(step.time, scope, tmpVec, acc.px, acc.py, acc.pz);
                        acc.px += tmpVec[0] * blendW;
                        acc.py += tmpVec[1] * blendW;
                        acc.pz += tmpVec[2] * blendW;
                    }
                    if (ba.rotation != null) {
                        ba.rotation.evaluate(step.time, scope, tmpVec, acc.rx, acc.ry, acc.rz);
                        acc.rx += tmpVec[0] * blendW;
                        acc.ry += tmpVec[1] * blendW;
                        acc.rz += tmpVec[2] * blendW;
                    }
                    if (ba.scale != null) {
                        ba.scale.evaluate(step.time, scope, tmpVec, acc.sx, acc.sy, acc.sz);
                        if (!acc.hasScale) {
                            acc.sx = 1f + (tmpVec[0] - 1f) * blendW;
                            acc.sy = 1f + (tmpVec[1] - 1f) * blendW;
                            acc.sz = 1f + (tmpVec[2] - 1f) * blendW;
                            acc.hasScale = true;
                        } else {
                            acc.sx *= (1f + (tmpVec[0] - 1f) * blendW);
                            acc.sy *= (1f + (tmpVec[1] - 1f) * blendW);
                            acc.sz *= (1f + (tmpVec[2] - 1f) * blendW);
                        }
                    }
                }
            }

            // Trigger timeline/sound/particle events crossed in (prevTime, time]
            fireEventsForStep(anim, step.prevTime, step.time, particleMap, soundMap, pack, particleEngine, worldX, worldY, worldZ);
            queryContext.animTime = prevAnimTime;
        }

        // Drain any sounds & particles pushed onto queryContext by controllers
        for (String se : queryContext.drainPendingSoundEffects()) {
            String resolved = soundMap.getOrDefault(se, se);
            if (!resolved.isEmpty()) pendingSounds.add(resolved);
        }
        if (particleEngine != null) {
            for (String[] pe : queryContext.drainPendingParticleEffects()) {
                String resolved = particleMap.getOrDefault(pe[0], pe[0]);
                BedrockParticleDef pDef = pack.particles.get(resolved.toLowerCase(Locale.ROOT));
                if (pDef != null) {
                    particleEngine.spawnEmitter(pDef, scope, (float) worldX, (float) worldY, (float) worldZ);
                }
            }
        }

        scope.clearTemps();
    }

    private void stepAnimateTarget(String pathKey,
                                   String shortOrFullName,
                                   float weight,
                                   float safeDt,
                                   Map<String, String> animMap,
                                   BedrockPackManager pack,
                                   List<AnimStep> activeSteps,
                                   int depth) {
        if (depth > 8 || weight <= 0.001f || shortOrFullName == null || shortOrFullName.isEmpty()) {
            return;
        }
        String target = animMap.getOrDefault(shortOrFullName, shortOrFullName);
        if (target.startsWith("controller.animation.")) {
            BedrockAnimationController ctrlDef = pack.animationControllers.get(target);
            if (ctrlDef == null) {
                return;
            }
            String ctrlKey = depth == 0 ? target : (pathKey + "->" + target);
            BedrockAnimationController.RuntimeState rt = controllerStates.computeIfAbsent(
                ctrlKey, k -> new BedrockAnimationController.RuntimeState(ctrlDef));
            List<BedrockAnimationController.ActiveAnimOut> ctrlOut = new ArrayList<>();
            rt.step(safeDt, scope, animMap, pack, ctrlOut);
            for (BedrockAnimationController.ActiveAnimOut out : ctrlOut) {
                String childTarget = animMap.getOrDefault(out.shortNameLower(), out.shortNameLower());
                if (childTarget.startsWith("controller.animation.")) {
                    stepAnimateTarget(ctrlKey, childTarget, weight * out.weight(), safeDt, animMap, pack, activeSteps, depth + 1);
                } else {
                    BedrockAnimation animDef = pack.animations.get(childTarget);
                    if (animDef == null) {
                        continue;
                    }
                    String key = ctrlKey + "->" + childTarget;
                    float prev = (out.stateTime() <= safeDt + 1e-4f) ? 0f : animPrevTimes.getOrDefault(key, 0f);
                    float nextRaw = animDef.animTimeUpdate != null
                        ? evalAnimTimeUpdate(animDef, prev, safeDt)
                        : out.stateTime();
                    float cur = wrapTime(animDef, nextRaw);
                    float wrappedPrev = wrapTime(animDef, prev);
                    float storedNext = (animDef.animTimeUpdate != null || animDef.animationLength <= 1e-4f) ? nextRaw : cur;
                    animPrevTimes.put(key, storedNext);
                    activeSteps.add(new AnimStep(childTarget, weight * out.weight(), cur, wrappedPrev));
                }
            }
        } else {
            BedrockAnimation animDef = pack.animations.get(target);
            if (animDef == null) {
                return;
            }
            float prev = directAnimTimes.getOrDefault(pathKey, 0f);
            float nextRaw = animDef.animTimeUpdate != null
                ? evalAnimTimeUpdate(animDef, prev, safeDt)
                : (prev + safeDt);
            directAnimTimes.put(pathKey, nextRaw);
            float cur = wrapTime(animDef, nextRaw);
            float wrappedPrev = wrapTime(animDef, prev);
            activeSteps.add(new AnimStep(target, weight, cur, wrappedPrev));
        }
    }

    private float computeAnimTime(BedrockAnimation anim, float dt, float stateTime, float prev) {
        if (anim.animTimeUpdate != null) {
            float prevGlobal = queryContext.animTime;
            queryContext.animTime = prev;
            float next = evalAnimTimeUpdate(anim, prev, dt);
            queryContext.animTime = prevGlobal;
            return wrapTime(anim, next);
        }
        return wrapTime(anim, stateTime);
    }

    private float evalAnimTimeUpdate(BedrockAnimation anim, float currentLocal, float dt) {
        float prevGlobal = queryContext.animTime;
        queryContext.animTime = currentLocal;
        float res = anim.animTimeUpdate.evaluateFloat(scope);
        queryContext.animTime = prevGlobal;
        if (!Float.isFinite(res)) {
            return currentLocal + dt;
        }
        return res;
    }

    private static float wrapTime(BedrockAnimation anim, float rawTime) {
        float len = anim.animationLength;
        if (len <= 1e-4f) return Math.max(0f, rawTime);
        return switch (anim.loopMode) {
            case LOOP -> {
                float m = rawTime % len;
                yield m < 0f ? m + len : m;
            }
            case HOLD_ON_LAST_FRAME -> Math.max(0f, Math.min(rawTime, len));
            case ONCE -> Math.max(0f, Math.min(rawTime, len));
        };
    }

    private void fireEventsForStep(BedrockAnimation anim,
                                   float prevTime,
                                   float curTime,
                                   Map<String, String> particleMap,
                                   Map<String, String> soundMap,
                                   BedrockPackManager pack,
                                   BedrockParticleEngine particleEngine,
                                   double worldX, double worldY, double worldZ) {
        if (curTime < prevTime) {
            // Wrapped loop boundary: fire [0, curTime]
            prevTime = -1e-4f;
        } else if (Math.abs(curTime - prevTime) < 1e-6f) {
            return;
        }

        for (BedrockAnimation.TimelineEvent te : anim.timeline) {
            if (te.time() > prevTime && te.time() <= curTime) {
                for (MolangExpr e : te.scripts()) {
                    e.evaluate(scope);
                }
            }
        }
        for (BedrockAnimation.SoundTrigger st : anim.soundEffects) {
            if (st.time() > prevTime && st.time() <= curTime) {
                String resolved = soundMap.getOrDefault(st.effect(), st.effect());
                if (!resolved.isEmpty()) {
                    pendingSounds.add(resolved);
                }
            }
        }
        if (particleEngine != null) {
            for (BedrockAnimation.ParticleTrigger pt : anim.particleEffects) {
                if (pt.time() > prevTime && pt.time() <= curTime) {
                    if (pt.preEffectScript() != null) {
                        pt.preEffectScript().evaluate(scope);
                    }
                    String effectId = particleMap.getOrDefault(pt.effect(), pt.effect());
                    BedrockParticleDef pDef = pack.particles.get(effectId.toLowerCase(Locale.ROOT));
                    if (pDef != null) {
                        particleEngine.spawnEmitter(pDef, scope, (float) worldX, (float) worldY, (float) worldZ);
                    }
                }
            }
        }
    }

    /**
     * Rebuilds hierarchical bone {@link Matrix4f} transforms (model-space blocks -> posed-actor blocks)
     * and updates {@code q.bone_orientation_trs} / {@code q.bone_origin} for MoLang locators & attachments.
     */
    public void buildBoneMatrices(BedrockGeometry geo) {
        buildBoneMatrices(geo, null);
    }

    public void buildBoneMatrices(BedrockGeometry geo, BedrockEntityState ownerState) {
        if (geo == null) return;
        Matrix4f identity = new Matrix4f();
        for (BedrockGeometry.Bone root : geo.rootBones) {
            Matrix4f parentMat = identity;
            if (ownerState != null && root.bindingExpr != null && !root.bindingExpr.isEmpty()) {
                String boundName = resolveBindingBoneName(root.bindingExpr);
                if (!boundName.isEmpty()) {
                    Matrix4f ownerBoneMat = ownerState.getBoneMatrix(boundName);
                    if (ownerBoneMat != null) {
                        parentMat = ownerBoneMat;
                    }
                }
            }
            buildBoneRecursive(root, parentMat, ownerState);
        }
    }

    private String resolveBindingBoneName(String bindingExpr) {
        String s = bindingExpr.trim();
        // If literal alphanumeric identifier with no MoLang operators/dots, use directly; otherwise eval MoLang
        boolean simple = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_')) {
                simple = false;
                break;
            }
        }
        if (simple) {
            return s.toLowerCase(Locale.ROOT);
        }
        return MolangParser.parse(s).evaluate(scope).asString().toLowerCase(Locale.ROOT);
    }

    private void buildBoneRecursive(BedrockGeometry.Bone bone, Matrix4f parentMatrix, BedrockEntityState ownerState) {
        if (ownerState != null && bone.bindingExpr != null && !bone.bindingExpr.isEmpty() && bone.parent != null && !bone.parent.isEmpty()) {
            String boundName = resolveBindingBoneName(bone.bindingExpr);
            Matrix4f ownerBoneMat = ownerState.getBoneMatrix(boundName);
            if (ownerBoneMat != null) {
                parentMatrix = ownerBoneMat;
            }
        }
        BoneAccum acc = boneAccums.get(bone.nameLower);

        float pivX = bone.pivot[0] / 16f;
        float pivY = bone.pivot[1] / 16f;
        float pivZ = bone.pivot[2] / 16f;

        float tx = acc != null ? (-acc.px / 16f) : 0f;
        float ty = acc != null ? (acc.py / 16f) : 0f;
        float tz = acc != null ? (acc.pz / 16f) : 0f;

        float rxDeg = bone.rotation[0] + (acc != null ? -acc.rx : 0f);
        float ryDeg = bone.rotation[1] + (acc != null ? -acc.ry : 0f);
        float rzDeg = bone.rotation[2] + (acc != null ? acc.rz : 0f);

        float sx = acc != null ? sanitizeScale(acc.sx) : 1f;
        float sy = acc != null ? sanitizeScale(acc.sy) : 1f;
        float sz = acc != null ? sanitizeScale(acc.sz) : 1f;

        Matrix4f boneMatrix;
        if (acc != null && acc.relativeToRotationEntity) {
            Vector3f wp = new Vector3f(pivX + tx, pivY + ty, pivZ + tz);
            parentMatrix.transformPosition(wp);
            Vector3f ps = parentMatrix.getScale(new Vector3f());
            boneMatrix = new Matrix4f().translation(wp.x, wp.y, wp.z);
            if (rzDeg != 0f) boneMatrix.rotateZ((float) Math.toRadians(rzDeg));
            if (ryDeg != 0f) boneMatrix.rotateY((float) Math.toRadians(ryDeg));
            if (rxDeg != 0f) boneMatrix.rotateX((float) Math.toRadians(rxDeg));
            float fsx = sx * ps.x;
            float fsy = sy * ps.y;
            float fsz = sz * ps.z;
            if (fsx != 1f || fsy != 1f || fsz != 1f) boneMatrix.scale(fsx, fsy, fsz);
            boneMatrix.translate(-pivX, -pivY, -pivZ);
        } else {
            boneMatrix = new Matrix4f(parentMatrix);
            boneMatrix.translate(pivX + tx, pivY + ty, pivZ + tz);
            if (rzDeg != 0f) boneMatrix.rotateZ((float) Math.toRadians(rzDeg));
            if (ryDeg != 0f) boneMatrix.rotateY((float) Math.toRadians(ryDeg));
            if (rxDeg != 0f) boneMatrix.rotateX((float) Math.toRadians(rxDeg));
            if (sx != 1f || sy != 1f || sz != 1f) boneMatrix.scale(sx, sy, sz);
            boneMatrix.translate(-pivX, -pivY, -pivZ);
        }

        boneMatrices.put(bone.nameLower, boneMatrix);

        // Bind-pose origin in Bedrock Y-down coordinates + posed orientation TRS for MoLang
        Vector3f posedPivot = boneMatrix.transformPosition(new Vector3f(pivX, pivY, pivZ));
        Vector3f euler = new Quaternionf().setFromUnnormalized(boneMatrix).getEulerAnglesXYZ(new Vector3f());
        if (!Float.isFinite(euler.x) || !Float.isFinite(euler.y) || !Float.isFinite(euler.z)) {
            euler.set(0f, 0f, 0f);
        }
        queryContext.setBoneOrigin(bone.nameLower, -bone.pivot[0], 24f - bone.pivot[1], bone.pivot[2]);
        queryContext.setBoneTrs(bone.nameLower, MolangValue.trs(
            posedPivot.x * 16f, posedPivot.y * 16f, posedPivot.z * 16f,
            Math.toDegrees(euler.x), Math.toDegrees(euler.y), Math.toDegrees(euler.z),
            sx, sy, sz
        ));

        for (BedrockGeometry.Bone child : bone.children) {
            buildBoneRecursive(child, boneMatrix, ownerState);
        }
    }

    private static float sanitizeScale(float v) {
        return Float.isFinite(v) ? v : 1f;
    }

    /** Helper mapping all non-finite check assertions during verification. */
    public boolean allMatricesFinite() {
        for (Matrix4f m : boneMatrices.values()) {
            for (int r = 0; r < 4; r++) {
                for (int c = 0; c < 4; c++) {
                    if (!Float.isFinite(m.get(c, r))) return false;
                }
            }
        }
        return true;
    }
}
