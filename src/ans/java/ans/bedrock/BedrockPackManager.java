package ans.bedrock;

import ans.molang.MolangExpr;
import ans.molang.MolangScope;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Central registry for all parsed Bedrock definitions (geometries, animations, animation controllers,
 * render controllers, entities, attachables, particles, materials, colormaps, atmospherics, biomes, fogs)
 * loaded either from the active Minecraft {@link ResourceManager} or directly from {@code Actions-And-Stuff-Java-1.20.1.zip}.
 */
public final class BedrockPackManager {

    public record ColormapData(int width, int height, int[] argb) {
    }

    public record AuditReport(
        int entityCount,
        int attachableCount,
        int geometryCount,
        int animationCount,
        int animationControllerCount,
        int renderControllerCount,
        int particleCount,
        int materialCount,
        int atmosphericCount,
        int biomeCount,
        int fogCount,
        int waterCount,
        int lightingCount,
        int availableTextureCount,
        List<String> parseErrors,
        List<String> missingGeometries,
        List<String> missingAnimations,
        List<String> missingRenderControllers,
        List<String> missingTextures
    ) {
    }

    private static final Map<String, String> JAVA_TO_BEDROCK_ENTITY = createEntityAliases();
    private static final Map<String, String> JAVA_TO_BEDROCK_ITEM = createItemAliases();

    public final Map<String, BedrockGeometry> geometries = new LinkedHashMap<>();
    public final Map<String, BedrockAnimation> animations = new LinkedHashMap<>();
    public final Map<String, BedrockAnimationController> animationControllers = new LinkedHashMap<>();
    public final Map<String, BedrockRenderController> renderControllers = new LinkedHashMap<>();
    public final Map<String, BedrockMaterialDef> materials = new LinkedHashMap<>();
    public final Map<String, BedrockEntityDef> entities = new LinkedHashMap<>();
    public final Map<String, BedrockAttachableDef> attachablesById = new LinkedHashMap<>();
    public final Map<String, List<BedrockAttachableDef>> attachablesByItem = new LinkedHashMap<>();
    public final Map<String, BedrockParticleDef> particles = new LinkedHashMap<>();

    public final Map<String, String> atmosphericJsons = new LinkedHashMap<>();
    public final Map<String, String> biomeJsons = new LinkedHashMap<>();
    public final Map<String, String> fogJsons = new LinkedHashMap<>();
    public final Map<String, String> waterJsons = new LinkedHashMap<>();
    public final Map<String, String> lightingJsons = new LinkedHashMap<>();
    public final Map<String, String> colorGradingJsons = new LinkedHashMap<>();
    public final Map<String, ColormapData> colormaps = new LinkedHashMap<>();
    public final Set<String> availableTextures = new LinkedHashSet<>();
    public final List<String> parseErrors = new ArrayList<>();

    private boolean loaded = false;

    public synchronized void clear() {
        geometries.clear();
        animations.clear();
        animationControllers.clear();
        renderControllers.clear();
        materials.clear();
        entities.clear();
        attachablesById.clear();
        attachablesByItem.clear();
        particles.clear();
        atmosphericJsons.clear();
        biomeJsons.clear();
        fogJsons.clear();
        waterJsons.clear();
        lightingJsons.clear();
        colorGradingJsons.clear();
        colormaps.clear();
        availableTextures.clear();
        parseErrors.clear();
        loaded = false;
    }

    public boolean isLoaded() {
        return loaded && (!entities.isEmpty() || !attachablesById.isEmpty() || !biomeJsons.isEmpty());
    }

    public synchronized void loadFromResourceManager(ResourceManager rm) {
        clear();
        if (rm != null) {
            try {
                Map<Identifier, Resource> bedrockRes = rm.findResources("bedrock", id -> true);
                if (!bedrockRes.isEmpty()) {
                    for (Map.Entry<Identifier, Resource> entry : bedrockRes.entrySet()) {
                        String path = entry.getKey().getPath();
                        if (path.startsWith("bedrock/")) {
                            path = path.substring("bedrock/".length());
                        }
                        try (InputStream in = entry.getValue().getInputStream()) {
                            ingestBedrockFile(path, in.readAllBytes());
                        } catch (Exception ex) {
                            parseErrors.add(path + ": " + ex.getMessage());
                        }
                    }
                    Map<Identifier, Resource> texRes = rm.findResources("textures", id -> id.getPath().endsWith(".png"));
                    for (Map.Entry<Identifier, Resource> entry : texRes.entrySet()) {
                        String tp = entry.getKey().getPath().toLowerCase(Locale.ROOT);
                        availableTextures.add(tp);
                        if (tp.startsWith("textures/colormap/") && tp.endsWith(".png")) {
                            try (InputStream in = entry.getValue().getInputStream()) {
                                readColormap(tp, in.readAllBytes());
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    finalizeLoad();
                    return;
                }
            } catch (Exception ex) {
                parseErrors.add("ResourceManager load failed: " + ex.getMessage());
            }
        }

        // Auto-fallback to bundled/downloaded converted resource pack zip if not manually enabled in options
        Path fallback = findDefaultConvertedZip();
        if (fallback != null) {
            try {
                loadFromConvertedZip(fallback);
            } catch (IOException ex) {
                parseErrors.add("Fallback zip load failed (" + fallback + "): " + ex.getMessage());
                finalizeLoad();
            }
        } else {
            finalizeLoad();
        }
    }

    public static Path findDefaultConvertedZip() {
        Path[] candidates = new Path[]{
            Path.of("resourcepacks", "Actions-And-Stuff-Java-1.20.1.zip"),
            Path.of("run", "resourcepacks", "Actions-And-Stuff-Java-1.20.1.zip"),
            Path.of(System.getProperty("user.home", "."), "Downloads", "Actions-And-Stuff-Java-1.20.1.zip")
        };
        for (Path p : candidates) {
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
    }

    public synchronized void loadFromConvertedZip(Path zipPath) throws IOException {
        clear();
        try (ZipFile zip = new ZipFile(zipPath.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry entry = en.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("assets/minecraft/bedrock/")) {
                    String rel = name.substring("assets/minecraft/bedrock/".length());
                    try (InputStream in = zip.getInputStream(entry)) {
                        ingestBedrockFile(rel, in.readAllBytes());
                    } catch (Exception ex) {
                        parseErrors.add(rel + ": " + ex.getMessage());
                    }
                } else if (name.startsWith("assets/minecraft/textures/") && name.endsWith(".png")) {
                    String tp = name.substring("assets/minecraft/".length()).toLowerCase(Locale.ROOT);
                    availableTextures.add(tp);
                    if (tp.startsWith("textures/colormap/")) {
                        try (InputStream in = zip.getInputStream(entry)) {
                            readColormap(tp, in.readAllBytes());
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }
        finalizeLoad();
    }

    private void ingestBedrockFile(String relPath, byte[] bytes) {
        String lower = relPath.toLowerCase(Locale.ROOT);
        String text = new String(bytes, StandardCharsets.UTF_8);

        if (lower.startsWith("models/") && lower.endsWith(".json")) {
            geometries.putAll(BedrockGeometryLoader.parseFile(text));
        } else if (lower.startsWith("animations/") && lower.endsWith(".json")) {
            animations.putAll(BedrockAnimation.parseFile(text));
        } else if (lower.startsWith("animation_controllers/") && lower.endsWith(".json")) {
            animationControllers.putAll(BedrockAnimationController.parseFile(text));
        } else if (lower.startsWith("render_controllers/") && lower.endsWith(".json")) {
            renderControllers.putAll(BedrockRenderController.parseFile(text));
        } else if (lower.startsWith("materials/") && lower.endsWith(".material")) {
            BedrockMaterialDef.parseMaterialFile(text, materials);
        } else if (lower.startsWith("entity/") && lower.endsWith(".json")) {
            BedrockEntityDef def = BedrockEntityDef.parseFile(text);
            if (def != null) {
                BedrockEntityDef prev = entities.get(def.identifierLower);
                if (prev == null || BedrockEntityDef.compareVersions(def.minEngineVersion, prev.minEngineVersion) >= 0) {
                    entities.put(def.identifierLower, def);
                }
            }
        } else if (lower.startsWith("attachables/") && lower.endsWith(".json")) {
            BedrockAttachableDef def = BedrockAttachableDef.parseFile(text);
            if (def != null) {
                BedrockAttachableDef prev = attachablesById.get(def.identifierLower);
                if (prev == null || BedrockEntityDef.compareVersions(def.minEngineVersion, prev.minEngineVersion) >= 0) {
                    attachablesById.put(def.identifierLower, def);
                }
            }
        } else if (lower.startsWith("particles/") && lower.endsWith(".json")) {
            BedrockParticleDef def = BedrockParticleDef.parseFile(text);
            if (def != null) {
                particles.put(def.identifierLower, def);
                int colon = def.identifierLower.indexOf(':');
                if (colon >= 0) {
                    particles.putIfAbsent(def.identifierLower.substring(colon + 1), def);
                }
            }
        } else if (lower.startsWith("atmospherics/") && lower.endsWith(".json")) {
            atmosphericJsons.put(lower, text);
        } else if (lower.startsWith("biomes/") && lower.endsWith(".json")) {
            biomeJsons.put(lower, text);
        } else if (lower.startsWith("fogs/") && lower.endsWith(".json")) {
            fogJsons.put(lower, text);
        } else if (lower.startsWith("water/") && lower.endsWith(".json")) {
            waterJsons.put(lower, text);
        } else if (lower.startsWith("lighting/") && lower.endsWith(".json")) {
            lightingJsons.put(lower, text);
        } else if (lower.startsWith("color_grading/") && lower.endsWith(".json")) {
            colorGradingJsons.put(lower, text);
        }
    }

    private void readColormap(String texPath, byte[] pngBytes) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(pngBytes));
        if (img == null) return;
        int w = img.getWidth();
        int h = img.getHeight();
        int[] argb = img.getRGB(0, 0, w, h, null, 0, w);
        String key = texPath.substring("textures/colormap/".length());
        colormaps.put(key, new ColormapData(w, h, argb));
    }

    private void finalizeLoad() {
        BedrockGeometryLoader.addBuiltinFallbacks(geometries);
        BedrockAnimation.addBuiltinFallbacks(animations);
        BedrockAnimationController.addBuiltinFallbacks(animationControllers);
        BedrockRenderController.addBuiltinFallbacks(renderControllers);
        BedrockMaterialDef.resolveAll(materials);

        // Index attachables by item ID and itemConditions, placing conditional variants first
        attachablesByItem.clear();
        for (BedrockAttachableDef def : attachablesById.values()) {
            if (!def.itemConditions.isEmpty()) {
                for (String itemId : def.itemConditions.keySet()) {
                    attachablesByItem.computeIfAbsent(itemId, k -> new ArrayList<>()).add(def);
                }
            }
            attachablesByItem.computeIfAbsent(def.identifierLower, k -> new ArrayList<>()).add(def);
            int dot = def.identifierLower.indexOf('.');
            if (dot > 0) {
                String base = def.identifierLower.substring(0, dot);
                attachablesByItem.computeIfAbsent(base, k -> new ArrayList<>()).add(def);
            }
        }
        for (List<BedrockAttachableDef> list : attachablesByItem.values()) {
            list.sort((a, b) -> {
                int aCond = a.itemConditions.isEmpty() ? 1 : 0;
                int bCond = b.itemConditions.isEmpty() ? 1 : 0;
                return Integer.compare(aCond, bCond);
            });
        }
        loaded = true;
    }

    public BedrockEntityDef resolveEntity(String mcEntityId) {
        if (mcEntityId == null || mcEntityId.isEmpty()) return null;
        String lower = mcEntityId.toLowerCase(Locale.ROOT);
        if (!lower.contains(":")) lower = "minecraft:" + lower;
        BedrockEntityDef direct = entities.get(lower);
        if (direct != null) return direct;
        String alias = JAVA_TO_BEDROCK_ENTITY.get(lower);
        if (alias != null) {
            return entities.get(alias);
        }
        return null;
    }

    public BedrockAttachableDef resolveAttachable(String mcItemId, MolangScope scope) {
        if (mcItemId == null || mcItemId.isEmpty()) return null;
        String lower = mcItemId.toLowerCase(Locale.ROOT);
        if (!lower.contains(":")) lower = "minecraft:" + lower;

        BedrockAttachableDef found = matchAttachableList(attachablesByItem.get(lower), lower, scope);
        if (found != null) return found;

        String alias = JAVA_TO_BEDROCK_ITEM.get(lower);
        if (alias != null) {
            found = matchAttachableList(attachablesByItem.get(alias), alias, scope);
            if (found != null) return found;
        }
        if (lower.endsWith("_banner")) {
            return matchAttachableList(attachablesByItem.get("minecraft:banner"), "minecraft:banner", scope);
        }
        if (lower.endsWith("_bed")) {
            return matchAttachableList(attachablesByItem.get("minecraft:bed"), "minecraft:bed", scope);
        }
        return null;
    }

    private BedrockAttachableDef matchAttachableList(List<BedrockAttachableDef> candidates, String itemId, MolangScope scope) {
        if (candidates == null || candidates.isEmpty()) return null;
        for (BedrockAttachableDef def : candidates) {
            MolangExpr cond = def.itemConditions.get(itemId);
            if (cond != null) {
                if (scope == null || cond.evaluateBoolean(scope)) {
                    return def;
                }
            }
        }
        for (BedrockAttachableDef def : candidates) {
            if (def.itemConditions.isEmpty() && def.identifierLower.equals(itemId)) {
                return def;
            }
        }
        for (BedrockAttachableDef def : candidates) {
            if (def.itemConditions.isEmpty()) {
                return def;
            }
        }
        return candidates.get(0);
    }

    public BedrockMaterialDef getMaterial(String materialIdLower) {
        if (materialIdLower == null) return materials.get("entity");
        BedrockMaterialDef mat = materials.get(materialIdLower.toLowerCase(Locale.ROOT));
        return mat != null ? mat : materials.getOrDefault("entity_alphatest", materials.get("entity"));
    }

    public String resolveTexturePath(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) return "";
        String p = rawPath.replace('\\', '/').toLowerCase(Locale.ROOT).trim();
        if (p.startsWith("minecraft:")) {
            p = p.substring("minecraft:".length());
        }
        while (p.startsWith("../")) {
            p = p.substring(3);
        }
        if ("atlas.terrain".equals(p)) {
            return "textures/particle/particles.png";
        }
        if (p.endsWith(".tga") || p.endsWith(".png")) {
            p = p.substring(0, p.length() - 4);
        }
        if (!p.startsWith("textures/")) {
            p = "textures/" + p;
        }
        String png = p + ".png";
        if (availableTextures.contains(png)) {
            return png;
        }
        // Check alternate paths sometimes used between Bedrock and flattened Java
        String alt = png.replace("textures/entity/villager2/", "textures/entity/villager/")
            .replace("textures/entity/zombie_villager2/", "textures/entity/zombie_villager/")
            .replace("textures/entity/horse2/", "textures/entity/horse/")
            .replace("textures/entity/banner/", "textures/entity/banner_base/");
        if (availableTextures.contains(alt)) {
            return alt;
        }
        return png;
    }

    public boolean hasTexture(String resolvedPngPath) {
        return availableTextures.contains(resolvedPngPath);
    }

    public AuditReport auditIntegrity() {
        Set<String> missingGeos = new LinkedHashSet<>();
        Set<String> missingAnims = new LinkedHashSet<>();
        Set<String> missingRcs = new LinkedHashSet<>();
        Set<String> missingTex = new LinkedHashSet<>();

        for (BedrockEntityDef ent : entities.values()) {
            for (String g : ent.geometry.values()) {
                if (!geometries.containsKey(g)) missingGeos.add(g);
            }
            for (String a : ent.animations.values()) {
                if (!animations.containsKey(a) && !animationControllers.containsKey(a)) {
                    missingAnims.add(a);
                }
            }
            for (BedrockEntityDef.RenderControllerRef rc : ent.renderControllers) {
                if (!renderControllers.containsKey(rc.controllerLower())) {
                    missingRcs.add(rc.controllerLower());
                }
            }
            for (String t : ent.textures.values()) {
                String res = resolveTexturePath(t);
                if (!res.isEmpty() && !availableTextures.contains(res)) {
                    missingTex.add(ent.identifier + " -> " + t + " (" + res + ")");
                }
            }
        }

        for (BedrockAttachableDef att : attachablesById.values()) {
            for (String g : att.geometry.values()) {
                if (!geometries.containsKey(g)) missingGeos.add(g);
            }
            for (String a : att.animations.values()) {
                if (!animations.containsKey(a) && !animationControllers.containsKey(a)) {
                    missingAnims.add(a);
                }
            }
            for (BedrockEntityDef.RenderControllerRef rc : att.renderControllers) {
                if (!renderControllers.containsKey(rc.controllerLower())) {
                    missingRcs.add(rc.controllerLower());
                }
            }
            for (String t : att.textures.values()) {
                String res = resolveTexturePath(t);
                if (!res.isEmpty() && !availableTextures.contains(res)) {
                    missingTex.add(att.identifier + " -> " + t + " (" + res + ")");
                }
            }
        }

        for (BedrockParticleDef p : particles.values()) {
            if (!p.texturePath.isEmpty()) {
                String res = resolveTexturePath(p.texturePath);
                if (!availableTextures.contains(res)) {
                    missingTex.add(p.identifier + " -> " + p.texturePath + " (" + res + ")");
                }
            }
        }

        return new AuditReport(
            entities.size(),
            attachablesById.size(),
            geometries.size(),
            animations.size(),
            animationControllers.size(),
            renderControllers.size(),
            particles.size(),
            materials.size(),
            atmosphericJsons.size(),
            biomeJsons.size(),
            fogJsons.size(),
            waterJsons.size(),
            lightingJsons.size(),
            availableTextures.size(),
            new ArrayList<>(parseErrors),
            new ArrayList<>(missingGeos),
            new ArrayList<>(missingAnims),
            new ArrayList<>(missingRcs),
            new ArrayList<>(missingTex)
        );
    }

    private static Map<String, String> createEntityAliases() {
        Map<String, String> m = new HashMap<>();
        m.put("minecraft:end_crystal", "minecraft:ender_crystal");
        m.put("minecraft:evoker_fangs", "minecraft:evocation_fang");
        m.put("minecraft:evoker", "minecraft:evocation_illager");
        m.put("minecraft:eye_of_ender", "minecraft:eye_of_ender_signal");
        m.put("minecraft:firework_rocket", "minecraft:fireworks_rocket");
        m.put("minecraft:fishing_bobber", "minecraft:fishing_hook");
        m.put("minecraft:potion", "minecraft:splash_potion");
        m.put("minecraft:trident", "minecraft:thrown_trident");
        m.put("minecraft:tropical_fish", "minecraft:tropicalfish");
        m.put("minecraft:villager", "minecraft:villager_v2");
        m.put("minecraft:experience_bottle", "minecraft:xp_bottle");
        m.put("minecraft:experience_orb", "minecraft:xp_orb");
        m.put("minecraft:zombified_piglin", "minecraft:zombie_pigman");
        m.put("minecraft:zombie_villager", "minecraft:zombie_villager_v2");
        m.put("minecraft:spectral_arrow", "minecraft:arrow");
        m.put("minecraft:furnace_minecart", "minecraft:minecart");
        m.put("minecraft:spawner_minecart", "minecraft:minecart");
        return Collections.unmodifiableMap(m);
    }

    private static Map<String, String> createItemAliases() {
        Map<String, String> m = new HashMap<>();
        m.put("minecraft:experience_bottle", "minecraft:xp_bottle");
        m.put("minecraft:firework_rocket", "minecraft:fireworks_rocket");
        m.put("minecraft:zombified_piglin_spawn_egg", "minecraft:zombie_pigman_spawn_egg");
        m.put("minecraft:mooshroom_spawn_egg", "minecraft:mooshroom_spawn_egg");
        m.put("minecraft:music_disc_otherside", "minecraft:music_disc_otherside");
        m.put("minecraft:scute", "minecraft:turtle_scute");
        return Collections.unmodifiableMap(m);
    }
}
