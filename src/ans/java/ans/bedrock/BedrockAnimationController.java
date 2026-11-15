package ans.bedrock;

import ans.molang.MolangExpr;
import ans.molang.MolangParser;
import ans.molang.MolangQueryContext;
import ans.molang.MolangScope;
import ans.molang.MolangValue;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Parses and executes Bedrock {@code animation_controllers/*.json} finite-state machines
 * with conditional transitions, {@code on_entry}/{@code on_exit} scripts, remap variables,
 * blend transitions, and weighted animation outputs.
 */
public final class BedrockAnimationController {

    public record Transition(String targetStateLower, MolangExpr condition) {
    }

    public record AnimRef(String shortNameLower, MolangExpr weightExpr) {
    }

    public record ControllerVar(String varNameLower, MolangExpr input, TreeMap<Float, Float> remapCurve) {
    }

    public record ParticleEntry(String effect, String locator, MolangExpr preEffectScript) {
    }

    public record SoundEntry(String effect) {
    }

    public static final class StateDef {
        public final String nameLower;
        public final List<ControllerVar> variables = new ArrayList<>();
        public final List<AnimRef> animations = new ArrayList<>();
        public final List<MolangExpr> onEntry = new ArrayList<>();
        public final List<MolangExpr> onExit = new ArrayList<>();
        public final List<ParticleEntry> particleEffects = new ArrayList<>();
        public final List<SoundEntry> soundEffects = new ArrayList<>();
        public final List<Transition> transitions = new ArrayList<>();
        public float blendTransition = 0f;

        public StateDef(String nameLower) {
            this.nameLower = nameLower;
        }
    }

    public final String identifier;
    public final String identifierLower;
    public final String initialStateLower;
    public final Map<String, StateDef> states = new LinkedHashMap<>();

    public BedrockAnimationController(String identifier, String initialStateLower) {
        this.identifier = identifier;
        this.identifierLower = identifier.toLowerCase(Locale.ROOT);
        this.initialStateLower = initialStateLower;
    }

    public static Map<String, BedrockAnimationController> parseFile(String jsonText) {
        Map<String, BedrockAnimationController> out = new LinkedHashMap<>();
        if (jsonText == null || jsonText.isBlank()) {
            return out;
        }
        JsonObject root = JsonParser.parseString(jsonText).getAsJsonObject();
        JsonObject ctrls = root.has("animation_controllers") && root.get("animation_controllers").isJsonObject()
            ? root.getAsJsonObject("animation_controllers") : root;

        for (Map.Entry<String, JsonElement> e : ctrls.entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            BedrockAnimationController ctrl = parseSingle(e.getKey(), e.getValue().getAsJsonObject());
            out.put(ctrl.identifierLower, ctrl);
        }
        return out;
    }

    private static BedrockAnimationController parseSingle(String id, JsonObject obj) {
        String initial = obj.has("initial_state") && obj.get("initial_state").isJsonPrimitive()
            ? obj.get("initial_state").getAsString().toLowerCase(Locale.ROOT)
            : "default";
        BedrockAnimationController ctrl = new BedrockAnimationController(id, initial);

        if (obj.has("states") && obj.get("states").isJsonObject()) {
            for (Map.Entry<String, JsonElement> se : obj.getAsJsonObject("states").entrySet()) {
                if (!se.getValue().isJsonObject()) continue;
                StateDef st = parseState(se.getKey().toLowerCase(Locale.ROOT), se.getValue().getAsJsonObject());
                ctrl.states.put(st.nameLower, st);
            }
        }
        return ctrl;
    }

    private static StateDef parseState(String nameLower, JsonObject obj) {
        StateDef st = new StateDef(nameLower);
        if (obj.has("blend_transition")) {
            JsonElement bt = obj.get("blend_transition");
            if (bt.isJsonPrimitive()) {
                st.blendTransition = bt.getAsFloat();
            } else if (bt.isJsonObject()) {
                float maxT = 0f;
                for (Map.Entry<String, JsonElement> e : bt.getAsJsonObject().entrySet()) {
                    try {
                        maxT = Math.max(maxT, Float.parseFloat(e.getKey()));
                    } catch (NumberFormatException ignored) {
                    }
                }
                st.blendTransition = maxT;
            }
        }
        if (obj.has("variables") && obj.get("variables").isJsonObject()) {
            for (Map.Entry<String, JsonElement> ve : obj.getAsJsonObject("variables").entrySet()) {
                if (!ve.getValue().isJsonObject()) continue;
                JsonObject vo = ve.getValue().getAsJsonObject();
                MolangExpr input = vo.has("input") ? MolangParser.parseJson(vo.get("input")) : MolangExpr.ZERO;
                TreeMap<Float, Float> curve = new TreeMap<>();
                if (vo.has("remap_curve") && vo.get("remap_curve").isJsonObject()) {
                    for (Map.Entry<String, JsonElement> ce : vo.getAsJsonObject("remap_curve").entrySet()) {
                        try {
                            curve.put(Float.parseFloat(ce.getKey()), ce.getValue().getAsFloat());
                        } catch (Exception ignored) {
                        }
                    }
                }
                st.variables.add(new ControllerVar(ve.getKey().toLowerCase(Locale.ROOT), input, curve));
            }
        }
        if (obj.has("animations") && obj.get("animations").isJsonArray()) {
            for (JsonElement ae : obj.getAsJsonArray("animations")) {
                if (ae.isJsonPrimitive()) {
                    st.animations.add(new AnimRef(ae.getAsString().toLowerCase(Locale.ROOT), MolangExpr.ONE));
                } else if (ae.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> me : ae.getAsJsonObject().entrySet()) {
                        st.animations.add(new AnimRef(me.getKey().toLowerCase(Locale.ROOT), MolangParser.parseJson(me.getValue())));
                    }
                }
            }
        }
        parseScripts(obj.get("on_entry"), st.onEntry);
        parseScripts(obj.get("on_exit"), st.onExit);
        if (obj.has("particle_effects") && obj.get("particle_effects").isJsonArray()) {
            for (JsonElement pe : obj.getAsJsonArray("particle_effects")) {
                if (!pe.isJsonObject()) continue;
                JsonObject po = pe.getAsJsonObject();
                String eff = po.has("effect") ? po.get("effect").getAsString() : "";
                String loc = po.has("locator") ? po.get("locator").getAsString() : "";
                MolangExpr pre = po.has("pre_effect_script") ? MolangParser.parseJson(po.get("pre_effect_script")) : null;
                if (!eff.isEmpty()) st.particleEffects.add(new ParticleEntry(eff, loc, pre));
            }
        }
        if (obj.has("sound_effects") && obj.get("sound_effects").isJsonArray()) {
            for (JsonElement se : obj.getAsJsonArray("sound_effects")) {
                if (!se.isJsonObject()) continue;
                JsonObject so = se.getAsJsonObject();
                String eff = so.has("effect") ? so.get("effect").getAsString() : "";
                if (!eff.isEmpty()) st.soundEffects.add(new SoundEntry(eff));
            }
        }
        if (obj.has("transitions") && obj.get("transitions").isJsonArray()) {
            for (JsonElement te : obj.getAsJsonArray("transitions")) {
                if (!te.isJsonObject()) continue;
                for (Map.Entry<String, JsonElement> me : te.getAsJsonObject().entrySet()) {
                    st.transitions.add(new Transition(me.getKey().toLowerCase(Locale.ROOT), MolangParser.parseJson(me.getValue())));
                }
            }
        }
        return st;
    }

    private static void parseScripts(JsonElement el, List<MolangExpr> out) {
        if (el == null || el.isJsonNull()) return;
        if (el.isJsonPrimitive()) {
            out.add(MolangParser.parseJson(el));
        } else if (el.isJsonArray()) {
            for (JsonElement item : el.getAsJsonArray()) {
                out.add(MolangParser.parseJson(item));
            }
        }
    }

    /** Per-instance mutable execution state for a single controller bound to an entity/attachable. */
    public static final class RuntimeState {
        public final BedrockAnimationController def;
        public StateDef currentState;
        public StateDef prevState = null;
        public float prevStateTime = 0f;
        public boolean enteredInitial = false;
        public float stateTime = 0f;

        public RuntimeState(BedrockAnimationController def) {
            this.def = def;
            this.currentState = def.states.getOrDefault(def.initialStateLower,
                def.states.isEmpty() ? null : def.states.values().iterator().next());
        }

        public void step(float dt, MolangScope scope, List<ActiveAnimOut> outAnims) {
            step(dt, scope, null, null, outAnims);
        }

        public void step(float dt,
                         MolangScope scope,
                         Map<String, String> animMap,
                         BedrockPackManager pack,
                         List<ActiveAnimOut> outAnims) {
            if (currentState == null) {
                return;
            }
            MolangQueryContext.DefaultContext ctx = scope.getQueryContext() instanceof MolangQueryContext.DefaultContext dc ? dc : null;
            float prevStateTime = ctx != null ? ctx.stateTime : 0f;
            boolean prevAny = ctx != null && ctx.anyAnimationFinished;
            boolean prevAll = ctx != null && ctx.allAnimationsFinished;

            try {
                if (!enteredInitial) {
                    enteredInitial = true;
                    updateFinishFlags(ctx, animMap, pack);
                if (ctx != null) {
                    ctx.stateTime = stateTime;
                }
                    runEntryEffects(currentState, scope);
                }

                updateFinishFlags(ctx, animMap, pack);
                if (ctx != null) {
                    ctx.stateTime = stateTime;
                }

                // Evaluate controller state variables with optional remap curves
                for (ControllerVar cv : currentState.variables) {
                    float raw = cv.input.evaluateFloat(scope);
                    float mapped = evalCurve(cv.remapCurve, raw);
                    scope.setVariable(cv.varNameLower, MolangValue.of(mapped));
                }

                // Evaluate transitions (guard against infinite cycling per tick)
                for (int guard = 0; guard < 4; guard++) {
                    String nextName = null;
                    for (Transition tr : currentState.transitions) {
                        if (tr.condition.evaluateBoolean(scope)) {
                            nextName = tr.targetStateLower;
                            break;
                        }
                    }
                    if (nextName == null || nextName.equals(currentState.nameLower)) {
                        break;
                    }
                    StateDef nextState = def.states.get(nextName);
                    if (nextState == null) {
                        break;
                    }
                    for (MolangExpr exitExpr : currentState.onExit) {
                        exitExpr.evaluate(scope);
                    }
                    prevState = currentState;
                    prevStateTime = stateTime;
                    currentState = nextState;
                    stateTime = 0f;
                    updateFinishFlags(ctx, animMap, pack);
                if (ctx != null) {
                    ctx.stateTime = stateTime;
                }
                    runEntryEffects(currentState, scope);
                }

                stateTime += dt;
                float blendDur = currentState.blendTransition > 1e-4f ? currentState.blendTransition : 0.15f;
                if (prevState != null && stateTime < blendDur) {
                    prevStateTime += dt;
                }
                if (ctx != null) {
                    ctx.stateTime = stateTime;
                }

                if (prevState != null && stateTime < blendDur) {
                    float alpha = Math.max(0f, Math.min(1f, stateTime / blendDur));
                    float invAlpha = 1f - alpha;
                    if (invAlpha > 0.001f) {
                        for (AnimRef ar : prevState.animations) {
                            float w = ar.weightExpr.evaluateFloat(scope) * invAlpha;
                            if (w > 0.001f) {
                                outAnims.add(new ActiveAnimOut(ar.shortNameLower, w, prevStateTime));
                            }
                        }
                    }
                    if (alpha > 0.001f) {
                        for (AnimRef ar : currentState.animations) {
                            float w = ar.weightExpr.evaluateFloat(scope) * alpha;
                            if (w > 0.001f) {
                                outAnims.add(new ActiveAnimOut(ar.shortNameLower, w, stateTime));
                            }
                        }
                    }
                } else {
                    prevState = null;
                    for (AnimRef ar : currentState.animations) {
                        float w = ar.weightExpr.evaluateFloat(scope);
                        if (w > 0.001f) {
                            outAnims.add(new ActiveAnimOut(ar.shortNameLower, w, stateTime));
                        }
                    }
                }
            } finally {
                if (ctx != null) {
                    ctx.stateTime = prevStateTime;
                    ctx.anyAnimationFinished = prevAny;
                    ctx.allAnimationsFinished = prevAll;
                }
            }
        }

        private void updateFinishFlags(MolangQueryContext.DefaultContext ctx,
                                       Map<String, String> animMap,
                                       BedrockPackManager pack) {
            if (ctx == null || currentState == null) {
                return;
            }
            ctx.stateTime = stateTime;
            boolean anyFin = false;
            boolean allFin = true;
            int onceCount = 0;
            for (AnimRef ar : currentState.animations) {
                String mapped = animMap != null
                    ? animMap.getOrDefault(ar.shortNameLower, ar.shortNameLower)
                    : ar.shortNameLower;
                BedrockAnimation anim = pack != null ? pack.animations.get(mapped) : null;
                if (anim != null && anim.loopMode == BedrockAnimation.LoopMode.ONCE) {
                    onceCount++;
                    boolean fin = stateTime >= Math.max(0.05f, anim.animationLength) - 1e-4f;
                    anyFin |= fin;
                    allFin &= fin;
                }
            }
            if (onceCount == 0) {
                anyFin = stateTime >= 0.05f;
                allFin = anyFin;
            }
            ctx.anyAnimationFinished = anyFin;
            ctx.allAnimationsFinished = allFin;
        }

        private static void runEntryEffects(StateDef st, MolangScope scope) {
            for (MolangExpr expr : st.onEntry) {
                expr.evaluate(scope);
            }
            for (SoundEntry se : st.soundEffects) {
                scope.getQueryContext().playSoundEffect(se.effect);
            }
            for (ParticleEntry pe : st.particleEffects) {
                if (pe.preEffectScript != null) {
                    pe.preEffectScript.evaluate(scope);
                }
                scope.getQueryContext().spawnParticleEffect(pe.effect, pe.locator);
            }
        }

        private static float evalCurve(TreeMap<Float, Float> curve, float x) {
            if (curve == null || curve.isEmpty()) return x;
            Map.Entry<Float, Float> floor = curve.floorEntry(x);
            Map.Entry<Float, Float> ceil = curve.ceilingEntry(x);
            if (floor == null) return ceil.getValue();
            if (ceil == null || floor.getKey().equals(ceil.getKey())) return floor.getValue();
            float t = (x - floor.getKey()) / (ceil.getKey() - floor.getKey());
            return floor.getValue() + (ceil.getValue() - floor.getValue()) * t;
        }
    }

    public record ActiveAnimOut(String shortNameLower, float weight, float stateTime) {
    }

    public static void addBuiltinFallbacks(Map<String, BedrockAnimationController> registry) {
        registry.computeIfAbsent("controller.animation.persona.blink",
            k -> new BedrockAnimationController(k, "default"));
        registry.computeIfAbsent("controller.animation.qqjxbi",
            k -> new BedrockAnimationController(k, "default"));
    }
}
