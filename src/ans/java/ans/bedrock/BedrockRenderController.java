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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Parses and evaluates Bedrock {@code render_controllers/*.json} definitions including
 * array lookups, bone wildcard material/visibility rules, {@code uv_anim},
 * {@code color}, {@code is_hurt_color}, and {@code on_fire_color}.
 */
public final class BedrockRenderController {

    public record ColorExpr(MolangExpr r, MolangExpr g, MolangExpr b, MolangExpr a) {
        public void eval(MolangScope scope, float[] out) {
            out[0] = r != null ? r.evaluateFloat(scope) : 1f;
            out[1] = g != null ? g.evaluateFloat(scope) : 1f;
            out[2] = b != null ? b.evaluateFloat(scope) : 1f;
            out[3] = a != null ? a.evaluateFloat(scope) : 1f;
        }
    }

    public record BoneRule(Pattern pattern, MolangExpr valueExpr) {
        public boolean matches(String boneNameLower) {
            return pattern.matcher(boneNameLower).matches();
        }
    }

    public final String identifier;
    public final String identifierLower;
    public final Map<String, List<MolangExpr>> geometryArrays = new LinkedHashMap<>();
    public final Map<String, List<MolangExpr>> materialArrays = new LinkedHashMap<>();
    public final Map<String, List<MolangExpr>> textureArrays = new LinkedHashMap<>();
    public MolangExpr geometryExpr = MolangParser.parse("Geometry.default");
    public final List<BoneRule> materials = new ArrayList<>();
    public final List<BoneRule> partVisibility = new ArrayList<>();
    public final List<MolangExpr> textures = new ArrayList<>();
    public ColorExpr color;
    public ColorExpr overlayColor;
    public ColorExpr isHurtColor;
    public ColorExpr onFireColor;
    public MolangExpr uvOffsetU;
    public MolangExpr uvOffsetV;
    public MolangExpr uvScaleU;
    public MolangExpr uvScaleV;
    public boolean ignoreLighting = false;
    public boolean filterLighting = false;

    public BedrockRenderController(String identifier) {
        this.identifier = identifier;
        this.identifierLower = identifier.toLowerCase(Locale.ROOT);
    }

    public static Map<String, BedrockRenderController> parseFile(String jsonText) {
        Map<String, BedrockRenderController> out = new LinkedHashMap<>();
        if (jsonText == null || jsonText.isBlank()) {
            return out;
        }
        JsonObject root = JsonParser.parseString(jsonText).getAsJsonObject();
        JsonObject ctrls = root.has("render_controllers") && root.get("render_controllers").isJsonObject()
            ? root.getAsJsonObject("render_controllers") : root;

        for (Map.Entry<String, JsonElement> e : ctrls.entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            BedrockRenderController rc = parseSingle(e.getKey(), e.getValue().getAsJsonObject());
            out.put(rc.identifierLower, rc);
        }
        return out;
    }

    private static BedrockRenderController parseSingle(String id, JsonObject obj) {
        BedrockRenderController rc = new BedrockRenderController(id);

        if (obj.has("arrays") && obj.get("arrays").isJsonObject()) {
            JsonObject arrays = obj.getAsJsonObject("arrays");
            parseArrays(arrays.getAsJsonObject("geometries"), rc.geometryArrays);
            parseArrays(arrays.getAsJsonObject("materials"), rc.materialArrays);
            parseArrays(arrays.getAsJsonObject("textures"), rc.textureArrays);
        }

        if (obj.has("geometry")) {
            rc.geometryExpr = MolangParser.parseJson(obj.get("geometry"));
        }

        if (obj.has("materials") && obj.get("materials").isJsonArray()) {
            for (JsonElement me : obj.getAsJsonArray("materials")) {
                if (!me.isJsonObject()) continue;
                for (Map.Entry<String, JsonElement> kv : me.getAsJsonObject().entrySet()) {
                    rc.materials.add(new BoneRule(compileGlob(kv.getKey()), MolangParser.parseJson(kv.getValue())));
                }
            }
        }

        if (obj.has("part_visibility") && obj.get("part_visibility").isJsonArray()) {
            for (JsonElement pe : obj.getAsJsonArray("part_visibility")) {
                if (!pe.isJsonObject()) continue;
                for (Map.Entry<String, JsonElement> kv : pe.getAsJsonObject().entrySet()) {
                    rc.partVisibility.add(new BoneRule(compileGlob(kv.getKey()), MolangParser.parseJson(kv.getValue())));
                }
            }
        }

        if (obj.has("textures") && obj.get("textures").isJsonArray()) {
            for (JsonElement te : obj.getAsJsonArray("textures")) {
                rc.textures.add(MolangParser.parseJson(te));
            }
        }

        rc.color = parseColor(obj.get("color"));
        rc.overlayColor = parseColor(obj.get("overlay_color"));
        rc.isHurtColor = parseColor(obj.get("is_hurt_color"));
        rc.onFireColor = parseColor(obj.get("on_fire_color"));

        if (obj.has("uv_anim") && obj.get("uv_anim").isJsonObject()) {
            JsonObject uva = obj.getAsJsonObject("uv_anim");
            if (uva.has("offset") && uva.get("offset").isJsonArray()) {
                JsonArray o = uva.getAsJsonArray("offset");
                if (o.size() > 0) rc.uvOffsetU = MolangParser.parseJson(o.get(0));
                if (o.size() > 1) rc.uvOffsetV = MolangParser.parseJson(o.get(1));
            }
            if (uva.has("scale") && uva.get("scale").isJsonArray()) {
                JsonArray s = uva.getAsJsonArray("scale");
                if (s.size() > 0) rc.uvScaleU = MolangParser.parseJson(s.get(0));
                if (s.size() > 1) rc.uvScaleV = MolangParser.parseJson(s.get(1));
            }
        }

        rc.ignoreLighting = obj.has("ignore_lighting") && obj.get("ignore_lighting").isJsonPrimitive()
            && obj.get("ignore_lighting").getAsBoolean();
        rc.filterLighting = obj.has("filter_lighting") && obj.get("filter_lighting").isJsonPrimitive()
            && obj.get("filter_lighting").getAsBoolean();
        return rc;
    }

    private static void parseArrays(JsonObject section, Map<String, List<MolangExpr>> out) {
        if (section == null) return;
        for (Map.Entry<String, JsonElement> e : section.entrySet()) {
            if (!e.getValue().isJsonArray()) continue;
            String arrKey = e.getKey().toLowerCase(Locale.ROOT);
            if (arrKey.startsWith("array.")) {
                arrKey = arrKey.substring("array.".length());
            }
            List<MolangExpr> list = new ArrayList<>();
            for (JsonElement item : e.getValue().getAsJsonArray()) {
                if (item.isJsonObject()) {
                    // Case where material array holds { "*": "Material.x" }
                    for (Map.Entry<String, JsonElement> first : item.getAsJsonObject().entrySet()) {
                        list.add(MolangParser.parseJson(first.getValue()));
                        break;
                    }
                } else {
                    list.add(MolangParser.parseJson(item));
                }
            }
            out.put(arrKey, list);
        }
    }

    private static ColorExpr parseColor(JsonElement el) {
        if (el == null || !el.isJsonObject()) return null;
        JsonObject o = el.getAsJsonObject();
        return new ColorExpr(
            o.has("r") ? MolangParser.parseJson(o.get("r")) : MolangExpr.ONE,
            o.has("g") ? MolangParser.parseJson(o.get("g")) : MolangExpr.ONE,
            o.has("b") ? MolangParser.parseJson(o.get("b")) : MolangExpr.ONE,
            o.has("a") ? MolangParser.parseJson(o.get("a")) : MolangExpr.ONE
        );
    }

    private static Pattern compileGlob(String glob) {
        String lower = glob.toLowerCase(Locale.ROOT);
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else if (c == '?') {
                regex.append('.');
            } else {
                if ("\\[]{}().+^$|".indexOf(c) >= 0) {
                    regex.append('\\');
                }
                regex.append(c);
            }
        }
        regex.append('$');
        return Pattern.compile(regex.toString());
    }

    /** Evaluates arrays into {@link MolangScope} before running geometry/texture/material expressions. */
    public void populateArrays(MolangScope scope) {
        pushArrayGroup(geometryArrays, scope);
        pushArrayGroup(materialArrays, scope);
        pushArrayGroup(textureArrays, scope);
    }

    private static void pushArrayGroup(Map<String, List<MolangExpr>> map, MolangScope scope) {
        for (Map.Entry<String, List<MolangExpr>> e : map.entrySet()) {
            List<MolangExpr> items = e.getValue();
            MolangValue[] vals = new MolangValue[items.size()];
            for (int i = 0; i < items.size(); i++) {
                vals[i] = items.get(i).evaluate(scope);
            }
            scope.defineArray(e.getKey(), vals);
        }
    }

    public String resolveGeometrySymbol(MolangScope scope) {
        MolangValue v = geometryExpr.evaluate(scope);
        return stripPrefix(v.asString(), "geometry.");
    }

    public List<String> resolveTextureSymbols(MolangScope scope) {
        List<String> out = new ArrayList<>(textures.size());
        for (MolangExpr expr : textures) {
            String s = expr.evaluate(scope).asString();
            if (!s.isEmpty() && !"0".equals(s)) {
                out.add(stripPrefix(s, "texture."));
            }
        }
        return out;
    }

    public String resolveMaterialForBone(String boneNameLower, MolangScope scope) {
        String mat = "default";
        for (BoneRule rule : materials) {
            if (rule.matches(boneNameLower)) {
                String res = rule.valueExpr.evaluate(scope).asString();
                if (!res.isEmpty() && !"0".equals(res)) {
                    mat = stripPrefix(res, "material.");
                }
            }
        }
        return mat;
    }

    public boolean isBoneVisible(String boneNameLower, MolangScope scope) {
        boolean visible = true;
        for (BoneRule rule : partVisibility) {
            if (rule.matches(boneNameLower)) {
                visible = rule.valueExpr.evaluateBoolean(scope);
            }
        }
        return visible;
    }

    private static String stripPrefix(String s, String prefix) {
        String low = s.toLowerCase(Locale.ROOT);
        if (low.startsWith(prefix)) {
            return low.substring(prefix.length());
        }
        return low;
    }

    public static void addBuiltinFallbacks(Map<String, BedrockRenderController> registry) {
        for (String id : new String[]{"controller.render.default", "controller.render.armor"}) {
            registry.computeIfAbsent(id, k -> {
                BedrockRenderController rc = new BedrockRenderController(k);
                rc.materials.add(new BoneRule(compileGlob("*"), MolangParser.parse("Material.default")));
                rc.textures.add(MolangParser.parse("Texture.default"));
                return rc;
            });
        }
    }
}
