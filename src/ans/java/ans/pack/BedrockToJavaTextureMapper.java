package ans.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/**
 * Maps Bedrock Actions &amp; Stuff SP2+root textures to Java 1.20.1 resource pack paths,
 * converts all {@code .tga} files to {@code .png}, normalizes Bedrock cutout/grass alphas,
 * slices compass/clock/painting/particle atlases, generates custom spawn egg item models,
 * and supplies fallbacks for the 4 vanilla-referenced Bedrock textures omitted from the pack.
 */
public final class BedrockToJavaTextureMapper {

    private BedrockToJavaTextureMapper() {
    }

    public record TextureReport(
        Map<String, byte[]> files,
        int totalSourceTextures,
        int convertedTgaCount,
        int pngCount,
        int generatedMcmetaCount,
        int slicedAtlasSprites,
        int spawnEggModelsGenerated,
        int mappedVanillaBlocks,
        int totalVanillaBlocks,
        int mappedVanillaItems,
        int totalVanillaItems,
        List<String> unmappedVanillaBlocks,
        List<String> unmappedVanillaItems,
        List<String> decodeErrors
    ) {
    }

    private static final Map<String, String> JAVA_TO_BEDROCK_BLOCK = createBlockAliasMap();
    private static final Map<String, String> JAVA_TO_BEDROCK_ITEM = createItemAliasMap();
    private static final Map<String, String> BEDROCK_TO_JAVA_ENTITY = createEntityAliasMap();

    public static TextureReport convertAll(ZipFile mcpack, ZipFile vanillaJar, FlipbookMcmetaGenerator flipbooks) throws IOException {
        Map<String, ZipEntry> packEntries = collectPackImageEntries(mcpack);
        Map<String, String> terrainVars = loadTerrainTextureMap(mcpack);
        Map<String, byte[]> vanillaMcmetas = collectVanillaMcmetas(vanillaJar);
        Set<String> vanillaBlocks = collectVanillaPngBasenames(vanillaJar, "assets/minecraft/textures/block/");
        Set<String> vanillaItems = collectVanillaPngBasenames(vanillaJar, "assets/minecraft/textures/item/");
        Set<String> vanillaSpawnEggs = collectVanillaSpawnEggNames(vanillaJar);

        Map<String, byte[]> outFiles = new LinkedHashMap<>();
        // Store decoded PNG bytes by canonical bedrock path (without extension), e.g. "textures/blocks/stone"
        Map<String, DecodedAsset> decodedByBedrockKey = new HashMap<>();
        List<String> decodeErrors = new ArrayList<>();
        int tgaCount = 0;
        int pngCount = 0;

        for (Map.Entry<String, ZipEntry> entry : packEntries.entrySet()) {
            String rel = entry.getKey(); // e.g. textures/blocks/stone.png or .tga
            String lower = rel.toLowerCase(Locale.ROOT);
            boolean isTga = lower.endsWith(".tga");
            String noExt = stripExt(lower);
            byte[] raw = readAll(mcpack, entry.getValue());

            byte[] pngBytes;
            int width = 0;
            int height = 0;
            try {
                if (isTga) {
                    tgaCount++;
                    BufferedImage img = TgaDecoder.decode(raw);
                    TgaDecoder.AlphaMode mode = selectTgaAlphaMode(noExt);
                    img = TgaDecoder.applyAlphaMode(img, mode);
                    width = img.getWidth();
                    height = img.getHeight();
                    pngBytes = TgaDecoder.toPngBytes(img);
                } else {
                    pngCount++;
                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(raw));
                    if (img == null) {
                        throw new IOException("ImageIO returned null");
                    }
                    width = img.getWidth();
                    height = img.getHeight();
                    // Preserve original PNG bytes unless foliage cutout cleanup is needed
                    if (needsCutoutFloor(noExt)) {
                        BufferedImage fixed = TgaDecoder.applyAlphaMode(img, TgaDecoder.AlphaMode.CUTOUT_ALPHA_FLOOR);
                        pngBytes = TgaDecoder.toPngBytes(fixed);
                    } else {
                        pngBytes = raw;
                    }
                }
            } catch (Exception ex) {
                decodeErrors.add(rel + ": " + ex.getMessage());
                continue;
            }

            boolean blank1x1 = (width <= 1 && height <= 1);
            // If both .png and .tga exist for same stem, prefer non-1x1 or SP2 (already resolved in map ordering, plus check)
            DecodedAsset prev = decodedByBedrockKey.get(noExt);
            if (prev == null || (prev.blank1x1 && !blank1x1) || !isTga) {
                decodedByBedrockKey.put(noExt, new DecodedAsset(noExt, pngBytes, width, height, blank1x1));
            }

            // Always preserve exact Bedrock texture path (normalized to .png) in Java assets/minecraft/textures/...
            outFiles.put("assets/minecraft/" + noExt + ".png", pngBytes);
        }

        // Write normalized variant keys from terrain_texture.json (e.g. diamond_ore -> textures/blocks/diamond_ore_0)
        Map<String, String> bedrockBlockByShortName = new HashMap<>();
        Map<String, String> bedrockItemByShortName = new HashMap<>();
        for (String key : decodedByBedrockKey.keySet()) {
            if (key.startsWith("textures/blocks/")) {
                String sub = key.substring("textures/blocks/".length());
                bedrockBlockByShortName.put(sub, key);
                int slash = sub.lastIndexOf('/');
                if (slash >= 0) {
                    bedrockBlockByShortName.putIfAbsent(sub.substring(slash + 1), key);
                }
            } else if (key.startsWith("textures/items/")) {
                String sub = key.substring("textures/items/".length());
                bedrockItemByShortName.put(sub, key);
                int slash = sub.lastIndexOf('/');
                if (slash >= 0) {
                    bedrockItemByShortName.putIfAbsent(sub.substring(slash + 1), key);
                }
            }
        }
        // Register terrain_texture.json aliases (like diamond_ore -> textures/blocks/diamond_ore_0)
        for (Map.Entry<String, String> tv : terrainVars.entrySet()) {
            if (decodedByBedrockKey.containsKey(tv.getValue())) {
                bedrockBlockByShortName.putIfAbsent(tv.getKey(), tv.getValue());
            }
        }

        // Special grass/podzol/mycelium/dirt_path TGA splits
        writeGrassSplits(decodedByBedrockKey, outFiles);

        // Slice dynamic item & painting atlases
        int slicedCount = 0;
        slicedCount += sliceVerticalStrip(decodedByBedrockKey.get("textures/items/watch_atlas"),
            "assets/minecraft/textures/item/clock_", 64, outFiles);
        slicedCount += sliceVerticalStrip(decodedByBedrockKey.get("textures/items/compass_atlas"),
            "assets/minecraft/textures/item/compass_", 32, outFiles);
        slicedCount += sliceVerticalStrip(decodedByBedrockKey.get("textures/items/recovery_compass_atlas"),
            "assets/minecraft/textures/item/recovery_compass_", 32, outFiles);
        slicedCount += slicePaintings(decodedByBedrockKey.get("textures/painting/kz"), vanillaJar, outFiles);

        // Copy direct standalone paintings (backyard, baroque, etc.) and earth/wind/fire/water if present
        for (String key : decodedByBedrockKey.keySet()) {
            if (key.startsWith("textures/painting/") && !key.equals("textures/painting/kz")) {
                outFiles.put("assets/minecraft/" + key + ".png", decodedByBedrockKey.get(key).pngBytes);
            }
        }

        // Map all 1.20.1 vanilla blocks -> assets/minecraft/textures/block/<name>.png
        int mappedBlocks = 0;
        List<String> unmappedBlocks = new ArrayList<>();
        Map<String, String> javaToBedrockResolved = new HashMap<>();
        for (String javaBlock : vanillaBlocks) {
            if (outFiles.containsKey("assets/minecraft/textures/block/" + javaBlock + ".png")) {
                // Already created (e.g. grass_block_side / grass_block_side_overlay / grass_block_snow)
                mappedBlocks++;
                continue;
            }
            String bedrockKey = resolveBlockKey(javaBlock, decodedByBedrockKey, bedrockBlockByShortName, terrainVars);
            if (bedrockKey != null) {
                DecodedAsset asset = decodedByBedrockKey.get(bedrockKey);
                if (asset != null && !asset.blank1x1) {
                    outFiles.put("assets/minecraft/textures/block/" + javaBlock + ".png", asset.pngBytes);
                    javaToBedrockResolved.put("assets/minecraft/textures/block/" + javaBlock + ".png", bedrockKey);
                    mappedBlocks++;
                    continue;
                }
            }
            unmappedBlocks.add(javaBlock);
        }

        // Also map Bedrock-only textures under textures/blocks/* directly into textures/block/* when non-conflicting
        for (String key : decodedByBedrockKey.keySet()) {
            if (key.startsWith("textures/blocks/")) {
                String sub = key.substring("textures/blocks/".length());
                String dst = "assets/minecraft/textures/block/" + sub + ".png";
                DecodedAsset asset = decodedByBedrockKey.get(key);
                if (!asset.blank1x1 && !outFiles.containsKey(dst)) {
                    outFiles.put(dst, asset.pngBytes);
                    javaToBedrockResolved.put(dst, key);
                }
            }
        }

        // Map all 1.20.1 vanilla items -> assets/minecraft/textures/item/<name>.png
        int mappedItems = 0;
        List<String> unmappedItems = new ArrayList<>();
        for (String javaItem : vanillaItems) {
            String dstPath = "assets/minecraft/textures/item/" + javaItem + ".png";
            if (outFiles.containsKey(dstPath)) {
                mappedItems++;
                continue;
            }
            String bedrockKey = resolveItemKey(javaItem, decodedByBedrockKey, bedrockItemByShortName, bedrockBlockByShortName);
            if (bedrockKey != null) {
                DecodedAsset asset = decodedByBedrockKey.get(bedrockKey);
                if (asset != null && !asset.blank1x1) {
                    outFiles.put(dstPath, asset.pngBytes);
                    javaToBedrockResolved.put(dstPath, bedrockKey);
                    mappedItems++;
                    continue;
                }
            }
            unmappedItems.add(javaItem);
        }

        // Also map custom Bedrock spawn eggs -> models/item/<mob>_spawn_egg.json + textures/item/<mob>_spawn_egg.png
        int spawnEggModels = generateSpawnEggOverrides(decodedByBedrockKey, vanillaSpawnEggs, outFiles);

        // Armor layers & trims & colormaps & environment & entities
        mapArmorAndTrims(decodedByBedrockKey, outFiles);
        mapEnvironmentAndColormaps(decodedByBedrockKey, vanillaJar, outFiles);
        mapEntities(decodedByBedrockKey, outFiles);
        generateMissingPackFallbacks(decodedByBedrockKey, outFiles);

        // Generate validated .png.mcmeta files for every animated texture
        int mcmetaCount = generateAllMcmetas(outFiles, flipbooks, javaToBedrockResolved, vanillaMcmetas);

        return new TextureReport(
            outFiles,
            packEntries.size(),
            tgaCount,
            pngCount,
            mcmetaCount,
            slicedCount,
            spawnEggModels,
            mappedBlocks,
            vanillaBlocks.size(),
            mappedItems,
            vanillaItems.size(),
            unmappedBlocks,
            unmappedItems,
            decodeErrors
        );
    }

    private static void writeGrassSplits(Map<String, DecodedAsset> assets, Map<String, byte[]> out) {
        DecodedAsset grassSide = assets.get("textures/blocks/grass_side");
        if (grassSide != null && !grassSide.blank1x1) {
            try {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(grassSide.pngBytes));
                if (img != null) {
                    out.put("assets/minecraft/textures/block/grass_block_side.png",
                        TgaDecoder.toPngBytes(TgaDecoder.applyAlphaMode(img, TgaDecoder.AlphaMode.FORCE_OPAQUE)));
                    out.put("assets/minecraft/textures/block/grass_block_side_overlay.png",
                        TgaDecoder.toPngBytes(TgaDecoder.applyAlphaMode(img, TgaDecoder.AlphaMode.EXTRACT_OVERLAY_ONLY)));
                }
            } catch (IOException ignored) {
            }
        }
        DecodedAsset grassSnow = assets.get("textures/blocks/grass_side_snowed");
        if (grassSnow != null && !grassSnow.blank1x1) {
            forceOpaqueCopy(grassSnow, "assets/minecraft/textures/block/grass_block_snow.png", out);
        }
        DecodedAsset podzolSide = assets.get("textures/blocks/dirt_podzol_side");
        if (podzolSide != null && !podzolSide.blank1x1) {
            forceOpaqueCopy(podzolSide, "assets/minecraft/textures/block/podzol_side.png", out);
        }
        DecodedAsset mycSide = assets.get("textures/blocks/mycelium_side");
        if (mycSide != null && !mycSide.blank1x1) {
            forceOpaqueCopy(mycSide, "assets/minecraft/textures/block/mycelium_side.png", out);
        }
        DecodedAsset pathSide = assets.get("textures/blocks/grass_path_side");
        if (pathSide != null && !pathSide.blank1x1) {
            forceOpaqueCopy(pathSide, "assets/minecraft/textures/block/dirt_path_side.png", out);
        }
    }

    private static void forceOpaqueCopy(DecodedAsset src, String destPath, Map<String, byte[]> out) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(src.pngBytes));
            if (img != null) {
                out.put(destPath, TgaDecoder.toPngBytes(TgaDecoder.applyAlphaMode(img, TgaDecoder.AlphaMode.FORCE_OPAQUE)));
            }
        } catch (IOException ignored) {
        }
    }

    private static int sliceVerticalStrip(DecodedAsset atlas, String destPrefix, int maxFrames, Map<String, byte[]> out) {
        if (atlas == null || atlas.blank1x1) {
            return 0;
        }
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(atlas.pngBytes));
            if (src == null || src.getWidth() <= 0 || src.getHeight() < src.getWidth()) {
                return 0;
            }
            int frameSize = src.getWidth();
            int availableFrames = src.getHeight() / frameSize;
            int count = Math.min(maxFrames, availableFrames);
            for (int i = 0; i < count; i++) {
                BufferedImage sub = src.getSubimage(0, i * frameSize, frameSize, frameSize);
                String num = (i < 10 ? "0" : "") + i;
                out.put(destPrefix + num + ".png", TgaDecoder.toPngBytes(sub));
            }
            return count;
        } catch (IOException e) {
            return 0;
        }
    }

    private static int slicePaintings(DecodedAsset kzAsset, ZipFile vanillaJar, Map<String, byte[]> out) {
        if (kzAsset == null || kzAsset.blank1x1) {
            return 0;
        }
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(kzAsset.pngBytes));
            if (src == null || src.getWidth() < 16 || src.getHeight() < 16) {
                return 0;
            }
            int cellW = src.getWidth() / 16;
            int cellH = src.getHeight() / 16;
            int count = 0;
            int[][] rects = {
                // {col, row, wCells, hCells} matching paintingNames order
                {0, 0, 1, 1}, {1, 0, 1, 1}, {2, 0, 1, 1}, {3, 0, 1, 1}, {4, 0, 1, 1}, {5, 0, 1, 1}, {6, 0, 1, 1},
                {0, 2, 2, 1}, {2, 2, 2, 1}, {4, 2, 2, 1}, {6, 2, 2, 1}, {8, 2, 2, 1},
                {0, 4, 1, 2}, {1, 4, 1, 2},
                {0, 6, 4, 2},
                {0, 8, 2, 2}, {2, 8, 2, 2}, {4, 8, 2, 2}, {6, 8, 2, 2}, {8, 8, 2, 2}, {10, 8, 2, 2},
                {0, 12, 4, 4}, {4, 12, 4, 4}, {8, 12, 4, 4},
                {12, 4, 4, 3}, {12, 7, 4, 3},
                {15, 0, 1, 1}
            };
            String[] names = {
                "kebab", "aztec", "alban", "aztec2", "bomb", "plant", "wasteland",
                "pool", "courbet", "sea", "sunset", "creebet",
                "wanderer", "graham",
                "fighters",
                "match", "bust", "stage", "void", "skull_and_roses", "wither",
                "pointer", "pigscene", "burning_skull",
                "skeleton", "donkey_kong",
                "back"
            };
            for (int i = 0; i < names.length; i++) {
                int[] r = rects[i];
                int x = r[0] * cellW;
                int y = r[1] * cellH;
                int w = r[2] * cellW;
                int h = r[3] * cellH;
                if (x + w <= src.getWidth() && y + h <= src.getHeight()) {
                    BufferedImage sub = src.getSubimage(x, y, w, h);
                    String dst = "assets/minecraft/textures/painting/" + names[i] + ".png";
                    // For "back", check if cell is completely transparent; if so fall back to vanilla back.png
                    if ("back".equals(names[i]) && isFullyTransparent(sub) && vanillaJar != null) {
                        ZipEntry vanBack = vanillaJar.getEntry(dst);
                        if (vanBack != null) {
                            out.put(dst, readAll(vanillaJar, vanBack));
                            count++;
                            continue;
                        }
                    }
                    out.put(dst, TgaDecoder.toPngBytes(sub));
                    count++;
                }
            }
            return count;
        } catch (IOException e) {
            return 0;
        }
    }

    private static boolean isFullyTransparent(BufferedImage img) {
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if (((img.getRGB(x, y) >>> 24) & 0xFF) > 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int generateSpawnEggOverrides(Map<String, DecodedAsset> assets, Set<String> vanillaEggs, Map<String, byte[]> out) {
        int count = 0;
        for (Map.Entry<String, DecodedAsset> e : assets.entrySet()) {
            String key = e.getKey();
            if (!key.startsWith("textures/items/spawn_eggs/spawn_egg_") || e.getValue().blank1x1) {
                continue;
            }
            String mob = key.substring("textures/items/spawn_eggs/spawn_egg_".length());
            // Normalize known Bedrock mob names -> Java spawn egg names
            String javaMob = switch (mob) {
                case "mooshroom_cow" -> "mooshroom";
                case "pigzombie", "zombie_pigman" -> "zombified_piglin";
                case "snow_golem" -> "snow_golem";
                case "iron_golem" -> "iron_golem";
                case "wandering_trader" -> "wandering_trader";
                case "trader_llama" -> "trader_llama";
                case "cave_spider" -> "cave_spider";
                case "magma_cube" -> "magma_cube";
                case "polar_bear" -> "polar_bear";
                case "wither_skeleton" -> "wither_skeleton";
                case "zombie_villager" -> "zombie_villager";
                case "tropical_fish" -> "tropical_fish";
                case "elder_guardian" -> "elder_guardian";
                case "glow_squid" -> "glow_squid";
                case "ender_dragon" -> "ender_dragon";
                default -> mob;
            };
            String eggItem = javaMob + "_spawn_egg";
            out.put("assets/minecraft/textures/item/" + eggItem + ".png", e.getValue().pngBytes);
            if (vanillaEggs.isEmpty() || vanillaEggs.contains(eggItem)) {
                String json = "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"minecraft:item/" + eggItem + "\"}}";
                out.put("assets/minecraft/models/item/" + eggItem + ".json", json.getBytes(StandardCharsets.UTF_8));
                count++;
            }
        }
        return count;
    }

    private static void mapArmorAndTrims(Map<String, DecodedAsset> assets, Map<String, byte[]> out) {
        Map<String, String> armorMap = Map.ofEntries(
            Map.entry("textures/models/armor/chain_1", "assets/minecraft/textures/models/armor/chainmail_layer_1.png"),
            Map.entry("textures/models/armor/chain_2", "assets/minecraft/textures/models/armor/chainmail_layer_2.png"),
            Map.entry("textures/models/armor/diamond_1", "assets/minecraft/textures/models/armor/diamond_layer_1.png"),
            Map.entry("textures/models/armor/diamond_2", "assets/minecraft/textures/models/armor/diamond_layer_2.png"),
            Map.entry("textures/models/armor/gold_1", "assets/minecraft/textures/models/armor/gold_layer_1.png"),
            Map.entry("textures/models/armor/gold_2", "assets/minecraft/textures/models/armor/gold_layer_2.png"),
            Map.entry("textures/models/armor/iron_1", "assets/minecraft/textures/models/armor/iron_layer_1.png"),
            Map.entry("textures/models/armor/iron_2", "assets/minecraft/textures/models/armor/iron_layer_2.png"),
            Map.entry("textures/models/armor/netherite_1", "assets/minecraft/textures/models/armor/netherite_layer_1.png"),
            Map.entry("textures/models/armor/netherite_2", "assets/minecraft/textures/models/armor/netherite_layer_2.png"),
            Map.entry("textures/models/armor/turtle_1", "assets/minecraft/textures/models/armor/turtle_layer_1.png")
        );
        for (Map.Entry<String, String> e : armorMap.entrySet()) {
            DecodedAsset a = assets.get(e.getKey());
            if (a != null && !a.blank1x1) {
                out.put(e.getValue(), a.pngBytes);
            }
        }
        // Elytra
        DecodedAsset elytra = assets.get("textures/models/armor/elytra");
        if (elytra != null && !elytra.blank1x1) {
            out.put("assets/minecraft/textures/entity/elytra.png", elytra.pngBytes);
        }
        // Trims: Bedrock stores in textures/trims/<pattern>.png; Java 1.20.1 uses textures/trims/models/armor/<pattern>.png
        for (Map.Entry<String, DecodedAsset> e : assets.entrySet()) {
            String key = e.getKey();
            if (key.startsWith("textures/trims/") && !key.startsWith("textures/trims/color_palettes/") && !e.getValue().blank1x1) {
                String sub = key.substring("textures/trims/".length());
                if (!sub.contains("/")) {
                    out.put("assets/minecraft/textures/trims/models/armor/" + sub + ".png", e.getValue().pngBytes);
                }
            }
        }
    }

    private static void mapEnvironmentAndColormaps(Map<String, DecodedAsset> assets, ZipFile vanillaJar, Map<String, byte[]> out) {
        DecodedAsset sun = assets.get("textures/environment/sun");
        if (sun != null && !sun.blank1x1) {
            out.put("assets/minecraft/textures/environment/sun.png", sun.pngBytes);
        }
        DecodedAsset moon = assets.get("textures/environment/moon_phases");
        if (moon != null && !moon.blank1x1) {
            out.put("assets/minecraft/textures/environment/moon_phases.png", moon.pngBytes);
        }
        DecodedAsset endSky = assets.get("textures/environment/end_sky");
        if (endSky != null && !endSky.blank1x1) {
            out.put("assets/minecraft/textures/environment/end_sky.png", endSky.pngBytes);
        }
        // Weather: Bedrock stores unified weather.png; Java separates rain.png / snow.png. Keep vanilla if valid, or slice weather.png.
        DecodedAsset weather = assets.get("textures/environment/weather");
        if (weather != null && !weather.blank1x1) {
            try {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(weather.pngBytes));
                if (img != null && img.getWidth() >= 64 && img.getHeight() >= 64) {
                    // Preserve weather.png and avoid replacing vanilla rain/snow if weather.png isn't split cleanly
                    out.put("assets/minecraft/textures/environment/weather.png", weather.pngBytes);
                }
            } catch (IOException ignored) {
            }
        }
        // Colormaps
        for (String name : List.of("grass", "foliage")) {
            DecodedAsset cm = assets.get("textures/colormap/" + name);
            if (cm != null && !cm.blank1x1) {
                out.put("assets/minecraft/textures/colormap/" + name + ".png", cm.pngBytes);
            }
        }
    }

    private static void mapEntities(Map<String, DecodedAsset> assets, Map<String, byte[]> out) {
        for (Map.Entry<String, String> e : BEDROCK_TO_JAVA_ENTITY.entrySet()) {
            DecodedAsset a = assets.get(e.getKey());
            if (a != null && !a.blank1x1) {
                out.put("assets/minecraft/" + e.getValue() + ".png", a.pngBytes);
            }
        }
        // Also normalize wood-type entity textures cleanly
        String[] woods = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo", "crimson", "warped"};
        for (String w : woods) {
            String bw = w.equals("dark_oak") ? "darkoak" : w;
            DecodedAsset sign = assets.get("textures/entity/" + w + "_sign");
            if (sign == null) sign = assets.get("textures/entity/sign_" + bw);
            if (sign == null && w.equals("oak")) sign = assets.get("textures/entity/sign");
            if (sign != null && !sign.blank1x1) {
                out.put("assets/minecraft/textures/entity/signs/" + w + ".png", sign.pngBytes);
            }
            DecodedAsset hSign = assets.get("textures/entity/" + w + "_hanging_sign");
            if (hSign != null && !hSign.blank1x1) {
                out.put("assets/minecraft/textures/entity/signs/hanging/" + w + ".png", hSign.pngBytes);
            }
            DecodedAsset boat = assets.get("textures/entity/boat/" + w + "_boat");
            if (boat == null) boat = assets.get("textures/entity/boat/boat_" + bw);
            if (boat == null && w.equals("bamboo")) boat = assets.get("textures/entity/boat/bamboo_raft");
            if (boat != null && !boat.blank1x1) {
                out.put("assets/minecraft/textures/entity/boat/" + w + ".png", boat.pngBytes);
            }
            DecodedAsset cBoat = assets.get("textures/entity/boat/chest_boat_" + bw);
            if (cBoat == null) cBoat = assets.get("textures/entity/boat/chest_boat_" + w);
            if (cBoat != null && !cBoat.blank1x1) {
                out.put("assets/minecraft/textures/entity/chest_boat/" + w + ".png", cBoat.pngBytes);
            }
        }
    }

    private static void generateMissingPackFallbacks(Map<String, DecodedAsset> assets, Map<String, byte[]> out) {
        // 4 textures referenced in Bedrock pack JSONs omitted from the archive (vanilla Bedrock built-ins)
        // 1. textures/entity/loyalty_rope
        if (!out.containsKey("assets/minecraft/textures/entity/loyalty_rope.png")) {
            DecodedAsset lead = assets.get("textures/entity/lead_rope");
            byte[] bytes = lead != null ? lead.pngBytes : solidRgbaPng(16, 16, 0xFF8BE5FF);
            out.put("assets/minecraft/textures/entity/loyalty_rope.png", bytes);
        }
        // 2. textures/entity/wolf/wolf_armor_cracked_none
        if (!out.containsKey("assets/minecraft/textures/entity/wolf/wolf_armor_cracked_none.png")) {
            out.put("assets/minecraft/textures/entity/wolf/wolf_armor_cracked_none.png", solidRgbaPng(1, 1, 0x00000000));
        }
        // 3. textures/misc/enchanted_item_glint
        if (!out.containsKey("assets/minecraft/textures/misc/enchanted_item_glint.png")) {
            out.put("assets/minecraft/textures/misc/enchanted_item_glint.png", createGlintFallbackPng());
        }
        // 4. textures/models/armor/copper_1
        if (!out.containsKey("assets/minecraft/textures/models/armor/copper_1.png")) {
            DecodedAsset iron1 = assets.get("textures/models/armor/iron_1");
            byte[] bytes = iron1 != null ? tintImage(iron1.pngBytes, 0.90f, 0.56f, 0.36f) : solidRgbaPng(64, 32, 0xFFE08050);
            out.put("assets/minecraft/textures/models/armor/copper_1.png", bytes);
        }
    }

    private static int generateAllMcmetas(
        Map<String, byte[]> outFiles,
        FlipbookMcmetaGenerator flipbooks,
        Map<String, String> javaToBedrockResolved,
        Map<String, byte[]> vanillaMcmetas
    ) {
        int count = 0;
        List<String> pngPaths = new ArrayList<>();
        for (String p : outFiles.keySet()) {
            if (p.endsWith(".png")) {
                pngPaths.add(p);
            }
        }
        for (String pngPath : pngPaths) {
            byte[] pngBytes = outFiles.get(pngPath);
            // Lookup flipbook def via original bedrock key or resolved bedrock key or filename fallback
            FlipbookMcmetaGenerator.FlipbookDef def = null;
            String bedrockOrig = javaToBedrockResolved.get(pngPath);
            if (bedrockOrig != null) {
                def = flipbooks.find(bedrockOrig);
            }
            if (def == null) {
                // Direct path inside textures/
                String relNoExt = pngPath.substring("assets/minecraft/".length(), pngPath.length() - 4);
                def = flipbooks.find(relNoExt);
                if (def == null && relNoExt.startsWith("textures/block/")) {
                    def = flipbooks.find("textures/blocks/" + relNoExt.substring("textures/block/".length()));
                } else if (def == null && relNoExt.startsWith("textures/item/")) {
                    def = flipbooks.find("textures/items/" + relNoExt.substring("textures/item/".length()));
                }
            }
            byte[] vanillaMeta = vanillaMcmetas.get(pngPath + ".mcmeta");
            byte[] mcmeta = flipbooks.buildMcmetaIfAnimated(pngPath, def, pngBytes, vanillaMeta);
            if (mcmeta != null) {
                outFiles.put(pngPath + ".mcmeta", mcmeta);
                count++;
            }
        }
        return count;
    }

    private static String resolveBlockKey(
        String javaName,
        Map<String, DecodedAsset> all,
        Map<String, String> byShortName,
        Map<String, String> terrainVars
    ) {
        // 1. Direct match under textures/blocks/<javaName>
        String direct = byShortName.get(javaName);
        if (isUsable(all, direct)) return direct;

        // 2. Explicit Bedrock alias
        String explicit = JAVA_TO_BEDROCK_BLOCK.get(javaName);
        if (explicit != null) {
            if (isUsable(all, explicit)) return explicit;
            String mapped = byShortName.get(explicit);
            if (isUsable(all, mapped)) return mapped;
            String tv = terrainVars.get(explicit);
            if (isUsable(all, tv)) return tv;
        }

        // 3. Heuristic normalizations (Java 1.20.1 <-> Bedrock naming patterns)
        for (String candidate : generateBlockCandidates(javaName)) {
            if (isUsable(all, candidate)) return candidate;
            if (isUsable(all, "textures/blocks/" + candidate)) return "textures/blocks/" + candidate;
            String mapped = byShortName.get(candidate);
            if (isUsable(all, mapped)) return mapped;
            String tv = terrainVars.get(candidate);
            if (isUsable(all, tv)) return tv;
        }
        return null;
    }

    private static String resolveItemKey(
        String javaName,
        Map<String, DecodedAsset> all,
        Map<String, String> byItemShort,
        Map<String, String> byBlockShort
    ) {
        String direct = byItemShort.get(javaName);
        if (isUsable(all, direct)) return direct;

        String explicit = JAVA_TO_BEDROCK_ITEM.get(javaName);
        if (explicit != null) {
            if (isUsable(all, explicit)) return explicit;
            String mapped = byItemShort.get(explicit);
            if (isUsable(all, mapped)) return mapped;
            String bMapped = byBlockShort.get(explicit);
            if (isUsable(all, bMapped)) return bMapped;
        }

        for (String candidate : generateItemCandidates(javaName)) {
            if (isUsable(all, candidate)) return candidate;
            if (isUsable(all, "textures/items/" + candidate)) return "textures/items/" + candidate;
            if (isUsable(all, "textures/blocks/" + candidate)) return "textures/blocks/" + candidate;
            String mapped = byItemShort.get(candidate);
            if (isUsable(all, mapped)) return mapped;
        }
        return null;
    }

    private static List<String> generateBlockCandidates(String name) {
        List<String> list = new ArrayList<>();
        list.add(name + "_0");
        if (name.endsWith("_wool")) list.add("wool_colored_" + swapSilver(stripSuffix(name, "_wool")));
        if (name.endsWith("_concrete")) list.add("concrete_" + swapSilver(stripSuffix(name, "_concrete")));
        if (name.endsWith("_concrete_powder")) list.add("concrete_powder_" + swapSilver(stripSuffix(name, "_concrete_powder")));
        if (name.endsWith("_terracotta")) {
            String col = stripSuffix(name, "_terracotta");
            if (col.startsWith("light_")) list.add("hardened_clay_stained_" + swapSilver(col));
            list.add("hardened_clay_stained_" + swapSilver(col));
        }
        if (name.endsWith("_stained_glass")) list.add("glass_" + swapSilver(stripSuffix(name, "_stained_glass")));
        if (name.endsWith("_stained_glass_pane_top")) list.add("glass_pane_top_" + swapSilver(stripSuffix(name, "_stained_glass_pane_top")));
        if (name.endsWith("_glazed_terracotta")) list.add("glazed_terracotta_" + swapSilver(stripSuffix(name, "_glazed_terracotta")));
        if (name.endsWith("_planks")) {
            String w = stripSuffix(name, "_planks");
            list.add("planks_" + w);
            list.add("huge_fungus/" + w + "_planks");
            if (w.equals("oak")) list.add("planks_oak");
            if (w.equals("dark_oak")) list.add("planks_big_oak");
            if (w.equals("bamboo")) list.add("bamboo_planks");
        }
        if (name.endsWith("_log")) {
            String w = stripSuffix(name, "_log");
            list.add("log_" + (w.equals("dark_oak") ? "big_oak" : w) + "_side");
            list.add(w + "_log_side");
        }
        if (name.endsWith("_log_top")) {
            String w = stripSuffix(name, "_log_top");
            list.add("log_" + (w.equals("dark_oak") ? "big_oak" : w) + "_top");
            list.add(w + "_log_top");
        }
        if (name.startsWith("stripped_") && name.endsWith("_log")) {
            String w = name.substring("stripped_".length(), name.length() - 4);
            list.add("stripped_" + w + "_log");
            list.add("stripped_" + w + "_log_side");
        }
        if (name.endsWith("_leaves")) {
            String w = stripSuffix(name, "_leaves");
            list.add("leaves_" + (w.equals("dark_oak") ? "big_oak" : w));
            list.add("leaves_" + w + "_carried");
        }
        if (name.endsWith("_sapling")) {
            String w = stripSuffix(name, "_sapling");
            list.add("sapling_" + (w.equals("dark_oak") ? "roofed_oak" : w));
        }
        if (name.endsWith("_door_top")) {
            String w = stripSuffix(name, "_door_top");
            list.add("door_" + (w.equals("oak") ? "wood" : w) + "_upper");
            list.add(w + "_door_upper");
        }
        if (name.endsWith("_door_bottom")) {
            String w = stripSuffix(name, "_door_bottom");
            list.add("door_" + (w.equals("oak") ? "wood" : w) + "_lower");
            list.add(w + "_door_lower");
        }
        if (name.endsWith("_trapdoor")) {
            String w = stripSuffix(name, "_trapdoor");
            list.add(w + "_trapdoor");
            if (w.equals("oak")) list.add("trapdoor");
        }
        if (name.contains("shulker_box")) {
            String pre = name.replace("_shulker_box", "").replace("shulker_box", "");
            if (pre.isEmpty()) list.add("shulker_top_undyed");
            else list.add("shulker_top_" + swapSilver(pre));
        }
        if (name.endsWith("_candle") || name.endsWith("_candle_lit")) {
            list.add("candles/" + name);
        }
        if (name.contains("deepslate")) {
            list.add("deepslate/" + name);
        }
        if (name.contains("crimson") || name.contains("warped")) {
            list.add("huge_fungus/" + name);
        }
        if (name.contains("candle")) {
            list.add("candles/" + name);
        }
        if (name.contains("coral")) {
            String t = name.replace("dead_", "").replace("_coral_block", "").replace("_coral_fan", "").replace("_coral", "");
            String col = switch (t) {
                case "tube" -> "blue";
                case "brain" -> "pink";
                case "bubble" -> "purple";
                case "fire" -> "red";
                case "horn" -> "yellow";
                default -> t;
            };
            boolean dead = name.startsWith("dead_");
            boolean fan = name.endsWith("_fan");
            boolean block = name.endsWith("_block");
            String dSuf = dead ? "_dead" : "";
            if (block) {
                list.add("coral_" + col + dSuf);
            } else if (fan) {
                list.add("coral_fan_" + col + dSuf);
                list.add("carried/coral_fan_" + col + dSuf + "_carried");
            } else {
                list.add("coral_plant_" + col + dSuf);
                list.add("carried/coral_plant_" + col + dSuf + "_carried");
            }
        }
        if (name.startsWith("potted_") || name.contains("flower") || name.contains("tulip")) {
            if (name.equals("poppy")) list.add("flower_rose");
            if (name.equals("dandelion")) list.add("flower_dandelion");
            list.add("flower_" + name);
        }
        return list;
    }

    private static List<String> generateItemCandidates(String name) {
        List<String> list = new ArrayList<>();
        if (name.endsWith("_dye")) {
            String col = swapSilver(stripSuffix(name, "_dye"));
            list.add("dye_powder_" + col);
            list.add(name);
        }
        if (name.endsWith("_bed")) {
            String col = swapSilver(stripSuffix(name, "_bed"));
            list.add("bed_" + col);
        }
        if (name.endsWith("_boat")) {
            String w = stripSuffix(name, "_boat");
            list.add("boat_" + (w.equals("dark_oak") ? "darkoak" : w));
        }
        if (name.endsWith("_chest_boat")) {
            String w = stripSuffix(name, "_chest_boat");
            list.add((w.equals("dark_oak") ? "darkoak" : w) + "_chest_boat");
            list.add("chest_boat_" + w);
        }
        if (name.endsWith("_door")) {
            String w = stripSuffix(name, "_door");
            list.add("door_" + (w.equals("oak") ? "wood" : w));
        }
        if (name.endsWith("_sign")) {
            String w = stripSuffix(name, "_sign");
            list.add("sign_" + w);
            list.add(name);
        }
        if (name.endsWith("_hanging_sign")) {
            list.add(name);
        }
        if (name.startsWith("music_disc_")) {
            list.add("record_" + name.substring("music_disc_".length()));
        }
        if (name.endsWith("_minecart")) {
            String t = stripSuffix(name, "_minecart");
            list.add("minecart_" + t);
        }
        if (name.startsWith("golden_")) {
            String s = name.substring("golden_".length());
            list.add(s + "_golden");
            list.add("gold_" + s);
        }
        if (name.endsWith("_sherd")) {
            list.add(name.replace("_pottery_sherd", "_pottery_sherd"));
        }
        if (name.endsWith("_smithing_template")) {
            list.add(name);
            list.add(name.replace("_armor_trim_smithing_template", "_armor_trim"));
        }
        if (name.startsWith("leather_")) list.add(name);
        if (name.startsWith("netherite_")) list.add(name);
        if (name.startsWith("diamond_")) list.add(name);
        if (name.startsWith("iron_")) list.add(name);
        if (name.startsWith("stone_")) list.add(name);
        if (name.startsWith("wooden_")) list.add("wood_" + name.substring("wooden_".length()));
        if (name.endsWith("_bucket")) {
            String prefix = stripSuffix(name, "_bucket");
            list.add("bucket_" + prefix);
        }
        if (name.endsWith("_spawn_egg")) {
            String mob = stripSuffix(name, "_spawn_egg");
            list.add("spawn_eggs/spawn_egg_" + mob);
        }
        return list;
    }

    private static String swapSilver(String col) {
        return "light_gray".equals(col) ? "silver" : col;
    }

    private static String stripSuffix(String s, String suffix) {
        return s.endsWith(suffix) ? s.substring(0, s.length() - suffix.length()) : s;
    }

    private static boolean isUsable(Map<String, DecodedAsset> all, String key) {
        if (key == null) return false;
        DecodedAsset a = all.get(key);
        return a != null && !a.blank1x1;
    }

    private static TgaDecoder.AlphaMode selectTgaAlphaMode(String noExt) {
        if (noExt.equals("textures/blocks/grass_side")
            || noExt.equals("textures/blocks/grass_side_snowed")
            || noExt.equals("textures/blocks/dirt_podzol_side")
            || noExt.equals("textures/blocks/mycelium_side")
            || noExt.equals("textures/blocks/grass_path_side")
            || noExt.equals("textures/blocks/snow")
            || noExt.equals("textures/blocks/grass_carried")) {
            return TgaDecoder.AlphaMode.FORCE_OPAQUE;
        }
        if (needsCutoutFloor(noExt)) {
            return TgaDecoder.AlphaMode.CUTOUT_ALPHA_FLOOR;
        }
        return TgaDecoder.AlphaMode.PRESERVE;
    }

    private static boolean needsCutoutFloor(String noExt) {
        return noExt.contains("leaves")
            || noExt.endsWith("/fern")
            || noExt.endsWith("/tallgrass")
            || noExt.contains("double_plant_")
            || noExt.endsWith("/vine")
            || noExt.endsWith("/waterlily");
    }

    private static Map<String, ZipEntry> collectPackImageEntries(ZipFile mcpack) {
        // Order: root first, then subpacks/SP2 overrides root. Ignore SP0, SP1, _mers/_normal/_mer.
        Map<String, ZipEntry> map = new LinkedHashMap<>();
        List<ZipEntry> sp2 = new ArrayList<>();
        Enumeration<? extends ZipEntry> en = mcpack.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            if (e.isDirectory()) continue;
            String n = e.getName();
            if (n.startsWith("subpacks/SP0/") || n.startsWith("subpacks/SP1/")) continue;
            boolean isSp2 = n.startsWith("subpacks/SP2/");
            String rel = isSp2 ? n.substring("subpacks/SP2/".length()) : n;
            String lower = rel.toLowerCase(Locale.ROOT);
            if (!lower.startsWith("textures/")) continue;
            if (!lower.endsWith(".png") && !lower.endsWith(".tga")) continue;
            if (lower.endsWith("_mers.png") || lower.endsWith("_normal.png") || lower.endsWith("_mer.png")
                || lower.endsWith("_mers.tga") || lower.endsWith("_normal.tga") || lower.endsWith("_mer.tga")) {
                continue;
            }
            if (isSp2) {
                sp2.add(e);
            } else {
                map.put(rel, e);
            }
        }
        for (ZipEntry e : sp2) {
            String rel = e.getName().substring("subpacks/SP2/".length());
            map.put(rel, e);
        }
        return map;
    }

    private static Map<String, String> loadTerrainTextureMap(ZipFile mcpack) {
        Map<String, String> out = new HashMap<>();
        ZipEntry e = mcpack.getEntry("subpacks/SP2/textures/terrain_texture.json");
        if (e == null) e = mcpack.getEntry("textures/terrain_texture.json");
        if (e == null) return out;
        try {
            String raw = new String(readAll(mcpack, e), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            if (!root.has("texture_data") || !root.get("texture_data").isJsonObject()) return out;
            JsonObject td = root.getAsJsonObject("texture_data");
            for (Map.Entry<String, JsonElement> item : td.entrySet()) {
                if (!item.getValue().isJsonObject()) continue;
                JsonElement texEl = item.getValue().getAsJsonObject().get("textures");
                String firstTex = extractFirstTexturePath(texEl);
                if (firstTex != null && !firstTex.isEmpty()) {
                    out.put(item.getKey().toLowerCase(Locale.ROOT), stripExt(firstTex.toLowerCase(Locale.ROOT)));
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static String extractFirstTexturePath(JsonElement el) {
        if (el == null || el.isJsonNull()) return null;
        if (el.isJsonPrimitive()) return el.getAsString();
        if (el.isJsonObject()) {
            JsonElement path = el.getAsJsonObject().get("path");
            if (path != null && path.isJsonPrimitive()) return path.getAsString();
            JsonElement vars = el.getAsJsonObject().get("variations");
            if (vars != null) return extractFirstTexturePath(vars);
        }
        if (el.isJsonArray() && !el.getAsJsonArray().isEmpty()) {
            return extractFirstTexturePath(el.getAsJsonArray().get(0));
        }
        return null;
    }

    private static Map<String, byte[]> collectVanillaMcmetas(ZipFile vanillaJar) throws IOException {
        Map<String, byte[]> map = new HashMap<>();
        if (vanillaJar == null) return map;
        Enumeration<? extends ZipEntry> en = vanillaJar.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            if (e.getName().startsWith("assets/minecraft/textures/") && e.getName().endsWith(".png.mcmeta")) {
                map.put(e.getName(), readAll(vanillaJar, e));
            }
        }
        return map;
    }

    private static Set<String> collectVanillaPngBasenames(ZipFile vanillaJar, String prefix) {
        Set<String> set = new TreeSet<>();
        if (vanillaJar == null) return set;
        Enumeration<? extends ZipEntry> en = vanillaJar.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            String n = e.getName();
            if (n.startsWith(prefix) && n.endsWith(".png")) {
                set.add(n.substring(prefix.length(), n.length() - 4));
            }
        }
        return set;
    }

    private static Set<String> collectVanillaSpawnEggNames(ZipFile vanillaJar) {
        Set<String> set = new HashSet<>();
        if (vanillaJar == null) return set;
        Enumeration<? extends ZipEntry> en = vanillaJar.entries();
        while (en.hasMoreElements()) {
            String n = en.nextElement().getName();
            if (n.startsWith("assets/minecraft/models/item/") && n.endsWith("_spawn_egg.json")) {
                set.add(n.substring("assets/minecraft/models/item/".length(), n.length() - 5));
            }
        }
        return set;
    }

    private static byte[] solidRgbaPng(int w, int h, int argb) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] row = new int[w];
        Arrays.fill(row, argb);
        for (int y = 0; y < h; y++) {
            img.setRGB(0, y, w, 1, row, 0, w);
        }
        try {
            return TgaDecoder.toPngBytes(img);
        } catch (IOException e) {
            return new byte[0];
        }
    }

    private static byte[] createGlintFallbackPng() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                int diag = (x + y) & 15;
                int v = diag < 4 ? 220 : (diag < 8 ? 140 : 70);
                int r = (v * 185) / 255;
                int g = (v * 110) / 255;
                int b = v;
                img.setRGB(x, y, (0x88 << 24) | (r << 16) | (g << 8) | b);
            }
        }
        try {
            return TgaDecoder.toPngBytes(img);
        } catch (IOException e) {
            return new byte[0];
        }
    }

    private static byte[] tintImage(byte[] pngBytes, float mr, float mg, float mb) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(pngBytes));
            if (src == null) return pngBytes;
            BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = out.createGraphics();
            g2.drawImage(src, 0, 0, null);
            g2.dispose();
            for (int y = 0; y < out.getHeight(); y++) {
                for (int x = 0; x < out.getWidth(); x++) {
                    int p = out.getRGB(x, y);
                    int a = (p >>> 24) & 0xFF;
                    int r = Math.min(255, Math.round(((p >>> 16) & 0xFF) * mr));
                    int g = Math.min(255, Math.round(((p >>> 8) & 0xFF) * mg));
                    int b = Math.min(255, Math.round((p & 0xFF) * mb));
                    out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                }
            }
            return TgaDecoder.toPngBytes(out);
        } catch (IOException e) {
            return pngBytes;
        }
    }

    private static String stripExt(String p) {
        int dot = p.lastIndexOf('.');
        return dot >= 0 ? p.substring(0, dot) : p;
    }

    private static byte[] readAll(ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream is = zip.getInputStream(entry)) {
            return is.readAllBytes();
        }
    }

    private record DecodedAsset(String bedrockKey, byte[] pngBytes, int width, int height, boolean blank1x1) {
    }

    private static Map<String, String> createBlockAliasMap() {
        Map<String, String> m = new HashMap<>();
        // Ore / Stone / Bricks / Quartz / Sandstone
        m.put("diamond_ore", "textures/blocks/diamond_ore_0");
        m.put("deepslate_diamond_ore", "textures/blocks/deepslate/deepslate_diamond_ore");
        m.put("stone_bricks", "textures/blocks/stonebrick");
        m.put("mossy_stone_bricks", "textures/blocks/stonebrick_mossy");
        m.put("cracked_stone_bricks", "textures/blocks/stonebrick_cracked");
        m.put("chiseled_stone_bricks", "textures/blocks/stonebrick_carved");
        m.put("chiseled_quartz_block", "textures/blocks/quartz_block_chiseled");
        m.put("chiseled_quartz_block_top", "textures/blocks/quartz_block_chiseled_top");
        m.put("quartz_pillar", "textures/blocks/quartz_block_lines");
        m.put("quartz_pillar_top", "textures/blocks/quartz_block_lines_top");
        m.put("smooth_sandstone", "textures/blocks/sandstone_smooth");
        m.put("cut_sandstone", "textures/blocks/sandstone_carved");
        m.put("chiseled_sandstone", "textures/blocks/sandstone_carved");
        m.put("sandstone", "textures/blocks/sandstone_side");
        m.put("red_sandstone", "textures/blocks/red_sandstone_side");
        m.put("cut_red_sandstone", "textures/blocks/red_sandstone_carved");
        m.put("chiseled_red_sandstone", "textures/blocks/red_sandstone_carved");
        m.put("smooth_red_sandstone", "textures/blocks/red_sandstone_smooth");
        m.put("andesite", "textures/blocks/stone_andesite");
        m.put("polished_andesite", "textures/blocks/stone_andesite_smooth");
        m.put("diorite", "textures/blocks/stone_diorite");
        m.put("polished_diorite", "textures/blocks/stone_diorite_smooth");
        m.put("granite", "textures/blocks/stone_granite");
        m.put("polished_granite", "textures/blocks/stone_granite_smooth");
        m.put("smooth_stone", "textures/blocks/stone_slab_top");
        m.put("smooth_stone_slab_side", "textures/blocks/stone_slab_side");
        m.put("mossy_cobblestone", "textures/blocks/cobblestone_mossy");
        m.put("nether_quartz_ore", "textures/blocks/quartz_ore");
        m.put("rooted_dirt", "textures/blocks/dirt_with_roots");
        m.put("anvil", "textures/blocks/anvil_base");
        m.put("anvil_top", "textures/blocks/anvil_top_damaged_0");
        m.put("chipped_anvil_top", "textures/blocks/anvil_top_damaged_1");
        m.put("damaged_anvil_top", "textures/blocks/anvil_top_damaged_2");
        for (int i = 0; i <= 9; i++) {
            m.put("destroy_stage_" + i, "textures/environment/destroy_stage_" + i);
        }
        m.put("terracotta", "textures/blocks/hardened_clay");
        m.put("packed_ice", "textures/blocks/ice_packed");
        m.put("prismarine_bricks", "textures/blocks/prismarine_bricks");
        m.put("dark_prismarine", "textures/blocks/prismarine_dark");
        m.put("prismarine", "textures/blocks/prismarine_rough");
        m.put("bricks", "textures/blocks/brick");
        m.put("nether_bricks", "textures/blocks/nether_brick");
        m.put("cracked_nether_bricks", "textures/blocks/cracked_nether_bricks");
        m.put("chiseled_nether_bricks", "textures/blocks/chiseled_nether_bricks");
        m.put("end_stone_bricks", "textures/blocks/end_bricks");
        m.put("magma", "textures/blocks/magma");
        m.put("slime_block", "textures/blocks/slime");
        m.put("cobweb", "textures/blocks/web");
        // Flora / Crops / Ground
        m.put("grass_block_top", "textures/blocks/grass_top");
        m.put("grass", "textures/blocks/tallgrass");
        m.put("tall_grass_bottom", "textures/blocks/double_plant_grass_bottom");
        m.put("tall_grass_top", "textures/blocks/double_plant_grass_top");
        m.put("large_fern_bottom", "textures/blocks/double_plant_fern_bottom");
        m.put("large_fern_top", "textures/blocks/double_plant_fern_top");
        m.put("lilac_bottom", "textures/blocks/double_plant_syringa_bottom");
        m.put("lilac_top", "textures/blocks/double_plant_syringa_top");
        m.put("peony_bottom", "textures/blocks/double_plant_paeonia_bottom");
        m.put("peony_top", "textures/blocks/double_plant_paeonia_top");
        m.put("rose_bush_bottom", "textures/blocks/double_plant_rose_bottom");
        m.put("rose_bush_top", "textures/blocks/double_plant_rose_top");
        m.put("sunflower_bottom", "textures/blocks/double_plant_sunflower_bottom");
        m.put("sunflower_top", "textures/blocks/double_plant_sunflower_top");
        m.put("sunflower_front", "textures/blocks/double_plant_sunflower_front");
        m.put("sunflower_back", "textures/blocks/double_plant_sunflower_back");
        m.put("poppy", "textures/blocks/flower_rose");
        m.put("dandelion", "textures/blocks/flower_dandelion");
        m.put("allium", "textures/blocks/flower_allium");
        m.put("azure_bluet", "textures/blocks/flower_houstonia");
        m.put("blue_orchid", "textures/blocks/flower_blue_orchid");
        m.put("cornflower", "textures/blocks/flower_cornflower");
        m.put("lily_of_the_valley", "textures/blocks/flower_lily_of_the_valley");
        m.put("oxeye_daisy", "textures/blocks/flower_oxeye_daisy");
        m.put("red_tulip", "textures/blocks/flower_tulip_red");
        m.put("orange_tulip", "textures/blocks/flower_tulip_orange");
        m.put("white_tulip", "textures/blocks/flower_tulip_white");
        m.put("pink_tulip", "textures/blocks/flower_tulip_pink");
        m.put("wither_rose", "textures/blocks/flower_wither_rose");
        m.put("sugar_cane", "textures/blocks/reeds");
        m.put("lily_pad", "textures/blocks/waterlily");
        m.put("wheat_stage0", "textures/blocks/wheat_stage_0");
        m.put("wheat_stage1", "textures/blocks/wheat_stage_1");
        m.put("wheat_stage2", "textures/blocks/wheat_stage_2");
        m.put("wheat_stage3", "textures/blocks/wheat_stage_3");
        m.put("wheat_stage4", "textures/blocks/wheat_stage_4");
        m.put("wheat_stage5", "textures/blocks/wheat_stage_5");
        m.put("wheat_stage6", "textures/blocks/wheat_stage_6");
        m.put("wheat_stage7", "textures/blocks/wheat_stage_7");
        m.put("carrots_stage0", "textures/blocks/carrots_stage_0");
        m.put("carrots_stage1", "textures/blocks/carrots_stage_1");
        m.put("carrots_stage2", "textures/blocks/carrots_stage_2");
        m.put("carrots_stage3", "textures/blocks/carrots_stage_3");
        m.put("potatoes_stage0", "textures/blocks/potatoes_stage_0");
        m.put("potatoes_stage1", "textures/blocks/potatoes_stage_1");
        m.put("potatoes_stage2", "textures/blocks/potatoes_stage_2");
        m.put("potatoes_stage3", "textures/blocks/potatoes_stage_3");
        m.put("beetroots_stage0", "textures/blocks/beetroots_stage_0");
        m.put("beetroots_stage1", "textures/blocks/beetroots_stage_1");
        m.put("beetroots_stage2", "textures/blocks/beetroots_stage_2");
        m.put("beetroots_stage3", "textures/blocks/beetroots_stage_3");
        m.put("nether_wart_stage0", "textures/blocks/nether_wart_stage_0");
        m.put("nether_wart_stage1", "textures/blocks/nether_wart_stage_1");
        m.put("nether_wart_stage2", "textures/blocks/nether_wart_stage_2");
        m.put("cocoa_stage0", "textures/blocks/cocoa_stage_0");
        m.put("cocoa_stage1", "textures/blocks/cocoa_stage_1");
        m.put("cocoa_stage2", "textures/blocks/cocoa_stage_2");
        m.put("attached_melon_stem", "textures/blocks/melon_stem_connected");
        m.put("melon_stem", "textures/blocks/melon_stem_disconnected");
        m.put("attached_pumpkin_stem", "textures/blocks/pumpkin_stem_connected");
        m.put("pumpkin_stem", "textures/blocks/pumpkin_stem_disconnected");
        m.put("podzol_top", "textures/blocks/dirt_podzol_top");
        m.put("dirt_path_top", "textures/blocks/grass_path_top");
        m.put("farmland", "textures/blocks/farmland_dry");
        m.put("farmland_moist", "textures/blocks/farmland_wet");
        m.put("red_nether_bricks", "textures/blocks/red_nether_brick");
        // Wood & Stems
        m.put("oak_log", "textures/blocks/log_oak");
        m.put("spruce_log", "textures/blocks/log_spruce");
        m.put("acacia_log", "textures/blocks/log_acacia");
        m.put("jungle_log", "textures/blocks/log_jungle");
        m.put("dark_oak_log", "textures/blocks/log_big_oak");
        m.put("crimson_stem", "textures/blocks/huge_fungus/crimson_log_side");
        m.put("crimson_stem_top", "textures/blocks/huge_fungus/crimson_log_top");
        m.put("stripped_crimson_stem", "textures/blocks/huge_fungus/strd_crimson_stem_side");
        m.put("stripped_crimson_stem_top", "textures/blocks/huge_fungus/stred_crimson_stem_top");
        m.put("warped_stem", "textures/blocks/huge_fungus/warped_stem_side");
        m.put("warped_stem_top", "textures/blocks/huge_fungus/warped_stem_top");
        m.put("stripped_warped_stem", "textures/blocks/huge_fungus/stred_warped_stem_side");
        m.put("stripped_warped_stem_top", "textures/blocks/huge_fungus/strped_warped_stem_top");
        m.put("bamboo_block", "textures/blocks/bamboo_block");
        m.put("bamboo_block_top", "textures/blocks/bamboo_block_top");
        m.put("bamboo_stalk", "textures/blocks/bamboo_stem");
        m.put("bamboo_stage0", "textures/blocks/bamboo_sapling");
        m.put("bamboo_small_leaves", "textures/blocks/bamboo_small_leaf");
        m.put("bamboo_large_leaves", "textures/blocks/bamboo_leaf");
        m.put("bamboo_fence_particle", "textures/blocks/bamboo_fence");
        m.put("bamboo_fence_gate_particle", "textures/blocks/bamboo_fence_gate");
        m.put("dark_oak_log_top", "textures/blocks/log_big_oak_top");
        m.put("dark_oak_planks", "textures/blocks/planks_big_oak");
        m.put("dark_oak_leaves", "textures/blocks/leaves_big_oak");
        m.put("dark_oak_sapling", "textures/blocks/sapling_roofed_oak");
        m.put("oak_trapdoor", "textures/blocks/trapdoor");
        m.put("oak_door_top", "textures/blocks/door_wood_upper");
        m.put("oak_door_bottom", "textures/blocks/door_wood_lower");
        // Flora, Vines, Kelp, Functional & Redstone & Rails
        m.put("big_dripleaf_side", "textures/blocks/big_dripleaf_side1");
        m.put("big_dripleaf_tip", "textures/blocks/big_dripleaf_top");
        m.put("flowering_azalea_leaves", "textures/blocks/azalea_leaves_flowers");
        m.put("dead_bush", "textures/blocks/deadbush");
        m.put("torchflower_crop_stage0", "textures/blocks/torchflower_crop_stage_0");
        m.put("torchflower_crop_stage1", "textures/blocks/torchflower_crop_stage_1");
        m.put("brown_mushroom", "textures/blocks/mushroom_brown");
        m.put("red_mushroom", "textures/blocks/mushroom_red");
        m.put("brown_mushroom_block", "textures/blocks/mushroom_block_skin_brown");
        m.put("red_mushroom_block", "textures/blocks/mushroom_block_skin_red");
        m.put("mushroom_stem", "textures/blocks/mushroom_block_skin_stem");
        m.put("crimson_nylium", "textures/blocks/crimson_nylium_top");
        m.put("warped_nylium", "textures/blocks/warped_nylium_top");
        m.put("cave_vines", "textures/blocks/cave_vines_head");
        m.put("cave_vines_lit", "textures/blocks/cave_vines_head_berries");
        m.put("cave_vines_plant", "textures/blocks/cave_vines_body");
        m.put("cave_vines_plant_lit", "textures/blocks/cave_vines_body_berries");
        m.put("twisting_vines", "textures/blocks/twisting_vines_bottom");
        m.put("twisting_vines_plant", "textures/blocks/twisting_vines_base");
        m.put("kelp", "textures/blocks/kelp_top");
        m.put("kelp_plant", "textures/blocks/kelp_a");
        m.put("dried_kelp_bottom", "textures/blocks/dried_kelp_top");
        m.put("dried_kelp_side", "textures/blocks/dried_kelp_side_a");
        m.put("tall_seagrass_bottom", "textures/blocks/seagrass_doubletall_bottom_a");
        m.put("tall_seagrass_top", "textures/blocks/seagrass_doubletall_top_a");
        m.put("beehive_end", "textures/blocks/beehive_top");
        m.put("blast_furnace_front", "textures/blocks/blast_furnace_front_off");
        m.put("furnace_front", "textures/blocks/furnace_front_off");
        m.put("smoker_front", "textures/blocks/smoker_front_off");
        m.put("dispenser_front", "textures/blocks/dispenser_front_horizontal");
        m.put("dropper_front", "textures/blocks/dropper_front_horizontal");
        m.put("fletching_table_front", "textures/blocks/fletcher_table_front");
        m.put("fletching_table_side", "textures/blocks/fletcher_table_side");
        m.put("fletching_table_top", "textures/blocks/fletcher_table_top");
        m.put("campfire_fire", "textures/blocks/campfire");
        m.put("soul_campfire_fire", "textures/blocks/soul_campfire");
        m.put("chain", "textures/blocks/chain1");
        m.put("composter_compost", "textures/blocks/compost");
        m.put("composter_ready", "textures/blocks/compost_ready");
        m.put("conduit", "textures/blocks/conduit_base");
        m.put("end_portal_frame_eye", "textures/blocks/endframe_eye");
        m.put("end_portal_frame_side", "textures/blocks/endframe_side");
        m.put("end_portal_frame_top", "textures/blocks/endframe_top");
        m.put("honey_block_bottom", "textures/blocks/honey_bottom");
        m.put("honey_block_side", "textures/blocks/honey_side");
        m.put("honey_block_top", "textures/blocks/honey_top");
        m.put("honeycomb_block", "textures/blocks/honeycomb");
        m.put("item_frame", "textures/blocks/itemframe_background");
        m.put("jigsaw_bottom", "textures/blocks/jigsaw_back");
        m.put("jigsaw_top", "textures/blocks/jigsaw_front");
        m.put("lightning_rod_on", "textures/blocks/lightning_rod");
        m.put("observer_back_on", "textures/blocks/observer_back_lit");
        m.put("piston_top", "textures/blocks/piston_top_normal");
        m.put("pointed_dripstone_down_tip_merge", "textures/blocks/pointed_dripstone_down_merge");
        m.put("pointed_dripstone_up_tip_merge", "textures/blocks/pointed_dripstone_up_merge");
        m.put("redstone_dust_dot", "textures/blocks/redstone_dust_cross");
        m.put("redstone_dust_line0", "textures/blocks/redstone_dust_line");
        m.put("redstone_dust_line1", "textures/blocks/redstone_dust_line");
        m.put("redstone_dust_overlay", "textures/blocks/redstone_dust_line");
        m.put("sniffer_egg_slightly_cracked_bottom", "textures/blocks/sniffer_egg_slightly_cracked_down");
        m.put("stonecutter_saw", "textures/blocks/stonecutter2_saw");
        m.put("tripwire", "textures/blocks/trip_wire");
        m.put("tripwire_hook", "textures/blocks/trip_wire_source");
        m.put("turtle_egg", "textures/blocks/turtle_egg_not_cracked");
        m.put("wet_sponge", "textures/blocks/sponge_wet");
        m.put("water_overlay", "textures/blocks/water_still_grey");
        m.put("note_block", "textures/blocks/noteblock");
        m.put("spawner", "textures/blocks/mob_spawner");
        m.put("torch", "textures/blocks/torch_on");
        m.put("redstone_torch", "textures/blocks/redstone_torch_on");
        m.put("redstone_lamp", "textures/blocks/redstone_lamp_off");
        m.put("redstone_lamp_on", "textures/blocks/redstone_lamp_on");
        m.put("repeater", "textures/blocks/repeater_off");
        m.put("comparator", "textures/blocks/comparator_off");
        m.put("powered_rail", "textures/blocks/rail_golden");
        m.put("powered_rail_on", "textures/blocks/rail_golden_powered");
        m.put("detector_rail", "textures/blocks/rail_detector");
        m.put("detector_rail_on", "textures/blocks/rail_detector_powered");
        m.put("activator_rail", "textures/blocks/rail_activator");
        m.put("activator_rail_on", "textures/blocks/rail_activator_powered");
        m.put("rail", "textures/blocks/carried/rail_carried");
        m.put("rail_corner", "textures/blocks/rail_normal_turned");
        m.put("fire_0", "textures/blocks/fire_0");
        m.put("fire_1", "textures/blocks/fire_1");
        m.put("soul_fire_0", "textures/blocks/soul_fire_0");
        m.put("soul_fire_1", "textures/blocks/soul_fire_1");
        m.put("water_still", "textures/blocks/water_still_grey");
        m.put("water_flow", "textures/blocks/water_flow_grey");
        m.put("nether_portal", "textures/blocks/portal");
        m.put("pumpkin_side", "textures/blocks/pumpkin_side");
        m.put("carved_pumpkin", "textures/blocks/pumpkin_face_off");
        m.put("jack_o_lantern", "textures/blocks/pumpkin_face_on");
        m.put("oak_log_top", "textures/blocks/log_oak_top");
        m.put("daylight_detector_inverted_top", "textures/blocks/daylight_detector_inverted_top");
        m.put("sculk_shrieker_can_summon_inner_top", "textures/blocks/scuk_shrieker_can_summon_inner_top");
        m.put("repeating_command_block_conditional", "textures/blocks/repommand_block_conditional_mipmap");
        m.put("repeating_command_block_back", "textures/blocks/repating_command_block_back_mipmap");
        m.put("repeating_command_block_front", "textures/blocks/repting_command_block_front_mipmap");
        m.put("repeating_command_block_side", "textures/blocks/repating_command_block_side_mipmap");
        m.put("command_block_back", "textures/blocks/command_block_back_mipmap");
        m.put("command_block_front", "textures/blocks/command_block_front_mipmap");
        m.put("command_block_side", "textures/blocks/command_block_side_mipmap");
        m.put("command_block_conditional", "textures/blocks/chaommand_block_conditional_mipmap");
        m.put("chain_command_block_back", "textures/blocks/chain_command_block_back_mipmap");
        m.put("chain_command_block_front", "textures/blocks/chain_command_block_front_mipmap");
        m.put("chain_command_block_side", "textures/blocks/chain_command_block_side_mipmap");
        m.put("chain_command_block_conditional", "textures/blocks/chain_command_block_conditional_mipmap");
        return m;
    }

    private static Map<String, String> createItemAliasMap() {
        Map<String, String> m = new HashMap<>();
        m.put("oak_door", "textures/items/door_wood");
        m.put("iron_door", "textures/items/door_iron");
        m.put("spruce_door", "textures/items/door_spruce");
        m.put("birch_door", "textures/items/door_birch");
        m.put("jungle_door", "textures/items/door_jungle");
        m.put("acacia_door", "textures/items/door_acacia");
        m.put("dark_oak_door", "textures/items/door_dark_oak");
        m.put("crimson_door", "textures/items/crimson_door");
        m.put("warped_door", "textures/items/warped_door");
        m.put("golden_apple", "textures/items/apple_golden");
        m.put("golden_carrot", "textures/items/carrot_golden");
        m.put("beef", "textures/items/beef_raw");
        m.put("cooked_beef", "textures/items/beef_cooked");
        m.put("chicken", "textures/items/chicken_raw");
        m.put("cooked_chicken", "textures/items/chicken_cooked");
        m.put("porkchop", "textures/items/porkchop_raw");
        m.put("cooked_porkchop", "textures/items/porkchop_cooked");
        m.put("mutton", "textures/items/mutton_raw");
        m.put("cooked_mutton", "textures/items/mutton_cooked");
        m.put("rabbit", "textures/items/rabbit_raw");
        m.put("cooked_rabbit", "textures/items/rabbit_cooked");
        m.put("cod", "textures/items/fish_raw");
        m.put("cooked_cod", "textures/items/fish_cooked");
        m.put("salmon", "textures/items/fish_salmon_raw");
        m.put("cooked_salmon", "textures/items/fish_salmon_cooked");
        m.put("tropical_fish", "textures/items/fish_clownfish_raw");
        m.put("pufferfish", "textures/items/fish_pufferfish_raw");
        m.put("sugar_cane", "textures/items/reeds");
        m.put("slime_ball", "textures/items/slimeball");
        m.put("clay_ball", "textures/items/clay_ball");
        m.put("redstone", "textures/items/redstone_dust");
        m.put("nether_brick", "textures/items/netherbrick");
        m.put("popped_chorus_fruit", "textures/items/chorus_fruit_popped");
        m.put("melon_slice", "textures/items/melon");
        m.put("glistering_melon_slice", "textures/items/melon_speckled");
        m.put("potion", "textures/items/potion_bottle_drinkable");
        m.put("splash_potion", "textures/items/potion_bottle_splash");
        m.put("lingering_potion", "textures/items/potion_bottle_lingering");
        m.put("potion_overlay", "textures/items/potion_overlay");
        m.put("tipped_arrow_base", "textures/items/tipped_arrow");
        m.put("tipped_arrow_head", "textures/items/tipped_arrow_base");
        m.put("firework_rocket", "textures/items/fireworks");
        m.put("firework_star", "textures/items/fireworks_charge");
        m.put("fire_charge", "textures/items/fireball");
        m.put("totem_of_undying", "textures/items/totem");
        m.put("ender_eye", "textures/items/ender_eye");
        m.put("ink_sac", "textures/items/dye_powder_black");
        m.put("glow_ink_sac", "textures/items/dye_powder_glow");
        m.put("bone_meal", "textures/items/dye_powder_white");
        m.put("cocoa_beans", "textures/items/dye_powder_brown");
        m.put("lapis_lazuli", "textures/items/dye_powder_blue");
        m.put("charcoal", "textures/items/charcoal");
        m.put("emerald", "textures/items/emerald");
        m.put("amethyst_shard", "textures/items/amethyst_shard");
        m.put("spider_eye", "textures/items/spider_eye");
        m.put("fermented_spider_eye", "textures/items/spider_eye_fermented");
        m.put("ghast_tear", "textures/items/ghast_tear");
        m.put("magma_cream", "textures/items/magma_cream");
        m.put("nether_star", "textures/items/nether_star");
        m.put("turtle_helmet", "textures/items/turtle_helmet");
        m.put("scute", "textures/items/turtle_shell_piece");
        m.put("writable_book", "textures/items/book_writable");
        m.put("written_book", "textures/items/book_written");
        m.put("enchanted_book", "textures/items/book_enchanted");
        m.put("book", "textures/items/book_normal");
        m.put("filled_map", "textures/items/map_filled");
        m.put("map", "textures/items/map_empty");
        m.put("Minecart", "textures/items/minecart_normal");
        m.put("minecart", "textures/items/minecart_normal");
        m.put("bucket", "textures/items/bucket_empty");
        m.put("water_bucket", "textures/items/bucket_water");
        m.put("lava_bucket", "textures/items/bucket_lava");
        m.put("milk_bucket", "textures/items/bucket_milk");
        m.put("powder_snow_bucket", "textures/items/bucket_powder_snow");
        m.put("axolotl_bucket", "textures/items/bucket_axolotl");
        m.put("cod_bucket", "textures/items/bucket_cod");
        m.put("salmon_bucket", "textures/items/bucket_salmon");
        m.put("pufferfish_bucket", "textures/items/bucket_pufferfish");
        m.put("tropical_fish_bucket", "textures/items/bucket_tropical");
        m.put("tadpole_bucket", "textures/items/bucket_tadpole");
        m.put("oak_sign", "textures/items/sign");
        m.put("bamboo_raft", "textures/items/bamboo_raft");
        m.put("bamboo_chest_raft", "textures/items/bamboo_chest_raft");
        m.put("nether_wart", "textures/items/nether_wart");
        m.put("sweet_berries", "textures/items/sweet_berries");
        m.put("glow_berries", "textures/items/glow_berries");
        m.put("kelp", "textures/items/kelp");
        m.put("flower_pot", "textures/items/flower_pot");
        m.put("armor_stand", "textures/items/armor_stand");
        m.put("lead", "textures/items/lead");
        m.put("name_tag", "textures/items/name_tag");
        m.put("item_frame", "textures/items/item_frame");
        m.put("glow_item_frame", "textures/items/glow_item_frame");
        m.put("painting", "textures/items/painting");
        m.put("dried_kelp", "textures/items/dried_kelp");
        m.put("wooden_sword", "textures/items/wood_sword");
        m.put("wooden_pickaxe", "textures/items/wood_pickaxe");
        m.put("wooden_axe", "textures/items/wood_axe");
        m.put("wooden_shovel", "textures/items/wood_shovel");
        m.put("wooden_hoe", "textures/items/wood_hoe");
        m.put("golden_sword", "textures/items/gold_sword");
        m.put("golden_pickaxe", "textures/items/gold_pickaxe");
        m.put("golden_axe", "textures/items/gold_axe");
        m.put("golden_shovel", "textures/items/gold_shovel");
        m.put("golden_hoe", "textures/items/gold_hoe");
        m.put("golden_helmet", "textures/items/gold_helmet");
        m.put("golden_chestplate", "textures/items/gold_chestplate");
        m.put("golden_leggings", "textures/items/gold_leggings");
        m.put("golden_boots", "textures/items/gold_boots");
        m.put("golden_horse_armor", "textures/items/gold_horse_armor");
        m.put("iron_horse_armor", "textures/items/iron_horse_armor");
        m.put("diamond_horse_armor", "textures/items/diamond_horse_armor");
        m.put("leather_horse_armor", "textures/items/leather_horse_armor");
        m.put("phantom_membrane", "textures/items/phantom_membrane");
        m.put("baked_potato", "textures/items/potato_baked");
        m.put("poisonous_potato", "textures/items/potato_poisonous");
        m.put("beetroot_seeds", "textures/items/seeds_beetroot");
        m.put("melon_seeds", "textures/items/seeds_melon");
        m.put("pumpkin_seeds", "textures/items/seeds_pumpkin");
        m.put("wheat_seeds", "textures/items/seeds_wheat");
        m.put("bow", "textures/items/bow_standby");
        m.put("fishing_rod", "textures/items/fishing_rod_uncast");
        m.put("dark_oak_sign", "textures/items/sign_darkoak");
        m.put("dragon_breath", "textures/items/dragons_breath");
        m.put("glass_bottle", "textures/items/potion_bottle_empty");
        m.put("heart_of_the_sea", "textures/items/heartofthesea_closed");
        m.put("nautilus_shell", "textures/items/nautilus");
        m.put("pitcher_plant", "textures/blocks/carried/pitcher_crop_carried");
        m.put("pointed_dripstone", "textures/blocks/carried/dripstone_carried");
        m.put("seagrass", "textures/blocks/seagrass_carried");
        m.put("spectral_arrow", "textures/items/arrow");
        m.put("spyglass_model", "textures/entity/spyglass");
        m.put("structure_void", "textures/blocks/structure_void");
        m.put("barrier", "textures/blocks/barrier");
        return m;
    }

    private static Map<String, String> createEntityAliasMap() {
        Map<String, String> m = new HashMap<>();
        m.put("textures/entity/armor_stand", "textures/entity/armorstand/wood");
        m.put("textures/entity/bed/silver", "textures/entity/bed/light_gray");
        m.put("textures/entity/bell/bell", "textures/entity/bell/bell_body");
        m.put("textures/entity/cat/allblackcat", "textures/entity/cat/all_black");
        m.put("textures/entity/cat/blackcat", "textures/entity/cat/black");
        m.put("textures/entity/cat/britishshorthair", "textures/entity/cat/british_shorthair");
        m.put("textures/entity/cow/mooshroom", "textures/entity/cow/red_mooshroom");
        m.put("textures/entity/dragon/dragon", "textures/entity/enderdragon/dragon");
        m.put("textures/entity/endercrystal/endercrystal", "textures/entity/end_crystal/end_crystal");
        m.put("textures/entity/endercrystal/endercrystal_beam", "textures/entity/end_crystal/end_crystal_beam");
        m.put("textures/entity/fishhook", "textures/entity/fishing_hook");
        m.put("textures/entity/fox/arctic_fox", "textures/entity/fox/snow_fox");
        m.put("textures/entity/zoglin/zoglin", "textures/entity/hoglin/zoglin");
        m.put("textures/entity/illager/fangs", "textures/entity/illager/evoker_fangs");
        m.put("textures/entity/pillager", "textures/entity/illager/pillager");
        m.put("textures/entity/vex/vex", "textures/entity/illager/vex");
        m.put("textures/entity/vex/vex_charging", "textures/entity/illager/vex_charging");
        m.put("textures/entity/vindicator", "textures/entity/illager/vindicator");
        m.put("textures/entity/iron_golem/cracked_high", "textures/entity/iron_golem/iron_golem_crackiness_high");
        m.put("textures/entity/iron_golem/cracked_medium", "textures/entity/iron_golem/iron_golem_crackiness_medium");
        m.put("textures/entity/iron_golem/cracked_low", "textures/entity/iron_golem/iron_golem_crackiness_low");
        m.put("textures/entity/llama/llama_brown", "textures/entity/llama/brown");
        m.put("textures/entity/llama/llama_creamy", "textures/entity/llama/creamy");
        m.put("textures/entity/llama/llama_gray", "textures/entity/llama/gray");
        m.put("textures/entity/llama/llama_white", "textures/entity/llama/white");
        m.put("textures/entity/llama/decor/trader_llama_decor", "textures/entity/llama/decor/trader_llama");
        m.put("textures/entity/panda/panda_aggressive", "textures/entity/panda/aggressive_panda");
        m.put("textures/entity/panda/panda_brown", "textures/entity/panda/brown_panda");
        m.put("textures/entity/panda/panda_lazy", "textures/entity/panda/lazy_panda");
        m.put("textures/entity/panda/panda_playful", "textures/entity/panda/playful_panda");
        m.put("textures/entity/panda/panda_sneezy", "textures/entity/panda/weak_panda");
        m.put("textures/entity/panda/panda_worried", "textures/entity/panda/worried_panda");
        m.put("textures/entity/piglin/zombie_piglin", "textures/entity/piglin/zombified_piglin");
        m.put("textures/entity/polarbear", "textures/entity/bear/polarbear");
        m.put("textures/entity/rabbit/blackrabbit", "textures/entity/rabbit/black");
        m.put("textures/entity/sea_turtle", "textures/entity/turtle/big_sea_turtle");
        m.put("textures/entity/shulker/shulker_silver", "textures/entity/shulker/shulker_light_gray");
        m.put("textures/entity/shulker/shulker_undyed", "textures/entity/shulker/shulker");
        m.put("textures/entity/squid", "textures/entity/squid/squid");
        m.put("textures/entity/strider/strider_saddled", "textures/entity/strider/strider_saddle");
        m.put("textures/entity/wither_boss/wither", "textures/entity/wither/wither");
        m.put("textures/entity/wither_boss/wither_invulnerable", "textures/entity/wither/wither_invulnerable");
        m.put("textures/entity/wither_boss/wither_armor_blue", "textures/entity/wither/wither_armor");
        m.put("textures/entity/alex", "textures/entity/player/slim/alex");
        m.put("textures/entity/steve", "textures/entity/player/wide/steve");
        return m;
    }
}
