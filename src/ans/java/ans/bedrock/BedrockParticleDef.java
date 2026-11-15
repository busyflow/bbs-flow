package ans.bedrock;

import ans.molang.MolangExpr;
import ans.molang.MolangMath;
import ans.molang.MolangParser;
import ans.molang.MolangScope;
import ans.molang.MolangValue;
import com.google.gson.JsonArray;
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
 * Parses Bedrock {@code particles/*.json} ({@code minecraft:particle_effect}) definitions
 * including emitter shapes/rates, curves, dynamic/parametric motion, flipbook billboards,
 * and gradient/MoLang tinting.
 */
public final class BedrockParticleDef {

    public record CurveDef(String varNameLower, String type, MolangExpr input, MolangExpr range, List<MolangExpr> nodes) {
        public float eval(MolangScope scope) {
            if (nodes.isEmpty()) return 0f;
            float r = range != null ? Math.max(1e-5f, range.evaluateFloat(scope)) : 1f;
            float x = Math.max(0f, Math.min(1f, input.evaluateFloat(scope) / r));
            if (nodes.size() == 1) return nodes.get(0).evaluateFloat(scope);
            float scaled = x * (nodes.size() - 1);
            int i0 = Math.min(nodes.size() - 2, Math.max(0, (int) Math.floor(scaled)));
            float u = scaled - i0;
            float v0 = nodes.get(i0).evaluateFloat(scope);
            float v1 = nodes.get(i0 + 1).evaluateFloat(scope);
            return v0 + (v1 - v0) * u;
        }
    }

    public enum EmitterRateMode { INSTANT, STEADY, MANUAL }
    public enum EmitterLifetimeMode { ONCE, LOOPING, EXPRESSION }
    public enum EmitterShape { POINT, SPHERE, BOX, DISC }
    public enum MaterialType { ALPHA, BLEND, ADD, OPAQUE }

    public final String identifier;
    public final String identifierLower;
    public String texturePath = "textures/particle/particles";
    public MaterialType materialType = MaterialType.ALPHA;
    public final List<CurveDef> curves = new ArrayList<>();

    public final List<MolangExpr> emitterInit = new ArrayList<>();
    public final List<MolangExpr> emitterUpdate = new ArrayList<>();
    public final List<MolangExpr> particleUpdate = new ArrayList<>();

    public EmitterRateMode rateMode = EmitterRateMode.STEADY;
    public MolangExpr spawnRate = MolangParser.parse("10");
    public MolangExpr maxParticles = MolangParser.parse("50");

    public EmitterLifetimeMode lifetimeMode = EmitterLifetimeMode.ONCE;
    public MolangExpr activeTime = MolangParser.parse("1");
    public MolangExpr sleepTime = MolangExpr.ZERO;
    public MolangExpr activationExpr = MolangExpr.ONE;
    public MolangExpr expirationExpr = MolangExpr.ZERO;

    public EmitterShape shape = EmitterShape.POINT;
    public MolangExpr[] shapeOffset = {MolangExpr.ZERO, MolangExpr.ZERO, MolangExpr.ZERO};
    public MolangExpr shapeRadius = MolangExpr.ONE;
    public MolangExpr[] shapeHalfDims = {MolangExpr.ONE, MolangExpr.ONE, MolangExpr.ONE};
    public MolangExpr[] shapeDir = null;

    public MolangExpr particleMaxLifetime = MolangParser.parse("1");
    public MolangExpr particleExpiration = MolangExpr.ZERO;
    public MolangExpr initialSpeedScalar = MolangExpr.ZERO;
    public MolangExpr[] initialSpeedVec = null;
    public MolangExpr initialRot = MolangExpr.ZERO;
    public MolangExpr initialRotRate = MolangExpr.ZERO;

    public MolangExpr[] linearAccel = {MolangExpr.ZERO, MolangExpr.ZERO, MolangExpr.ZERO};
    public MolangExpr linearDrag = MolangExpr.ZERO;
    public MolangExpr rotAccel = MolangExpr.ZERO;
    public MolangExpr rotDrag = MolangExpr.ZERO;

    public boolean parametricMotion = false;
    public MolangExpr[] parametricPos = null;
    public MolangExpr parametricRot = null;

    public MolangExpr sizeX = MolangParser.parse("0.15");
    public MolangExpr sizeY = MolangParser.parse("0.15");
    public int texWidth = 128;
    public int texHeight = 128;
    public MolangExpr uvU = MolangExpr.ZERO;
    public MolangExpr uvV = MolangExpr.ZERO;
    public MolangExpr uvW = MolangParser.parse("8");
    public MolangExpr uvH = MolangParser.parse("8");
    public boolean hasFlipbook = false;
    public MolangExpr fbBaseU = MolangExpr.ZERO;
    public MolangExpr fbBaseV = MolangExpr.ZERO;
    public float fbSizeU = 8f, fbSizeV = 8f, fbStepU = 8f, fbStepV = 0f;
    public float fbFps = 10f;
    public MolangExpr fbMaxFrame = MolangParser.parse("8");
    public boolean fbStretchToLifetime = false;
    public boolean fbLoop = false;

    public boolean affectedByLighting = false;
    public MolangExpr[] colorTint = {MolangExpr.ONE, MolangExpr.ONE, MolangExpr.ONE, MolangExpr.ONE};
    public TreeMap<Float, float[]> colorGradient = null;
    public MolangExpr colorInterpolant = MolangParser.parse("v.particle_age / v.particle_lifetime");

    public BedrockParticleDef(String identifier) {
        this.identifier = identifier;
        this.identifierLower = identifier.toLowerCase(Locale.ROOT);
    }

    public static BedrockParticleDef parseFile(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) return null;
        JsonObject root = JsonParser.parseString(jsonText).getAsJsonObject();
        JsonObject pe = null;
        if (root.has("particle_effect") && root.get("particle_effect").isJsonObject()) {
            pe = root.getAsJsonObject("particle_effect");
        } else if (root.has("minecraft:particle_effect") && root.get("minecraft:particle_effect").isJsonObject()) {
            pe = root.getAsJsonObject("minecraft:particle_effect");
        }
        if (pe == null) return null;

        JsonObject desc = pe.has("description") && pe.get("description").isJsonObject()
            ? pe.getAsJsonObject("description") : null;
        if (desc == null || !desc.has("identifier")) return null;

        BedrockParticleDef def = new BedrockParticleDef(desc.get("identifier").getAsString());
        if (desc.has("basic_render_parameters") && desc.get("basic_render_parameters").isJsonObject()) {
            JsonObject brp = desc.getAsJsonObject("basic_render_parameters");
            if (brp.has("texture") && brp.get("texture").isJsonPrimitive()) {
                def.texturePath = normalizeTexPath(brp.get("texture").getAsString());
            }
            if (brp.has("material") && brp.get("material").isJsonPrimitive()) {
                String mat = brp.get("material").getAsString().toLowerCase(Locale.ROOT);
                if (mat.contains("add")) def.materialType = MaterialType.ADD;
                else if (mat.contains("blend")) def.materialType = MaterialType.BLEND;
                else if (mat.contains("opaque")) def.materialType = MaterialType.OPAQUE;
                else def.materialType = MaterialType.ALPHA;
            }
        }

        if (pe.has("curves") && pe.get("curves").isJsonObject()) {
            for (Map.Entry<String, JsonElement> ce : pe.getAsJsonObject("curves").entrySet()) {
                if (!ce.getValue().isJsonObject()) continue;
                JsonObject co = ce.getValue().getAsJsonObject();
                String varKey = ce.getKey().toLowerCase(Locale.ROOT);
                if (varKey.startsWith("variable.") || varKey.startsWith("v.")) {
                    varKey = varKey.substring(varKey.indexOf('.') + 1);
                }
                String type = co.has("type") ? co.get("type").getAsString() : "linear";
                MolangExpr input = co.has("input") ? MolangParser.parseJson(co.get("input")) : MolangExpr.ZERO;
                MolangExpr range = co.has("horizontal_range") ? MolangParser.parseJson(co.get("horizontal_range")) : MolangExpr.ONE;
                List<MolangExpr> nodes = new ArrayList<>();
                if (co.has("nodes") && co.get("nodes").isJsonArray()) {
                    for (JsonElement n : co.getAsJsonArray("nodes")) {
                        nodes.add(MolangParser.parseJson(n));
                    }
                }
                def.curves.add(new CurveDef(varKey, type, input, range, nodes));
            }
        }

        if (pe.has("components") && pe.get("components").isJsonObject()) {
            parseComponents(pe.getAsJsonObject("components"), def);
        }
        return def;
    }

    private static void parseComponents(JsonObject comps, BedrockParticleDef def) {
        if (comps.has("minecraft:emitter_initialization") && comps.get("minecraft:emitter_initialization").isJsonObject()) {
            JsonObject o = comps.getAsJsonObject("minecraft:emitter_initialization");
            BedrockEntityDef.parseExprList(o.get("creation_expression"), def.emitterInit);
            BedrockEntityDef.parseExprList(o.get("per_update_expression"), def.emitterUpdate);
        }
        if (comps.has("minecraft:particle_initialization") && comps.get("minecraft:particle_initialization").isJsonObject()) {
            JsonObject o = comps.getAsJsonObject("minecraft:particle_initialization");
            BedrockEntityDef.parseExprList(o.get("per_update_expression"), def.particleUpdate);
        }
        if (comps.has("minecraft:emitter_rate_instant") && comps.get("minecraft:emitter_rate_instant").isJsonObject()) {
            def.rateMode = EmitterRateMode.INSTANT;
            JsonObject o = comps.getAsJsonObject("minecraft:emitter_rate_instant");
            if (o.has("num_particles")) def.maxParticles = MolangParser.parseJson(o.get("num_particles"));
        } else if (comps.has("minecraft:emitter_rate_steady") && comps.get("minecraft:emitter_rate_steady").isJsonObject()) {
            def.rateMode = EmitterRateMode.STEADY;
            JsonObject o = comps.getAsJsonObject("minecraft:emitter_rate_steady");
            if (o.has("spawn_rate")) def.spawnRate = MolangParser.parseJson(o.get("spawn_rate"));
            if (o.has("max_particles")) def.maxParticles = MolangParser.parseJson(o.get("max_particles"));
        } else if (comps.has("minecraft:emitter_rate_manual")) {
            def.rateMode = EmitterRateMode.MANUAL;
        }

        if (comps.has("minecraft:emitter_lifetime_looping") && comps.get("minecraft:emitter_lifetime_looping").isJsonObject()) {
            def.lifetimeMode = EmitterLifetimeMode.LOOPING;
            JsonObject o = comps.getAsJsonObject("minecraft:emitter_lifetime_looping");
            if (o.has("active_time")) def.activeTime = MolangParser.parseJson(o.get("active_time"));
            if (o.has("sleep_time")) def.sleepTime = MolangParser.parseJson(o.get("sleep_time"));
        } else if (comps.has("minecraft:emitter_lifetime_expression") && comps.get("minecraft:emitter_lifetime_expression").isJsonObject()) {
            def.lifetimeMode = EmitterLifetimeMode.EXPRESSION;
            JsonObject o = comps.getAsJsonObject("minecraft:emitter_lifetime_expression");
            if (o.has("activation_expression")) def.activationExpr = MolangParser.parseJson(o.get("activation_expression"));
            if (o.has("expiration_expression")) def.expirationExpr = MolangParser.parseJson(o.get("expiration_expression"));
        } else if (comps.has("minecraft:emitter_lifetime_once") && comps.get("minecraft:emitter_lifetime_once").isJsonObject()) {
            def.lifetimeMode = EmitterLifetimeMode.ONCE;
            JsonObject o = comps.getAsJsonObject("minecraft:emitter_lifetime_once");
            if (o.has("active_time")) def.activeTime = MolangParser.parseJson(o.get("active_time"));
        }

        parseShape(comps, def);

        if (comps.has("minecraft:particle_lifetime_expression") && comps.get("minecraft:particle_lifetime_expression").isJsonObject()) {
            JsonObject o = comps.getAsJsonObject("minecraft:particle_lifetime_expression");
            if (o.has("max_lifetime")) def.particleMaxLifetime = MolangParser.parseJson(o.get("max_lifetime"));
            if (o.has("expiration_expression")) def.particleExpiration = MolangParser.parseJson(o.get("expiration_expression"));
        }
        if (comps.has("minecraft:particle_initial_speed")) {
            JsonElement el = comps.get("minecraft:particle_initial_speed");
            if (el.isJsonArray()) def.initialSpeedVec = parseVec3(el, MolangExpr.ZERO);
            else def.initialSpeedScalar = MolangParser.parseJson(el);
        }
        if (comps.has("minecraft:particle_initial_spin") && comps.get("minecraft:particle_initial_spin").isJsonObject()) {
            JsonObject o = comps.getAsJsonObject("minecraft:particle_initial_spin");
            if (o.has("rotation")) def.initialRot = MolangParser.parseJson(o.get("rotation"));
            if (o.has("rotation_rate")) def.initialRotRate = MolangParser.parseJson(o.get("rotation_rate"));
        }
        if (comps.has("minecraft:particle_motion_dynamic") && comps.get("minecraft:particle_motion_dynamic").isJsonObject()) {
            JsonObject o = comps.getAsJsonObject("minecraft:particle_motion_dynamic");
            if (o.has("linear_acceleration")) def.linearAccel = parseVec3(o.get("linear_acceleration"), MolangExpr.ZERO);
            if (o.has("linear_drag_coefficient")) def.linearDrag = MolangParser.parseJson(o.get("linear_drag_coefficient"));
            if (o.has("rotation_acceleration")) def.rotAccel = MolangParser.parseJson(o.get("rotation_acceleration"));
            if (o.has("rotation_drag_coefficient")) def.rotDrag = MolangParser.parseJson(o.get("rotation_drag_coefficient"));
        }
        if (comps.has("minecraft:particle_motion_parametric") && comps.get("minecraft:particle_motion_parametric").isJsonObject()) {
            def.parametricMotion = true;
            JsonObject o = comps.getAsJsonObject("minecraft:particle_motion_parametric");
            if (o.has("relative_position")) def.parametricPos = parseVec3(o.get("relative_position"), MolangExpr.ZERO);
            if (o.has("rotation")) def.parametricRot = MolangParser.parseJson(o.get("rotation"));
        }
        if (comps.has("minecraft:particle_appearance_billboard") && comps.get("minecraft:particle_appearance_billboard").isJsonObject()) {
            JsonObject bb = comps.getAsJsonObject("minecraft:particle_appearance_billboard");
            if (bb.has("size") && bb.get("size").isJsonArray()) {
                JsonArray sa = bb.getAsJsonArray("size");
                if (sa.size() > 0) def.sizeX = MolangParser.parseJson(sa.get(0));
                if (sa.size() > 1) def.sizeY = MolangParser.parseJson(sa.get(1));
            }
            if (bb.has("uv") && bb.get("uv").isJsonObject()) {
                JsonObject uv = bb.getAsJsonObject("uv");
                def.texWidth = uv.has("texture_width") ? Math.max(1, uv.get("texture_width").getAsInt()) : 128;
                def.texHeight = uv.has("texture_height") ? Math.max(1, uv.get("texture_height").getAsInt()) : 128;
                if (uv.has("uv") && uv.get("uv").isJsonArray()) {
                    JsonArray u = uv.getAsJsonArray("uv");
                    if (u.size() > 0) def.uvU = MolangParser.parseJson(u.get(0));
                    if (u.size() > 1) def.uvV = MolangParser.parseJson(u.get(1));
                }
                if (uv.has("uv_size") && uv.get("uv_size").isJsonArray()) {
                    JsonArray s = uv.getAsJsonArray("uv_size");
                    if (s.size() > 0) def.uvW = MolangParser.parseJson(s.get(0));
                    if (s.size() > 1) def.uvH = MolangParser.parseJson(s.get(1));
                }
                if (uv.has("flipbook") && uv.get("flipbook").isJsonObject()) {
                    def.hasFlipbook = true;
                    JsonObject fb = uv.getAsJsonObject("flipbook");
                    if (fb.has("base_UV") && fb.get("base_UV").isJsonArray()) {
                        JsonArray b = fb.getAsJsonArray("base_UV");
                        if (b.size() > 0) def.fbBaseU = MolangParser.parseJson(b.get(0));
                        if (b.size() > 1) def.fbBaseV = MolangParser.parseJson(b.get(1));
                    }
                    if (fb.has("size_UV") && fb.get("size_UV").isJsonArray()) {
                        JsonArray s = fb.getAsJsonArray("size_UV");
                        if (s.size() > 0) def.fbSizeU = s.get(0).getAsFloat();
                        if (s.size() > 1) def.fbSizeV = s.get(1).getAsFloat();
                    }
                    if (fb.has("step_UV") && fb.get("step_UV").isJsonArray()) {
                        JsonArray st = fb.getAsJsonArray("step_UV");
                        if (st.size() > 0) def.fbStepU = st.get(0).getAsFloat();
                        if (st.size() > 1) def.fbStepV = st.get(1).getAsFloat();
                    }
                    def.fbFps = fb.has("frames_per_second") ? fb.get("frames_per_second").getAsFloat() : 10f;
                    if (fb.has("max_frame")) def.fbMaxFrame = MolangParser.parseJson(fb.get("max_frame"));
                    def.fbStretchToLifetime = fb.has("stretch_to_lifetime") && fb.get("stretch_to_lifetime").getAsBoolean();
                    def.fbLoop = fb.has("loop") && fb.get("loop").getAsBoolean();
                }
            }
        }
        def.affectedByLighting = comps.has("minecraft:particle_appearance_lighting");
        if (comps.has("minecraft:particle_appearance_tinting") && comps.get("minecraft:particle_appearance_tinting").isJsonObject()) {
            JsonObject t = comps.getAsJsonObject("minecraft:particle_appearance_tinting");
            if (t.has("color")) {
                parseColor(t.get("color"), def);
            }
        }
    }

    private static void parseShape(JsonObject comps, BedrockParticleDef def) {
        String[] keys = {
            "minecraft:emitter_shape_point", "minecraft:emitter_shape_sphere",
            "minecraft:emitter_shape_box", "minecraft:emitter_shape_disc", "minecraft:emitter_shape_entity_aabb"
        };
        for (String key : keys) {
            if (comps.has(key) && comps.get(key).isJsonObject()) {
                if (key.endsWith("sphere")) def.shape = EmitterShape.SPHERE;
                else if (key.endsWith("box") || key.endsWith("entity_aabb")) def.shape = EmitterShape.BOX;
                else if (key.endsWith("disc")) def.shape = EmitterShape.DISC;
                else def.shape = EmitterShape.POINT;
                JsonObject o = comps.getAsJsonObject(key);
                if (o.has("offset")) def.shapeOffset = parseVec3(o.get("offset"), MolangExpr.ZERO);
                if (o.has("radius")) def.shapeRadius = MolangParser.parseJson(o.get("radius"));
                if (o.has("half_dimensions")) def.shapeHalfDims = parseVec3(o.get("half_dimensions"), MolangExpr.ONE);
                if (o.has("direction") && o.get("direction").isJsonArray()) {
                    def.shapeDir = parseVec3(o.get("direction"), MolangExpr.ZERO);
                }
                break;
            }
        }
    }

    private static void parseColor(JsonElement el, BedrockParticleDef def) {
        if (el.isJsonPrimitive()) {
            float[] rgba = parseHexColor(el.getAsString());
            def.colorTint = new MolangExpr[]{
                MolangParser.parse(Float.toString(rgba[0])),
                MolangParser.parse(Float.toString(rgba[1])),
                MolangParser.parse(Float.toString(rgba[2])),
                MolangParser.parse(Float.toString(rgba[3]))
            };
        } else if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            def.colorTint = new MolangExpr[]{
                arr.size() > 0 ? MolangParser.parseJson(arr.get(0)) : MolangExpr.ONE,
                arr.size() > 1 ? MolangParser.parseJson(arr.get(1)) : MolangExpr.ONE,
                arr.size() > 2 ? MolangParser.parseJson(arr.get(2)) : MolangExpr.ONE,
                arr.size() > 3 ? MolangParser.parseJson(arr.get(3)) : MolangExpr.ONE
            };
        } else if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("interpolant")) {
                def.colorInterpolant = MolangParser.parseJson(obj.get("interpolant"));
            }
            if (obj.has("gradient") && obj.get("gradient").isJsonObject()) {
                def.colorGradient = new TreeMap<>();
                for (Map.Entry<String, JsonElement> ge : obj.getAsJsonObject("gradient").entrySet()) {
                    try {
                        float stop = Float.parseFloat(ge.getKey());
                        def.colorGradient.put(stop, parseColorStop(ge.getValue()));
                    } catch (Exception ignored) {
                    }
                }
            } else if (obj.has("gradient") && obj.get("gradient").isJsonArray()) {
                def.colorGradient = new TreeMap<>();
                JsonArray ga = obj.getAsJsonArray("gradient");
                for (int i = 0; i < ga.size(); i++) {
                    float stop = ga.size() == 1 ? 0f : (float) i / (ga.size() - 1);
                    def.colorGradient.put(stop, parseColorStop(ga.get(i)));
                }
            }
        }
    }

    private static float[] parseColorStop(JsonElement el) {
        if (el.isJsonPrimitive()) return parseHexColor(el.getAsString());
        if (el.isJsonArray()) {
            JsonArray a = el.getAsJsonArray();
            return new float[]{
                a.size() > 0 ? a.get(0).getAsFloat() : 1f,
                a.size() > 1 ? a.get(1).getAsFloat() : 1f,
                a.size() > 2 ? a.get(2).getAsFloat() : 1f,
                a.size() > 3 ? a.get(3).getAsFloat() : 1f
            };
        }
        return new float[]{1f, 1f, 1f, 1f};
    }

    public static float[] parseHexColor(String hex) {
        if (hex == null) return new float[]{1f, 1f, 1f, 1f};
        String s = hex.trim();
        if (s.startsWith("#")) s = s.substring(1);
        try {
            long val = Long.parseLong(s, 16);
            if (s.length() == 6) {
                return new float[]{((val >> 16) & 0xFF) / 255f, ((val >> 8) & 0xFF) / 255f, (val & 0xFF) / 255f, 1f};
            } else if (s.length() == 8) {
                return new float[]{((val >> 16) & 0xFF) / 255f, ((val >> 8) & 0xFF) / 255f, (val & 0xFF) / 255f, ((val >> 24) & 0xFF) / 255f};
            }
        } catch (NumberFormatException ignored) {
        }
        return new float[]{1f, 1f, 1f, 1f};
    }

    public void evalCurves(MolangScope scope) {
        for (CurveDef c : curves) {
            scope.setVariable(c.varNameLower, MolangValue.of(c.eval(scope)));
        }
    }

    private static MolangExpr[] parseVec3(JsonElement el, MolangExpr defVal) {
        if (el == null || !el.isJsonArray()) return new MolangExpr[]{defVal, defVal, defVal};
        JsonArray a = el.getAsJsonArray();
        return new MolangExpr[]{
            a.size() > 0 ? MolangParser.parseJson(a.get(0)) : defVal,
            a.size() > 1 ? MolangParser.parseJson(a.get(1)) : defVal,
            a.size() > 2 ? MolangParser.parseJson(a.get(2)) : defVal
        };
    }

    private static String normalizeTexPath(String raw) {
        String p = raw.replace('\\', '/').toLowerCase(Locale.ROOT).trim();
        while (p.startsWith("../")) {
            p = p.substring(3);
        }
        if ("atlas.terrain".equals(p)) {
            return "textures/particle/particles";
        }
        if (p.endsWith(".png") || p.endsWith(".tga")) p = p.substring(0, p.length() - 4);
        if (!p.startsWith("textures/") && !p.isEmpty()) {
            p = "textures/" + p;
        }
        return p;
    }
}
