package ans.bedrock;

import ans.molang.MolangExpr;
import ans.molang.MolangParser;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses Bedrock {@code entity/*.json} ({@code minecraft:client_entity}) definitions.
 */
public final class BedrockEntityDef {

    public record ScriptAnimate(String shortNameLower, MolangExpr condition) {
    }

    public record RenderControllerRef(String controllerLower, MolangExpr condition) {
    }

    public final String identifier;
    public final String identifierLower;
    public final Map<String, String> materials = new LinkedHashMap<>();
    public final Map<String, String> textures = new LinkedHashMap<>();
    public final Map<String, String> geometry = new LinkedHashMap<>();
    public final Map<String, String> animations = new LinkedHashMap<>();
    public final Map<String, String> particleEffects = new LinkedHashMap<>();
    public final Map<String, String> soundEffects = new LinkedHashMap<>();
    public final List<MolangExpr> initialize = new ArrayList<>();
    public final List<MolangExpr> preAnimation = new ArrayList<>();
    public final List<ScriptAnimate> animate = new ArrayList<>();
    public final List<RenderControllerRef> renderControllers = new ArrayList<>();
    public MolangExpr scaleExpr = MolangExpr.ONE;
    public boolean enableAttachables = true;
    public boolean hideArmor = false;
    public float heldItemScale = 1f;
    public int[] minEngineVersion = new int[0];

    public BedrockEntityDef(String identifier) {
        this.identifier = identifier;
        this.identifierLower = identifier.toLowerCase(Locale.ROOT);
    }

    public static BedrockEntityDef parseFile(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            return null;
        }
        JsonObject root = JsonParser.parseString(jsonText).getAsJsonObject();
        JsonObject clientEntity = root.has("minecraft:client_entity") && root.get("minecraft:client_entity").isJsonObject()
            ? root.getAsJsonObject("minecraft:client_entity") : null;
        if (clientEntity == null) {
            return null;
        }
        JsonObject desc = clientEntity.has("description") && clientEntity.get("description").isJsonObject()
            ? clientEntity.getAsJsonObject("description") : null;
        if (desc == null || !desc.has("identifier")) {
            return null;
        }

        BedrockEntityDef def = new BedrockEntityDef(desc.get("identifier").getAsString());
        readStringMap(desc.getAsJsonObject("materials"), def.materials);
        readStringMap(desc.getAsJsonObject("textures"), def.textures);
        readStringMap(desc.getAsJsonObject("geometry"), def.geometry);
        readStringMap(desc.getAsJsonObject("animations"), def.animations);
        readStringMap(desc.getAsJsonObject("particle_effects"), def.particleEffects);
        readStringMap(desc.getAsJsonObject("sound_effects"), def.soundEffects);

        def.enableAttachables = !desc.has("enable_attachables") || desc.get("enable_attachables").getAsBoolean();
        def.hideArmor = desc.has("hide_armor") && desc.get("hide_armor").getAsBoolean();
        if (desc.has("held_item_scale") && desc.get("held_item_scale").isJsonPrimitive()) {
            try {
                def.heldItemScale = desc.get("held_item_scale").getAsFloat();
            } catch (Exception ignored) {
            }
        }
        def.minEngineVersion = parseVersion(desc.get("min_engine_version"));

        if (desc.has("scripts") && desc.get("scripts").isJsonObject()) {
            JsonObject scripts = desc.getAsJsonObject("scripts");
            if (scripts.has("scale")) {
                def.scaleExpr = MolangParser.parseJson(scripts.get("scale"));
            }
            parseExprList(scripts.get("initialize"), def.initialize);
            parseExprList(scripts.get("pre_animation"), def.preAnimation);
            if (scripts.has("animate") && scripts.get("animate").isJsonArray()) {
                for (JsonElement ae : scripts.getAsJsonArray("animate")) {
                    if (ae.isJsonPrimitive()) {
                        def.animate.add(new ScriptAnimate(ae.getAsString().toLowerCase(Locale.ROOT), MolangExpr.ONE));
                    } else if (ae.isJsonObject()) {
                        for (Map.Entry<String, JsonElement> me : ae.getAsJsonObject().entrySet()) {
                            def.animate.add(new ScriptAnimate(me.getKey().toLowerCase(Locale.ROOT), MolangParser.parseJson(me.getValue())));
                        }
                    }
                }
            }
        }

        if (desc.has("render_controllers") && desc.get("render_controllers").isJsonArray()) {
            for (JsonElement rce : desc.getAsJsonArray("render_controllers")) {
                if (rce.isJsonPrimitive()) {
                    def.renderControllers.add(new RenderControllerRef(rce.getAsString().toLowerCase(Locale.ROOT), MolangExpr.ONE));
                } else if (rce.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> me : rce.getAsJsonObject().entrySet()) {
                        def.renderControllers.add(new RenderControllerRef(me.getKey().toLowerCase(Locale.ROOT), MolangParser.parseJson(me.getValue())));
                    }
                }
            }
        }
        return def;
    }

    static void readStringMap(JsonObject obj, Map<String, String> out) {
        if (obj == null) return;
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            if (e.getValue().isJsonPrimitive()) {
                out.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue().getAsString().toLowerCase(Locale.ROOT));
            }
        }
    }

    static void parseExprList(JsonElement el, List<MolangExpr> out) {
        if (el == null || el.isJsonNull()) return;
        if (el.isJsonPrimitive()) {
            out.add(MolangParser.parseJson(el));
        } else if (el.isJsonArray()) {
            for (JsonElement item : el.getAsJsonArray()) {
                out.add(MolangParser.parseJson(item));
            }
        }
    }

    public static int[] parseVersion(JsonElement el) {
        if (el == null || el.isJsonNull()) return new int[0];
        if (el.isJsonArray()) {
            var arr = el.getAsJsonArray();
            int[] out = new int[arr.size()];
            for (int i = 0; i < arr.size(); i++) {
                try { out[i] = arr.get(i).getAsInt(); } catch (Exception ignored) {}
            }
            return out;
        }
        if (el.isJsonPrimitive()) {
            String[] parts = el.getAsString().split("\\.");
            int[] out = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                try { out[i] = Integer.parseInt(parts[i].trim()); } catch (Exception ignored) {}
            }
            return out;
        }
        return new int[0];
    }

    public static int compareVersions(int[] a, int[] b) {
        int n = Math.max(a != null ? a.length : 0, b != null ? b.length : 0);
        for (int i = 0; i < n; i++) {
            int va = (a != null && i < a.length) ? a[i] : 0;
            int vb = (b != null && i < b.length) ? b[i] : 0;
            if (va != vb) return Integer.compare(va, vb);
        }
        return 0;
    }
}
