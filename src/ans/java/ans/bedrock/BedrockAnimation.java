package ans.bedrock;

import ans.molang.MolangExpr;
import ans.molang.MolangParser;
import ans.molang.MolangScope;
import ans.molang.MolangValue;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Parses and evaluates Bedrock {@code .animation.json} files including multi-keyframe
 * linear/catmullrom/step curves, {@code anim_time_update}, {@code blend_weight},
 * {@code timeline}, {@code sound_effects}, and {@code particle_effects}.
 */
public final class BedrockAnimation {

    public enum LoopMode {
        ONCE,
        LOOP,
        HOLD_ON_LAST_FRAME
    }

    public enum LerpMode {
        LINEAR,
        CATMULLROM,
        STEP
    }

    public record Vec3Expr(MolangExpr x, MolangExpr y, MolangExpr z) {
        public void eval(MolangScope scope, float[] out, float defX, float defY, float defZ) {
            scope.setThisValue(MolangValue.of(defX));
            out[0] = x != null ? x.evaluateFloat(scope) : defX;
            scope.setThisValue(MolangValue.of(defY));
            out[1] = y != null ? y.evaluateFloat(scope) : defY;
            scope.setThisValue(MolangValue.of(defZ));
            out[2] = z != null ? z.evaluateFloat(scope) : defZ;
        }
    }

    public record Keyframe(float time, Vec3Expr pre, Vec3Expr post, LerpMode lerpMode) {
    }

    public static final class ChannelCurve {
        public final boolean isConstant;
        public final Vec3Expr constant;
        public final Keyframe[] keyframes;

        public ChannelCurve(Vec3Expr constant) {
            this.isConstant = true;
            this.constant = constant;
            this.keyframes = null;
        }

        public ChannelCurve(List<Keyframe> sorted) {
            this.isConstant = false;
            this.constant = null;
            this.keyframes = sorted.toArray(new Keyframe[0]);
        }

        public void evaluate(float time, MolangScope scope, float[] out, float defX, float defY, float defZ) {
            if (isConstant) {
                constant.eval(scope, out, defX, defY, defZ);
                return;
            }
            if (keyframes.length == 0) {
                out[0] = defX;
                out[1] = defY;
                out[2] = defZ;
                return;
            }
            if (keyframes.length == 1 || time <= keyframes[0].time) {
                keyframes[0].post.eval(scope, out, defX, defY, defZ);
                return;
            }
            int last = keyframes.length - 1;
            if (time >= keyframes[last].time) {
                keyframes[last].pre.eval(scope, out, defX, defY, defZ);
                return;
            }

            // Binary search bracketing keyframe index
            int lo = 0;
            int hi = last;
            while (lo + 1 < hi) {
                int mid = (lo + hi) >>> 1;
                if (keyframes[mid].time <= time) {
                    lo = mid;
                } else {
                    hi = mid;
                }
            }

            Keyframe k1 = keyframes[lo];
            Keyframe k2 = keyframes[lo + 1];
            float dt = k2.time - k1.time;
            float u = dt <= 1e-6f ? 0f : (time - k1.time) / dt;

            float[] v1 = new float[3];
            float[] v2 = new float[3];
            k1.post.eval(scope, v1, defX, defY, defZ);
            k2.pre.eval(scope, v2, defX, defY, defZ);

            if (k1.lerpMode == LerpMode.STEP) {
                out[0] = v1[0];
                out[1] = v1[1];
                out[2] = v1[2];
                return;
            }

            if (k1.lerpMode == LerpMode.CATMULLROM) {
                Keyframe k0 = keyframes[Math.max(0, lo - 1)];
                Keyframe k3 = keyframes[Math.min(last, lo + 2)];
                float[] v0 = new float[3];
                float[] v3 = new float[3];
                k0.post.eval(scope, v0, defX, defY, defZ);
                k3.pre.eval(scope, v3, defX, defY, defZ);
                for (int i = 0; i < 3; i++) {
                    out[i] = catmullRom(v0[i], v1[i], v2[i], v3[i], u);
                }
                return;
            }

            for (int i = 0; i < 3; i++) {
                out[i] = v1[i] + (v2[i] - v1[i]) * u;
            }
        }

        private static float catmullRom(float p0, float p1, float p2, float p3, float t) {
            float t2 = t * t;
            float t3 = t2 * t;
            return 0.5f * (
                (2f * p1)
                + (-p0 + p2) * t
                + (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2
                + (-p0 + 3f * p1 - 3f * p2 + p3) * t3
            );
        }
    }

    public static final class BoneAnim {
        public final String boneNameLower;
        public final ChannelCurve position;
        public final ChannelCurve rotation;
        public final ChannelCurve scale;
        public final boolean relativeToRotationEntity;

        public BoneAnim(String boneNameLower, ChannelCurve position, ChannelCurve rotation, ChannelCurve scale) {
            this(boneNameLower, position, rotation, scale, false);
        }

        public BoneAnim(String boneNameLower, ChannelCurve position, ChannelCurve rotation, ChannelCurve scale,
                        boolean relativeToRotationEntity) {
            this.boneNameLower = boneNameLower;
            this.position = position;
            this.rotation = rotation;
            this.scale = scale;
            this.relativeToRotationEntity = relativeToRotationEntity;
        }
    }

    public record ParticleTrigger(float time, String effect, String locator, MolangExpr preEffectScript, boolean bindToActor) {
    }

    public record SoundTrigger(float time, String effect) {
    }

    public record TimelineEvent(float time, List<MolangExpr> scripts) {
    }

    public final String name;
    public final String nameLower;
    public final LoopMode loopMode;
    public final float animationLength;
    public final boolean overridePreviousAnimation;
    public final MolangExpr animTimeUpdate;
    public final MolangExpr blendWeight;
    public final Map<String, BoneAnim> bones = new LinkedHashMap<>();
    public final List<ParticleTrigger> particleEffects = new ArrayList<>();
    public final List<SoundTrigger> soundEffects = new ArrayList<>();
    public final List<TimelineEvent> timeline = new ArrayList<>();

    public BedrockAnimation(String name, LoopMode loopMode, float animationLength,
                            boolean overridePreviousAnimation, MolangExpr animTimeUpdate, MolangExpr blendWeight) {
        this.name = name;
        this.nameLower = name.toLowerCase(Locale.ROOT);
        this.loopMode = loopMode;
        this.animationLength = Math.max(0f, animationLength);
        this.overridePreviousAnimation = overridePreviousAnimation;
        this.animTimeUpdate = animTimeUpdate;
        this.blendWeight = blendWeight;
    }

    public static Map<String, BedrockAnimation> parseFile(String jsonText) {
        Map<String, BedrockAnimation> out = new LinkedHashMap<>();
        if (jsonText == null || jsonText.isBlank()) {
            return out;
        }
        JsonObject root = JsonParser.parseString(jsonText).getAsJsonObject();
        JsonObject anims = root.has("animations") && root.get("animations").isJsonObject()
            ? root.getAsJsonObject("animations") : root;

        for (Map.Entry<String, JsonElement> e : anims.entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            BedrockAnimation anim = parseSingle(e.getKey(), e.getValue().getAsJsonObject());
            out.put(anim.nameLower, anim);
        }
        return out;
    }

    private static BedrockAnimation parseSingle(String name, JsonObject obj) {
        LoopMode loop = LoopMode.ONCE;
        if (obj.has("loop")) {
            JsonElement l = obj.get("loop");
            if (l.isJsonPrimitive()) {
                var p = l.getAsJsonPrimitive();
                if (p.isBoolean() && p.getAsBoolean()) {
                    loop = LoopMode.LOOP;
                } else if (p.isString()) {
                    String s = p.getAsString();
                    if ("hold_on_last_frame".equalsIgnoreCase(s)) loop = LoopMode.HOLD_ON_LAST_FRAME;
                    else if ("true".equalsIgnoreCase(s)) loop = LoopMode.LOOP;
                }
            }
        }
        boolean overridePrev = obj.has("override_previous_animation")
            && obj.get("override_previous_animation").isJsonPrimitive()
            && obj.get("override_previous_animation").getAsBoolean();
        MolangExpr timeUpdate = obj.has("anim_time_update") ? MolangParser.parseJson(obj.get("anim_time_update")) : null;
        MolangExpr blend = obj.has("blend_weight") ? MolangParser.parseJson(obj.get("blend_weight")) : MolangExpr.ONE;

        float maxTime = 0f;
        Map<String, BoneAnim> parsedBones = new LinkedHashMap<>();
        if (obj.has("bones") && obj.get("bones").isJsonObject()) {
            for (Map.Entry<String, JsonElement> be : obj.getAsJsonObject("bones").entrySet()) {
                if (!be.getValue().isJsonObject()) continue;
                JsonObject bo = be.getValue().getAsJsonObject();
                float[] boneMax = new float[1];
                ChannelCurve pos = parseChannel(bo.get("position"), 0f, boneMax);
                ChannelCurve rot = parseChannel(bo.get("rotation"), 0f, boneMax);
                ChannelCurve scl = parseChannel(bo.get("scale"), 1f, boneMax);
                boolean relRotEntity = false;
                if (bo.has("relative_to") && bo.get("relative_to").isJsonObject()) {
                    JsonObject rt = bo.getAsJsonObject("relative_to");
                    if (rt.has("rotation") && rt.get("rotation").isJsonPrimitive()
                            && "entity".equalsIgnoreCase(rt.get("rotation").getAsString())) {
                        relRotEntity = true;
                    }
                }
                maxTime = Math.max(maxTime, boneMax[0]);
                String bKey = be.getKey().toLowerCase(Locale.ROOT);
                parsedBones.put(bKey, new BoneAnim(bKey, pos, rot, scl, relRotEntity));
            }
        }

        float len = obj.has("animation_length") && obj.get("animation_length").isJsonPrimitive()
            ? obj.get("animation_length").getAsFloat()
            : maxTime;

        BedrockAnimation anim = new BedrockAnimation(name, loop, len, overridePrev, timeUpdate, blend);
        anim.bones.putAll(parsedBones);

        if (obj.has("particle_effects") && obj.get("particle_effects").isJsonObject()) {
            for (Map.Entry<String, JsonElement> pe : obj.getAsJsonObject("particle_effects").entrySet()) {
                float t = parseTimeKey(pe.getKey());
                addParticles(t, pe.getValue(), anim.particleEffects);
            }
        }
        if (obj.has("sound_effects") && obj.get("sound_effects").isJsonObject()) {
            for (Map.Entry<String, JsonElement> se : obj.getAsJsonObject("sound_effects").entrySet()) {
                float t = parseTimeKey(se.getKey());
                addSounds(t, se.getValue(), anim.soundEffects);
            }
        }
        if (obj.has("timeline") && obj.get("timeline").isJsonObject()) {
            for (Map.Entry<String, JsonElement> te : obj.getAsJsonObject("timeline").entrySet()) {
                float t = parseTimeKey(te.getKey());
                List<MolangExpr> scripts = parseScriptList(te.getValue());
                if (!scripts.isEmpty()) {
                    anim.timeline.add(new TimelineEvent(t, scripts));
                }
            }
        }
        return anim;
    }

    private static ChannelCurve parseChannel(JsonElement el, float defVal, float[] maxTimeOut) {
        if (el == null || el.isJsonNull()) {
            return null;
        }
        if (el.isJsonPrimitive() || el.isJsonArray()) {
            return new ChannelCurve(parseVec3(el, defVal));
        }
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            // Handle single keyframe wrapper like { "vector": [...] } vs map {"0.0": ..., "0.25": ...}
            if (obj.has("vector") && !obj.has("0.0")) {
                return new ChannelCurve(parseVec3(obj.get("vector"), defVal));
            }
            TreeMap<Float, Keyframe> sorted = new TreeMap<>();
            for (Map.Entry<String, JsonElement> kf : obj.entrySet()) {
                float time = parseTimeKey(kf.getKey());
                maxTimeOut[0] = Math.max(maxTimeOut[0], time);
                Keyframe parsed = parseKeyframe(time, kf.getValue(), defVal);
                sorted.put(time, parsed);
            }
            return new ChannelCurve(new ArrayList<>(sorted.values()));
        }
        return null;
    }

    private static Keyframe parseKeyframe(float time, JsonElement val, float defVal) {
        if (val != null && val.isJsonObject()) {
            JsonObject obj = val.getAsJsonObject();
            LerpMode mode = LerpMode.LINEAR;
            if (obj.has("lerp_mode") && obj.get("lerp_mode").isJsonPrimitive()) {
                String m = obj.get("lerp_mode").getAsString().toLowerCase(Locale.ROOT);
                if ("catmullrom".equals(m)) mode = LerpMode.CATMULLROM;
                else if ("step".equals(m)) mode = LerpMode.STEP;
            }
            JsonElement postEl = obj.has("post") ? obj.get("post") : (obj.has("vector") ? obj.get("vector") : val);
            JsonElement preEl = obj.has("pre") ? obj.get("pre") : postEl;
            Vec3Expr post = parseVec3(postEl, defVal);
            Vec3Expr pre = parseVec3(preEl, defVal);
            return new Keyframe(time, pre, post, mode);
        }
        Vec3Expr v = parseVec3(val, defVal);
        return new Keyframe(time, v, v, LerpMode.LINEAR);
    }

    private static Vec3Expr parseVec3(JsonElement el, float defVal) {
        if (el == null || el.isJsonNull()) {
            MolangExpr d = defVal == 0f ? MolangExpr.ZERO : MolangExpr.ONE;
            return new Vec3Expr(d, d, d);
        }
        if (el.isJsonPrimitive()) {
            MolangExpr e = MolangParser.parseJson(el);
            return new Vec3Expr(e, e, e);
        }
        if (el.isJsonObject() && el.getAsJsonObject().has("vector")) {
            return parseVec3(el.getAsJsonObject().get("vector"), defVal);
        }
        if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            MolangExpr d = defVal == 0f ? MolangExpr.ZERO : MolangExpr.ONE;
            MolangExpr x = arr.size() > 0 ? MolangParser.parseJson(arr.get(0)) : d;
            MolangExpr y = arr.size() > 1 ? MolangParser.parseJson(arr.get(1)) : d;
            MolangExpr z = arr.size() > 2 ? MolangParser.parseJson(arr.get(2)) : d;
            return new Vec3Expr(x, y, z);
        }
        MolangExpr d = defVal == 0f ? MolangExpr.ZERO : MolangExpr.ONE;
        return new Vec3Expr(d, d, d);
    }

    private static void addParticles(float t, JsonElement val, List<ParticleTrigger> list) {
        if (val == null) return;
        if (val.isJsonArray()) {
            for (JsonElement item : val.getAsJsonArray()) {
                addParticles(t, item, list);
            }
            return;
        }
        if (val.isJsonObject()) {
            JsonObject o = val.getAsJsonObject();
            String effect = o.has("effect") ? o.get("effect").getAsString() : "";
            String locator = o.has("locator") ? o.get("locator").getAsString() : "";
            MolangExpr pre = o.has("pre_effect_script") ? MolangParser.parseJson(o.get("pre_effect_script")) : null;
            boolean bind = !o.has("bind_to_actor") || o.get("bind_to_actor").getAsBoolean();
            if (!effect.isEmpty()) {
                list.add(new ParticleTrigger(t, effect, locator, pre, bind));
            }
        }
    }

    private static void addSounds(float t, JsonElement val, List<SoundTrigger> list) {
        if (val == null) return;
        if (val.isJsonArray()) {
            for (JsonElement item : val.getAsJsonArray()) {
                addSounds(t, item, list);
            }
            return;
        }
        if (val.isJsonObject()) {
            JsonObject o = val.getAsJsonObject();
            String effect = o.has("effect") ? o.get("effect").getAsString() : "";
            if (!effect.isEmpty()) {
                list.add(new SoundTrigger(t, effect));
            }
        }
    }

    private static List<MolangExpr> parseScriptList(JsonElement val) {
        if (val == null || val.isJsonNull()) return Collections.emptyList();
        if (val.isJsonPrimitive()) {
            return List.of(MolangParser.parseJson(val));
        }
        if (val.isJsonArray()) {
            List<MolangExpr> out = new ArrayList<>();
            for (JsonElement el : val.getAsJsonArray()) {
                out.add(MolangParser.parseJson(el));
            }
            return out;
        }
        return Collections.emptyList();
    }

    private static float parseTimeKey(String key) {
        try {
            return Float.parseFloat(key.trim());
        } catch (NumberFormatException ignored) {
            return 0f;
        }
    }

    /** Registers vanilla built-in animation fallbacks referenced by Actions &amp; Stuff entity JSONs. */
    public static void addBuiltinFallbacks(Map<String, BedrockAnimation> registry) {
        String[] builtins = {
            "animation.armor.boots.offset", "animation.armor.chestplate.offset",
            "animation.armor.helmet.offset", "animation.armor.leggings.offset",
            "animation.axolotl.play_dead", "animation.bee.no_stinger", "animation.cod.flop",
            "animation.cow.baby_transform", "animation.elytra.default", "animation.elytra.sleeping",
            "animation.elytra.sneaking", "animation.evocation_fang.bite", "animation.humanoid.big_head",
            "animation.humanoid.riding.legs", "animation.iubeld", "animation.pig.baby_transform",
            "animation.player.look_at_target.ui", "animation.player.riding.arms", "animation.player.sleeping",
            "animation.polarbear.baby_transform", "animation.ravager.idle_mouth", "animation.tropicalfish.flop",
            "animation.villager.baby_transform", "animation.villager.raise_arms"
        };
        for (String id : builtins) {
            registry.computeIfAbsent(id, k -> {
                BedrockAnimation a = new BedrockAnimation(k, LoopMode.LOOP, 1.0f, false, null, MolangExpr.ONE);
                if (k.endsWith(".baby_transform")) {
                    a.bones.put("head", new BoneAnim("head",
                        null, null,
                        new ChannelCurve(new Vec3Expr(MolangParser.parse("2"), MolangParser.parse("2"), MolangParser.parse("2")))));
                } else if (k.equals("animation.humanoid.big_head")) {
                    a.bones.put("head", new BoneAnim("head",
                        null, null,
                        new ChannelCurve(new Vec3Expr(MolangParser.parse("1.5"), MolangParser.parse("1.5"), MolangParser.parse("1.5")))));
                } else if (k.equals("animation.player.sleeping")) {
                    a.bones.put("root", new BoneAnim("root",
                        null,
                        new ChannelCurve(new Vec3Expr(MolangParser.parse("-90"), MolangExpr.ZERO, MolangExpr.ZERO)),
                        null));
                }
                return a;
            });
        }
    }
}
