package ans.bedrock;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Parses Bedrock {@code materials/*.material} files with inheritance resolution
 * to classify render pass modes (alpha test, alpha blend, additive, emissive, glint, culling, UV anim).
 */
public final class BedrockMaterialDef {

    public enum RenderMode {
        OPAQUE,
        ALPHA_TEST,
        ALPHA_BLEND,
        ADDITIVE,
        EMISSIVE,
        EMISSIVE_ALPHA,
        GLINT
    }

    public final String name;
    public final String nameLower;
    public final String parentLower;
    public final Set<String> defines = new HashSet<>();
    public final Set<String> removedDefines = new HashSet<>();
    public final Set<String> states = new HashSet<>();
    public final Set<String> removedStates = new HashSet<>();
    public String blendSrc = "";
    public String blendDst = "";
    public String depthFunc = "";

    private boolean resolved;
    private boolean alphaTest;
    private boolean alphaBlend;
    private boolean additive;
    private boolean disableCulling;
    private boolean disableDepthWrite;
    private boolean disableColorWrite;
    private boolean useUvAnim;
    private boolean emissive;
    private boolean enchanted;
    private boolean useColorMask;
    private boolean useOverlay;

    public BedrockMaterialDef(String name, String parentLower) {
        this.name = name;
        this.nameLower = name.toLowerCase(Locale.ROOT);
        this.parentLower = parentLower == null ? "" : parentLower.toLowerCase(Locale.ROOT);
    }

    public static void parseMaterialFile(String text, Map<String, BedrockMaterialDef> out) {
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            JsonReader reader = new JsonReader(new StringReader(text));
            reader.setLenient(true);
            JsonElement rootEl = JsonParser.parseReader(reader);
            if (!rootEl.isJsonObject()) return;
            JsonObject root = rootEl.getAsJsonObject();
            JsonObject mats = root.has("materials") && root.get("materials").isJsonObject()
                ? root.getAsJsonObject("materials") : root;

            Map<String, JsonObject> rawDefs = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : mats.entrySet()) {
                String key = e.getKey();
                if ("version".equals(key) || !e.getValue().isJsonObject()) continue;
                int colon = key.indexOf(':');
                String name = colon >= 0 ? key.substring(0, colon) : key;
                String parent = colon >= 0 ? key.substring(colon + 1) : "";
                String nameLower = name.toLowerCase(Locale.ROOT);
                BedrockMaterialDef def = out.computeIfAbsent(nameLower, k -> new BedrockMaterialDef(name, parent));
                if (!parent.isEmpty() && def.parentLower.isEmpty()) {
                    def = new BedrockMaterialDef(name, parent);
                    out.put(nameLower, def);
                }
                rawDefs.put(nameLower, e.getValue().getAsJsonObject());
            }

            for (Map.Entry<String, JsonObject> e : rawDefs.entrySet()) {
                BedrockMaterialDef def = out.get(e.getKey());
                JsonObject obj = e.getValue();
                applyRawObject(def, obj);
            }
        } catch (Exception ignored) {
        }
    }

    private static void applyRawObject(BedrockMaterialDef def, JsonObject obj) {
        readArray(obj, "+defines", def.defines, def.removedDefines, true);
        readArray(obj, "-defines", def.defines, def.removedDefines, false);
        readArray(obj, "defines", def.defines, def.removedDefines, true);
        readArray(obj, "+states", def.states, def.removedStates, true);
        readArray(obj, "-states", def.states, def.removedStates, false);
        readArray(obj, "states", def.states, def.removedStates, true);
        if (obj.has("blendSrc") && obj.get("blendSrc").isJsonPrimitive()) {
            def.blendSrc = obj.get("blendSrc").getAsString();
        }
        if (obj.has("blendDst") && obj.get("blendDst").isJsonPrimitive()) {
            def.blendDst = obj.get("blendDst").getAsString();
        }
        if (obj.has("depthFunc") && obj.get("depthFunc").isJsonPrimitive()) {
            def.depthFunc = obj.get("depthFunc").getAsString();
        }
    }

    private static void readArray(JsonObject obj, String key, Set<String> addSet, Set<String> removeSet, boolean add) {
        if (!obj.has(key) || !obj.get(key).isJsonArray()) return;
        for (JsonElement el : obj.getAsJsonArray(key)) {
            if (el.isJsonPrimitive()) {
                String v = el.getAsString().trim();
                if (add) {
                    addSet.add(v);
                    removeSet.remove(v);
                } else {
                    addSet.remove(v);
                    removeSet.add(v);
                }
            }
        }
    }

    public static void resolveAll(Map<String, BedrockMaterialDef> mats) {
        addBuiltinFallbacks(mats);
        Set<String> visiting = new HashSet<>();
        for (BedrockMaterialDef def : mats.values()) {
            resolveChain(def, mats, visiting);
        }
    }

    private static void resolveChain(BedrockMaterialDef def, Map<String, BedrockMaterialDef> mats, Set<String> visiting) {
        if (def.resolved || !visiting.add(def.nameLower)) {
            return;
        }
        Set<String> effDefines = new HashSet<>();
        Set<String> effStates = new HashSet<>();
        String effBlendSrc = def.blendSrc;
        String effBlendDst = def.blendDst;
        boolean ancestorEmissive = false;
        boolean ancestorAlphaBlend = false;
        boolean ancestorAlphaTest = false;
        boolean ancestorAdditive = false;
        boolean ancestorGlint = false;
        boolean ancestorDisableCulling = false;
        boolean ancestorDisableColorWrite = false;

        if (!def.parentLower.isEmpty()) {
            BedrockMaterialDef parent = mats.get(def.parentLower);
            if (parent != null) {
                resolveChain(parent, mats, visiting);
                effDefines.addAll(parent.defines);
                effStates.addAll(parent.states);
                if (effBlendSrc.isEmpty()) effBlendSrc = parent.blendSrc;
                if (effBlendDst.isEmpty()) effBlendDst = parent.blendDst;
                ancestorEmissive = parent.emissive;
                ancestorAlphaBlend = parent.alphaBlend;
                ancestorAlphaTest = parent.alphaTest;
                ancestorAdditive = parent.additive;
                ancestorGlint = parent.enchanted;
                ancestorDisableCulling = parent.disableCulling;
                ancestorDisableColorWrite = parent.disableColorWrite;
            } else {
                ancestorEmissive = isNameEmissive(def.parentLower);
                ancestorAlphaBlend = isNameAlphaBlend(def.parentLower);
                ancestorAlphaTest = isNameAlphaTest(def.parentLower);
                ancestorAdditive = isNameAdditive(def.parentLower);
                ancestorGlint = isNameGlint(def.parentLower);
                ancestorDisableCulling = isNameDisableCulling(def.parentLower);
            }
        }

        effDefines.addAll(def.defines);
        effDefines.removeAll(def.removedDefines);
        effStates.addAll(def.states);
        effStates.removeAll(def.removedStates);
        def.defines.clear();
        def.defines.addAll(effDefines);
        def.states.clear();
        def.states.addAll(effStates);
        def.blendSrc = effBlendSrc;
        def.blendDst = effBlendDst;

        def.alphaTest = ancestorAlphaTest || effDefines.contains("ALPHA_TEST") || isNameAlphaTest(def.nameLower);
        def.alphaBlend = ancestorAlphaBlend || effStates.contains("Blending") || !effBlendDst.isEmpty() || isNameAlphaBlend(def.nameLower);
        def.additive = ancestorAdditive || "One".equalsIgnoreCase(effBlendDst) || isNameAdditive(def.nameLower);
        def.disableCulling = (ancestorDisableCulling && !def.removedStates.contains("DisableCulling"))
            || effStates.contains("DisableCulling") || isNameDisableCulling(def.nameLower);
        def.disableDepthWrite = effStates.contains("DisableDepthWrite");
        def.disableColorWrite = (ancestorDisableColorWrite && !def.removedStates.contains("DisableColorWrite"))
            || effStates.contains("DisableColorWrite");
        def.useUvAnim = effDefines.contains("USE_UV_ANIM") || isNameUvAnim(def.nameLower);
        def.emissive = ancestorEmissive || effDefines.contains("USE_EMISSIVE") || effDefines.contains("EMISSIVE")
            || isNameEmissive(def.nameLower);
        def.enchanted = ancestorGlint || effDefines.contains("GLINT") || isNameGlint(def.nameLower);
        def.useColorMask = effDefines.contains("USE_COLOR_MASK");
        def.useOverlay = effDefines.contains("USE_OVERLAY");
        def.resolved = true;
        visiting.remove(def.nameLower);
    }

    public static void addBuiltinFallbacks(Map<String, BedrockMaterialDef> mats) {
        ensureBuiltin(mats, "entity", "");
        ensureBuiltin(mats, "entity_nocull", "entity", Set.of("DisableCulling"), Set.of());
        ensureBuiltin(mats, "entity_alphatest", "entity", Set.of("DisableCulling"), Set.of("ALPHA_TEST"));
        ensureBuiltin(mats, "entity_alphatest_one_sided", "entity", Set.of(), Set.of("ALPHA_TEST"));
        ensureBuiltin(mats, "entity_alphablend", "entity", Set.of("Blending", "DisableCulling"), Set.of());
        ensureBuiltin(mats, "entity_emissive", "entity", Set.of(), Set.of("USE_EMISSIVE"));
        ensureBuiltin(mats, "entity_emissive_alpha", "entity_alphatest", Set.of("DisableCulling"), Set.of("ALPHA_TEST", "USE_EMISSIVE"));
        ensureBuiltin(mats, "entity_emissive_alpha_one_sided", "entity_alphatest_one_sided", Set.of(), Set.of("ALPHA_TEST", "USE_EMISSIVE"));
        ensureBuiltin(mats, "entity_glint", "entity");
        ensureBuiltin(mats, "armor_enchanted", "entity");
        ensureBuiltin(mats, "entity_additive", "entity_alphablend");
        ensureBuiltin(mats, "charged_creeper", "entity_additive");
        ensureBuiltin(mats, "warden", "entity_emissive_alpha");
        ensureBuiltin(mats, "breeze_wind", "entity_alphablend");
        ensureBuiltin(mats, "enderman", "entity_emissive_alpha");
        ensureBuiltin(mats, "spider", "entity_emissive_alpha");
        ensureBuiltin(mats, "phantom", "entity_emissive_alpha");
        ensureBuiltin(mats, "blaze_body", "entity_emissive");
        ensureBuiltin(mats, "armor", "entity_alphatest");
        ensureBuiltin(mats, "armor_stand", "entity_alphatest");
    }

    private static void ensureBuiltin(Map<String, BedrockMaterialDef> mats, String name, String parent) {
        mats.computeIfAbsent(name, k -> new BedrockMaterialDef(name, parent));
    }

    private static void ensureBuiltin(Map<String, BedrockMaterialDef> mats,
                                      String name,
                                      String parent,
                                      Set<String> defaultStates,
                                      Set<String> defaultDefines) {
        BedrockMaterialDef def = mats.computeIfAbsent(name, k -> new BedrockMaterialDef(name, parent));
        for (String st : defaultStates) {
            if (!def.removedStates.contains(st)) {
                def.states.add(st);
            }
        }
        for (String df : defaultDefines) {
            if (!def.removedDefines.contains(df)) {
                def.defines.add(df);
            }
        }
    }

    private static boolean isNameAlphaTest(String n) {
        return n.contains("alphatest") || n.equals("armor") || n.equals("armor_stand");
    }

    private static boolean isNameAlphaBlend(String n) {
        return n.contains("alphablend") || n.contains("breeze_wind") || n.contains("slime_outer");
    }

    private static boolean isNameAdditive(String n) {
        return n.contains("additive") || n.contains("charged_creeper");
    }

    private static boolean isNameDisableCulling(String n) {
        return n.contains("nocull") || n.contains("alphatest") || n.contains("alphablend")
            ? !n.contains("one_sided") : false;
    }

    private static boolean isNameUvAnim(String n) {
        return n.contains("glint") || n.contains("enchanted") || n.contains("charged_creeper") || n.contains("breeze_wind");
    }

    private static boolean isNameEmissive(String n) {
        return n.contains("emissive") || n.contains("warden") || n.contains("enderman")
            || n.contains("spider") || n.contains("phantom") || n.contains("blaze")
            || n.contains("drowned") || n.contains("glow_squid") || n.contains("breeze_eyes");
    }

    private static boolean isNameGlint(String n) {
        return n.contains("glint") || n.contains("enchanted");
    }

    public RenderMode getRenderMode() {
        if (enchanted && !additive) return RenderMode.GLINT;
        if (additive) return RenderMode.ADDITIVE;
        if (emissive && (alphaTest || alphaBlend)) return RenderMode.EMISSIVE_ALPHA;
        if (emissive) return RenderMode.EMISSIVE;
        if (alphaBlend) return RenderMode.ALPHA_BLEND;
        if (alphaTest) return RenderMode.ALPHA_TEST;
        return RenderMode.OPAQUE;
    }

    public boolean isAlphaTest() { return alphaTest; }
    public boolean isAlphaBlend() { return alphaBlend; }
    public boolean isAdditive() { return additive; }
    public boolean isDisableCulling() { return disableCulling; }
    public boolean isDisableDepthWrite() { return disableDepthWrite; }
    public boolean isColorWriteDisabled() { return disableColorWrite; }
    public boolean isUseUvAnim() { return useUvAnim; }
    public boolean isEmissive() { return emissive; }
    public boolean isEnchanted() { return enchanted; }
    public boolean isUseColorMask() { return useColorMask; }
    public boolean isUseOverlay() { return useOverlay; }
}
