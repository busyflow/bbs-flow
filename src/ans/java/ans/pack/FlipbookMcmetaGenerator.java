package ans.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Generates validated Java 1.20.1 {@code .png.mcmeta} flipbook animation metadata
 * from Bedrock's {@code subpacks/SP2/textures/flipbook_textures.json} and strips
 * out-of-range frames against actual image dimensions.
 */
public final class FlipbookMcmetaGenerator {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public record FlipbookDef(String bedrockTexture, String atlasTile, int atlasIndex,
                              int ticksPerFrame, boolean interpolate, int[] frames) {
    }

    private final Map<String, FlipbookDef> byBedrockPath = new HashMap<>();

    public void load(byte[] flipbookJsonBytes) {
        if (flipbookJsonBytes == null || flipbookJsonBytes.length == 0) {
            return;
        }
        String raw = new String(flipbookJsonBytes, StandardCharsets.UTF_8);
        JsonElement root = JsonParser.parseString(raw);
        if (!root.isJsonArray()) {
            return;
        }
        for (JsonElement el : root.getAsJsonArray()) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject obj = el.getAsJsonObject();
            String tex = getString(obj, "flipbook_texture", "");
            if (tex.isEmpty()) {
                continue;
            }
            String norm = normalizeTextureKey(tex);
            String tile = getString(obj, "atlas_tile", "");
            int atlasIndex = obj.has("atlas_index") && obj.get("atlas_index").isJsonPrimitive()
                ? obj.get("atlas_index").getAsInt() : 0;
            int ticks = obj.has("ticks_per_frame") && obj.get("ticks_per_frame").isJsonPrimitive()
                ? Math.max(1, obj.get("ticks_per_frame").getAsInt()) : 1;
            boolean blend = obj.has("blend_frames") && obj.get("blend_frames").isJsonPrimitive()
                && obj.get("blend_frames").getAsBoolean();

            int[] frames = null;
            if (obj.has("frames") && obj.get("frames").isJsonArray()) {
                JsonArray arr = obj.getAsJsonArray("frames");
                frames = new int[arr.size()];
                for (int i = 0; i < arr.size(); i++) {
                    frames[i] = Math.max(0, arr.get(i).getAsInt());
                }
            }

            FlipbookDef def = new FlipbookDef(norm, tile, atlasIndex, ticks, blend, frames);
            // Prefer atlasIndex == 0 as primary block texture animation if multiple variants exist.
            FlipbookDef prev = byBedrockPath.get(norm);
            if (prev == null || atlasIndex == 0) {
                byBedrockPath.put(norm, def);
            }
            // Also alias without numeric variant suffix when atlasIndex == 0 (e.g. diamond_ore_0 -> diamond_ore)
            if (atlasIndex == 0 && norm.endsWith("_0")) {
                byBedrockPath.putIfAbsent(norm.substring(0, norm.length() - 2), def);
            }
        }

        // Alias known typos in flipbook_textures.json
        aliasIfPresent("textures/blocks/repating_command_block_conditional", "textures/blocks/repeating_command_block_conditional");
        aliasIfPresent("textures/blocks/scuk_shrieker_can_summon_inner_top", "textures/blocks/sculk_shrieker_can_summon_inner_top");
        aliasIfPresent("textures/blocks/huge_fungus/crimson_log_side", "textures/blocks/crimson_stem");
        aliasIfPresent("textures/blocks/huge_fungus/warped_stem_side", "textures/blocks/warped_stem");
    }

    private void aliasIfPresent(String existing, String alias) {
        FlipbookDef def = byBedrockPath.get(existing);
        if (def != null) {
            byBedrockPath.putIfAbsent(alias, def);
        }
    }

    public FlipbookDef find(String bedrockPathNoExt) {
        return byBedrockPath.get(normalizeTextureKey(bedrockPathNoExt));
    }

    public int size() {
        return byBedrockPath.size();
    }

    /**
     * Generates valid {@code .png.mcmeta} bytes if {@code def} applies OR if the converted PNG
     * under {@code textures/block/} or {@code textures/item/} is a vertical animation strip
     * ({@code height > width && height % width == 0}). Returns {@code null} for static textures
     * or non-square strips where no animation applies.
     */
    public byte[] buildMcmetaIfAnimated(String javaPngPath, FlipbookDef def, byte[] pngBytes, byte[] vanillaMcmetaBytes) {
        int[] dims = readPngDimensions(pngBytes);
        if (dims == null) {
            return null;
        }
        int width = dims[0];
        int height = dims[1];
        if (width <= 0 || height <= width || (height % width) != 0) {
            // Single-frame image (e.g., 16x16 or non-strip); never emit animation mcmeta for it.
            return null;
        }
        int frameCount = height / width;

        boolean isAtlasSprite = javaPngPath.startsWith("assets/minecraft/textures/block/")
            || javaPngPath.startsWith("assets/minecraft/textures/item/")
            || javaPngPath.startsWith("assets/minecraft/textures/painting/")
            || javaPngPath.startsWith("assets/minecraft/textures/particle/");

        if (def == null && !isAtlasSprite) {
            return null;
        }

        // Preserve vanilla metadata properties like frametime/interpolate if no explicit Bedrock flipbook def exists
        if (def == null) {
            if (vanillaMcmetaBytes != null) {
                try {
                    JsonObject vanillaRoot = JsonParser.parseString(new String(vanillaMcmetaBytes, StandardCharsets.UTF_8)).getAsJsonObject();
                    if (vanillaRoot.has("animation") && vanillaRoot.get("animation").isJsonObject()) {
                        JsonObject anim = vanillaRoot.getAsJsonObject("animation").deepCopy();
                        if (anim.has("frames") && anim.get("frames").isJsonArray()) {
                            JsonArray clamped = clampFrames(anim.getAsJsonArray("frames"), frameCount);
                            if (clamped.isEmpty()) {
                                anim.remove("frames");
                            } else {
                                anim.add("frames", clamped);
                            }
                        }
                        JsonObject out = new JsonObject();
                        out.add("animation", anim);
                        return GSON.toJson(out).getBytes(StandardCharsets.UTF_8);
                    }
                } catch (Exception ignored) {
                    // Fall through to default vertical strip animation
                }
            }
            // In Java 1.20.1, any vertical strip in block/ or item/ NEEDS animation mcmeta, else it's invalid/squished.
            if (javaPngPath.startsWith("assets/minecraft/textures/block/")
                || javaPngPath.startsWith("assets/minecraft/textures/item/")) {
                JsonObject anim = new JsonObject();
                anim.addProperty("frametime", 2);
                JsonObject out = new JsonObject();
                out.add("animation", anim);
                return GSON.toJson(out).getBytes(StandardCharsets.UTF_8);
            }
            return null;
        }

        JsonObject anim = new JsonObject();
        anim.addProperty("frametime", Math.max(1, def.ticksPerFrame()));
        if (def.interpolate()) {
            anim.addProperty("interpolate", true);
        }
        if (def.frames() != null && def.frames().length > 0) {
            JsonArray arr = new JsonArray();
            for (int f : def.frames()) {
                int clamped = Math.min(Math.max(0, f), frameCount - 1);
                arr.add(clamped);
            }
            if (!arr.isEmpty()) {
                anim.add("frames", arr);
            }
        }
        JsonObject out = new JsonObject();
        out.add("animation", anim);
        return GSON.toJson(out).getBytes(StandardCharsets.UTF_8);
    }

    private static JsonArray clampFrames(JsonArray orig, int frameCount) {
        JsonArray out = new JsonArray();
        for (JsonElement el : orig) {
            if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
                int idx = el.getAsInt();
                if (idx >= 0 && idx < frameCount) {
                    out.add(idx);
                }
            } else if (el.isJsonObject()) {
                JsonObject copy = el.getAsJsonObject().deepCopy();
                if (copy.has("index")) {
                    int idx = copy.get("index").getAsInt();
                    if (idx >= 0 && idx < frameCount) {
                        out.add(copy);
                    }
                }
            }
        }
        return out;
    }

    public static int[] readPngDimensions(byte[] pngBytes) {
        if (pngBytes == null || pngBytes.length < 24) {
            return null;
        }
        if ((pngBytes[0] & 0xFF) == 0x89 && pngBytes[1] == 'P' && pngBytes[2] == 'N' && pngBytes[3] == 'G') {
            int w = ((pngBytes[16] & 0xFF) << 24) | ((pngBytes[17] & 0xFF) << 16)
                | ((pngBytes[18] & 0xFF) << 8) | (pngBytes[19] & 0xFF);
            int h = ((pngBytes[20] & 0xFF) << 24) | ((pngBytes[21] & 0xFF) << 16)
                | ((pngBytes[22] & 0xFF) << 8) | (pngBytes[23] & 0xFF);
            return new int[]{w, h};
        }
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(pngBytes));
            if (img != null) {
                return new int[]{img.getWidth(), img.getHeight()};
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private static String normalizeTextureKey(String path) {
        String p = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (p.startsWith("subpacks/sp2/")) {
            p = p.substring("subpacks/sp2/".length());
        }
        if (p.endsWith(".png")) {
            p = p.substring(0, p.length() - 4);
        } else if (p.endsWith(".tga")) {
            p = p.substring(0, p.length() - 4);
        }
        return p;
    }

    private static String getString(JsonObject obj, String key, String fallback) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsString();
        }
        return fallback;
    }
}
