package ans.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * End-to-end converter producing a self-contained Java Edition 1.20.1 resource pack zip
 * from {@code animation mod.mcpack} (Full Experience: {@code root/} overlaid with {@code subpacks/SP2/}).
 */
public final class ActionsAndStuffPackConverter {

    private ActionsAndStuffPackConverter() {
    }

    public static void main(String[] args) throws IOException {
        Path mcpackPath = Paths.get(args.length > 0 ? args[0] : "C:/Users/jacqu/Downloads/animation mod.mcpack");
        Path primaryOut = Paths.get(args.length > 1 ? args[1] : "C:/Users/jacqu/Downloads/Actions-And-Stuff-Java-1.20.1.zip");
        Path secondaryOut = args.length > 2 ? Paths.get(args[2]) : Paths.get("run/resourcepacks/Actions-And-Stuff-Java-1.20.1.zip");
        Path vanillaJarPath = args.length > 3 ? Paths.get(args[3])
            : Paths.get("C:/Users/jacqu/.gradle/caches/fabric-loom/1.20.1/minecraft-merged.jar");

        System.out.println("=== Converting Actions & Stuff v1.1.68 (.mcpack) -> Java 1.20.1 Resource Pack ===");
        System.out.println("Source:  " + mcpackPath);
        System.out.println("Target:  " + primaryOut);
        System.out.println("Vanilla: " + vanillaJarPath);

        if (!Files.exists(mcpackPath)) {
            throw new IOException("Missing source mcpack: " + mcpackPath);
        }

        try (ZipFile mcpack = new ZipFile(mcpackPath.toFile());
             ZipFile vanillaJar = Files.exists(vanillaJarPath) ? new ZipFile(vanillaJarPath.toFile()) : null) {

            // 1. Load flipbook definitions
            FlipbookMcmetaGenerator flipbooks = new FlipbookMcmetaGenerator();
            ZipEntry fbEntry = mcpack.getEntry("subpacks/SP2/textures/flipbook_textures.json");
            if (fbEntry == null) {
                fbEntry = mcpack.getEntry("textures/flipbook_textures.json");
            }
            if (fbEntry != null) {
                flipbooks.load(readAll(mcpack, fbEntry));
            }

            // 2. Convert & map all textures + .tga + atlases + spawn eggs + .mcmeta
            BedrockToJavaTextureMapper.TextureReport texReport =
                BedrockToJavaTextureMapper.convertAll(mcpack, vanillaJar, flipbooks);

            // 3. Convert sounds & sound definitions
            SoundPackConverter.SoundResult soundResult = SoundPackConverter.convert(mcpack, vanillaJar);

            // 4. Assemble final resource pack map (root overlaid with SP2 for bedrock runtime assets)
            Map<String, byte[]> zipContents = new LinkedHashMap<>();

            // pack.mcmeta (Java 1.20.1 = pack_format 15)
            String packMcmeta = "{\"pack\":{\"pack_format\":15,\"description\":\"Actions & Stuff v1.1.68 (SP2 Java 1.20.1 Port by Oreville Studios)\"}}\n";
            zipContents.put("pack.mcmeta", packMcmeta.getBytes(StandardCharsets.UTF_8));

            // pack.png
            ZipEntry iconEntry = mcpack.getEntry("pack_icon.png");
            if (iconEntry != null) {
                zipContents.put("pack.png", readAll(mcpack, iconEntry));
            }

            // Texture files & mcmeta & spawn egg models
            zipContents.putAll(texReport.files());

            // Sound files & sounds.json
            zipContents.putAll(soundResult.oggFiles());
            zipContents.put("assets/minecraft/sounds.json", soundResult.minecraftSoundsJson());
            zipContents.put("assets/oreville_ans/sounds.json", soundResult.orevilleSoundsJson());

            // Copy all Bedrock runtime JSON/lang/material data (root + SP2 overlay) under assets/minecraft/bedrock/
            copyBedrockRuntimeData(mcpack, zipContents);

            // Copy splashes if present
            ZipEntry splashes = mcpack.getEntry("splashes.json");
            if (splashes != null) {
                convertSplashes(readAll(mcpack, splashes), zipContents);
            }

            // Write primary output zip cleanly via temp file
            Files.createDirectories(primaryOut.getParent());
            Path tmpOut = Files.createTempFile("ans_java_pack_", ".zip");
            try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(tmpOut)))) {
                for (Map.Entry<String, byte[]> entry : zipContents.entrySet()) {
                    ZipEntry ze = new ZipEntry(entry.getKey());
                    zos.putNextEntry(ze);
                    zos.write(entry.getValue());
                    zos.closeEntry();
                }
            }
            Files.move(tmpOut, primaryOut, StandardCopyOption.REPLACE_EXISTING);

            if (secondaryOut != null && !secondaryOut.equals(primaryOut)) {
                Files.createDirectories(secondaryOut.getParent());
                Files.copy(primaryOut, secondaryOut, StandardCopyOption.REPLACE_EXISTING);
            }

            printReport(texReport, soundResult, zipContents.size(), primaryOut);

            if (!texReport.decodeErrors().isEmpty()) {
                throw new IOException("Encountered texture decode errors: " + texReport.decodeErrors().size());
            }
        }
    }

    private static void copyBedrockRuntimeData(ZipFile mcpack, Map<String, byte[]> zipContents) throws IOException {
        Map<String, ZipEntry> sp2Overlay = new LinkedHashMap<>();
        Enumeration<? extends ZipEntry> en = mcpack.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            if (e.isDirectory()) continue;
            String n = e.getName();
            if (n.startsWith("subpacks/SP0/") || n.startsWith("subpacks/SP1/")) continue;
            boolean isSp2 = n.startsWith("subpacks/SP2/");
            String rel = isSp2 ? n.substring("subpacks/SP2/".length()) : n;
            if (rel.startsWith("textures/") || rel.startsWith("sounds/") || rel.equals("pack_icon.png")) {
                continue;
            }
            if (rel.startsWith("models/") || rel.startsWith("animations/") || rel.startsWith("animation_controllers/")
                || rel.startsWith("render_controllers/") || rel.startsWith("entity/") || rel.startsWith("attachables/")
                || rel.startsWith("particles/") || rel.startsWith("atmospherics/") || rel.startsWith("biomes/")
                || rel.startsWith("fogs/") || rel.startsWith("water/") || rel.startsWith("lighting/")
                || rel.startsWith("color_grading/") || rel.startsWith("materials/") || rel.startsWith("texts/")
                || rel.equals("manifest.json") || rel.equals("blocks.json") || rel.equals("sounds.json")) {
                if (isSp2) {
                    sp2Overlay.put(rel, e);
                } else {
                    zipContents.put("assets/minecraft/bedrock/" + rel, readAll(mcpack, e));
                }
            }
        }
        // Apply SP2 overrides cleanly over root files
        for (Map.Entry<String, ZipEntry> e : sp2Overlay.entrySet()) {
            zipContents.put("assets/minecraft/bedrock/" + e.getKey(), readAll(mcpack, e.getValue()));
        }
        // Also keep key texture JSON metadata under bedrock/
        for (String extra : new String[]{
            "textures/terrain_texture.json", "textures/flipbook_textures.json",
            "textures/item_texture.json", "sounds/sound_definitions.json"
        }) {
            ZipEntry ze = mcpack.getEntry("subpacks/SP2/" + extra);
            if (ze == null) ze = mcpack.getEntry(extra);
            if (ze != null) {
                zipContents.put("assets/minecraft/bedrock/" + extra, readAll(mcpack, ze));
            }
        }
    }

    private static void convertSplashes(byte[] raw, Map<String, byte[]> zipContents) {
        try {
            String txt = new String(raw, StandardCharsets.UTF_8);
            JsonObject obj = com.google.gson.JsonParser.parseString(txt).getAsJsonObject();
            if (obj.has("splashes") && obj.get("splashes").isJsonArray()) {
                JsonArray arr = obj.getAsJsonArray("splashes");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < arr.size(); i++) {
                    sb.append(arr.get(i).getAsString()).append('\n');
                }
                zipContents.put("assets/minecraft/texts/splashes.txt", sb.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }

    private static void printReport(
        BedrockToJavaTextureMapper.TextureReport tex,
        SoundPackConverter.SoundResult snd,
        int totalEntries,
        Path outZip
    ) throws IOException {
        long sizeMb = Files.size(outZip) / (1024 * 1024);
        System.out.println("Output entries: " + totalEntries + " (" + sizeMb + " MB)");
        System.out.println("Source textures: " + tex.totalSourceTextures() + " (PNG=" + tex.pngCount() + ", TGA=" + tex.convertedTgaCount() + ")");
        System.out.println("Flipbook .mcmeta generated: " + tex.generatedMcmetaCount());
        System.out.println("Sliced atlas frames (clock/compass/paintings): " + tex.slicedAtlasSprites());
        System.out.println("Spawn egg custom item models: " + tex.spawnEggModelsGenerated());
        System.out.println("Vanilla 1.20.1 blocks mapped: " + tex.mappedVanillaBlocks() + " / " + tex.totalVanillaBlocks()
            + " (unmapped=" + tex.unmappedVanillaBlocks().size() + ")");
        System.out.println("Vanilla 1.20.1 items mapped:  " + tex.mappedVanillaItems() + " / " + tex.totalVanillaItems()
            + " (unmapped=" + tex.unmappedVanillaItems().size() + ")");
        System.out.println("Sounds copied (.ogg): " + snd.oggFiles().size() + ", sound events: " + snd.soundEventCount());
        System.out.println("Decode errors: " + tex.decodeErrors().size());
        if (!tex.decodeErrors().isEmpty()) {
            tex.decodeErrors().forEach(err -> System.err.println("  ERR: " + err));
        }
        System.out.println("Sample unmapped blocks (first 80): " + tex.unmappedVanillaBlocks().subList(0, Math.min(80, tex.unmappedVanillaBlocks().size())));
        System.out.println("Sample unmapped items  (first 80): " + tex.unmappedVanillaItems().subList(0, Math.min(80, tex.unmappedVanillaItems().size())));
    }

    private static byte[] readAll(ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream is = zip.getInputStream(entry)) {
            return is.readAllBytes();
        }
    }
}
