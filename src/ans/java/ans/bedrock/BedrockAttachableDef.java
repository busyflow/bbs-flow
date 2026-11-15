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
 * Parses Bedrock {@code attachables/*.json} ({@code minecraft:attachable}) definitions
 * used for 3D dynamic items, tools, shields, totems, bows, crossbows, tridents, and armor.
 */
public final class BedrockAttachableDef {

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
    public final List<MolangExpr> parentSetup = new ArrayList<>();
    public final List<BedrockEntityDef.ScriptAnimate> animate = new ArrayList<>();
    public final List<BedrockEntityDef.RenderControllerRef> renderControllers = new ArrayList<>();
    public final Map<String, MolangExpr> itemConditions = new LinkedHashMap<>();
    public MolangExpr scaleExpr = MolangExpr.ONE;
    public int[] minEngineVersion = new int[0];

    public BedrockAttachableDef(String identifier) {
        this.identifier = identifier;
        this.identifierLower = identifier.toLowerCase(Locale.ROOT);
    }

    public static BedrockAttachableDef parseFile(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            return null;
        }
        JsonObject root = JsonParser.parseString(jsonText).getAsJsonObject();
        JsonObject att = root.has("minecraft:attachable") && root.get("minecraft:attachable").isJsonObject()
            ? root.getAsJsonObject("minecraft:attachable") : null;
        if (att == null) {
            return null;
        }
        JsonObject desc = att.has("description") && att.get("description").isJsonObject()
            ? att.getAsJsonObject("description") : null;
        if (desc == null || !desc.has("identifier")) {
            return null;
        }

        BedrockAttachableDef def = new BedrockAttachableDef(desc.get("identifier").getAsString());
        def.minEngineVersion = BedrockEntityDef.parseVersion(desc.get("min_engine_version"));
        if (desc.has("item")) {
            JsonElement itemEl = desc.get("item");
            if (itemEl.isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : itemEl.getAsJsonObject().entrySet()) {
                    def.itemConditions.put(e.getKey().toLowerCase(Locale.ROOT), MolangParser.parseJson(e.getValue()));
                }
            }
        }
        BedrockEntityDef.readStringMap(desc.getAsJsonObject("materials"), def.materials);
        BedrockEntityDef.readStringMap(desc.getAsJsonObject("textures"), def.textures);
        BedrockEntityDef.readStringMap(desc.getAsJsonObject("geometry"), def.geometry);
        BedrockEntityDef.readStringMap(desc.getAsJsonObject("animations"), def.animations);
        BedrockEntityDef.readStringMap(desc.getAsJsonObject("particle_effects"), def.particleEffects);
        BedrockEntityDef.readStringMap(desc.getAsJsonObject("sound_effects"), def.soundEffects);

        if (desc.has("scripts") && desc.get("scripts").isJsonObject()) {
            JsonObject scripts = desc.getAsJsonObject("scripts");
            if (scripts.has("scale")) {
                def.scaleExpr = MolangParser.parseJson(scripts.get("scale"));
            }
            BedrockEntityDef.parseExprList(scripts.get("initialize"), def.initialize);
            BedrockEntityDef.parseExprList(scripts.get("pre_animation"), def.preAnimation);
            BedrockEntityDef.parseExprList(scripts.get("parent_setup"), def.parentSetup);
            if (scripts.has("animate") && scripts.get("animate").isJsonArray()) {
                for (JsonElement ae : scripts.getAsJsonArray("animate")) {
                    if (ae.isJsonPrimitive()) {
                        def.animate.add(new BedrockEntityDef.ScriptAnimate(ae.getAsString().toLowerCase(Locale.ROOT), MolangExpr.ONE));
                    } else if (ae.isJsonObject()) {
                        for (Map.Entry<String, JsonElement> me : ae.getAsJsonObject().entrySet()) {
                            def.animate.add(new BedrockEntityDef.ScriptAnimate(me.getKey().toLowerCase(Locale.ROOT), MolangParser.parseJson(me.getValue())));
                        }
                    }
                }
            }
        }

        if (desc.has("render_controllers") && desc.get("render_controllers").isJsonArray()) {
            for (JsonElement rce : desc.getAsJsonArray("render_controllers")) {
                if (rce.isJsonPrimitive()) {
                    def.renderControllers.add(new BedrockEntityDef.RenderControllerRef(rce.getAsString().toLowerCase(Locale.ROOT), MolangExpr.ONE));
                } else if (rce.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> me : rce.getAsJsonObject().entrySet()) {
                        def.renderControllers.add(new BedrockEntityDef.RenderControllerRef(me.getKey().toLowerCase(Locale.ROOT), MolangParser.parseJson(me.getValue())));
                    }
                }
            }
        }
        return def;
    }
}
