package ans.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Converts Bedrock {@code sounds/*.ogg} and {@code sounds/sound_definitions.json}
 * into valid Java 1.20.1 {@code assets/minecraft/sounds/**},
 * {@code assets/minecraft/sounds.json}, and {@code assets/oreville_ans/sounds.json}.
 */
public final class SoundPackConverter {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    public record SoundResult(
        Map<String, byte[]> oggFiles,
        byte[] minecraftSoundsJson,
        byte[] orevilleSoundsJson,
        int soundEventCount,
        int missingReferencedOggCount
    ) {
    }

    public static SoundResult convert(ZipFile mcpack, ZipFile vanillaJar) throws IOException {
        Map<String, byte[]> oggFiles = new HashMap<>();
        Set<String> availableOggs = new HashSet<>();

        // Collect all vanilla .ogg paths from minecraft-merged.jar (under assets/minecraft/sounds/)
        if (vanillaJar != null) {
            Enumeration<? extends ZipEntry> vanillaEntries = vanillaJar.entries();
            while (vanillaEntries.hasMoreElements()) {
                ZipEntry e = vanillaEntries.nextElement();
                String name = e.getName();
                if (name.startsWith("assets/minecraft/sounds/") && name.endsWith(".ogg")) {
                    String rel = name.substring("assets/minecraft/sounds/".length(), name.length() - 4)
                        .toLowerCase(Locale.ROOT);
                    availableOggs.add(rel);
                }
            }
        }

        // Copy all 958 .ogg files from animation mod.mcpack (preferring SP2 if any overrides exist)
        Enumeration<? extends ZipEntry> entries = mcpack.entries();
        Map<String, ZipEntry> selectedOggs = new HashMap<>();
        while (entries.hasMoreElements()) {
            ZipEntry e = entries.nextElement();
            if (e.isDirectory()) {
                continue;
            }
            String full = e.getName();
            if (full.startsWith("subpacks/SP0/") || full.startsWith("subpacks/SP1/")) {
                continue;
            }
            String relPath;
            boolean isSp2 = false;
            if (full.startsWith("subpacks/SP2/")) {
                relPath = full.substring("subpacks/SP2/".length());
                isSp2 = true;
            } else {
                relPath = full;
            }
            if (!relPath.startsWith("sounds/") || !relPath.toLowerCase(Locale.ROOT).endsWith(".ogg")) {
                continue;
            }
            String normRel = relPath.toLowerCase(Locale.ROOT);
            if (isSp2 || !selectedOggs.containsKey(normRel)) {
                selectedOggs.put(normRel, e);
            }
        }

        for (Map.Entry<String, ZipEntry> entry : selectedOggs.entrySet()) {
            String packOggPath = entry.getKey(); // e.g. sounds/oreville/ans/aaa.ogg
            byte[] bytes = readAll(mcpack, entry.getValue());
            String javaOutPath = "assets/minecraft/" + packOggPath;
            oggFiles.put(javaOutPath, bytes);
            String soundIdPath = packOggPath.substring("sounds/".length(), packOggPath.length() - 4);
            availableOggs.add(soundIdPath);
        }

        JsonObject minecraftSounds = new JsonObject();
        JsonObject orevilleSounds = new JsonObject();
        int missingRefs = 0;

        ZipEntry defEntry = mcpack.getEntry("subpacks/SP2/sounds/sound_definitions.json");
        if (defEntry == null) {
            defEntry = mcpack.getEntry("sounds/sound_definitions.json");
        }
        if (defEntry != null) {
            String raw = new String(readAll(mcpack, defEntry), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            JsonObject defs = root.has("sound_definitions") && root.get("sound_definitions").isJsonObject()
                ? root.getAsJsonObject("sound_definitions")
                : root;

            for (Map.Entry<String, JsonElement> ev : defs.entrySet()) {
                if (!ev.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject bedrockEv = ev.getValue().getAsJsonObject();
                if (!bedrockEv.has("sounds") || !bedrockEv.get("sounds").isJsonArray()) {
                    continue;
                }

                JsonArray javaSounds = new JsonArray();
                for (JsonElement sEl : bedrockEv.getAsJsonArray("sounds")) {
                    String soundName = null;
                    JsonObject sObj = null;
                    if (sEl.isJsonPrimitive()) {
                        soundName = sEl.getAsString();
                    } else if (sEl.isJsonObject()) {
                        sObj = sEl.getAsJsonObject();
                        if (sObj.has("name") && sObj.get("name").isJsonPrimitive()) {
                            soundName = sObj.get("name").getAsString();
                        }
                    }
                    if (soundName == null || soundName.isBlank()) {
                        continue;
                    }
                    String normalized = normalizeSoundPath(soundName);
                    if (!availableOggs.contains(normalized)) {
                        missingRefs++;
                        // Still skip nonexistent sound refs so Java does not emit missing sound warnings
                        continue;
                    }

                    if (sObj == null) {
                        javaSounds.add("minecraft:" + normalized);
                    } else {
                        JsonObject outSound = new JsonObject();
                        outSound.addProperty("name", "minecraft:" + normalized);
                        if (sObj.has("volume") && sObj.get("volume").isJsonPrimitive()) {
                            outSound.addProperty("volume", sObj.get("volume").getAsFloat());
                        }
                        if (sObj.has("pitch") && sObj.get("pitch").isJsonPrimitive()) {
                            outSound.addProperty("pitch", sObj.get("pitch").getAsFloat());
                        }
                        if (sObj.has("weight") && sObj.get("weight").isJsonPrimitive()) {
                            outSound.addProperty("weight", sObj.get("weight").getAsInt());
                        }
                        if (sObj.has("stream") && sObj.get("stream").isJsonPrimitive()) {
                            outSound.addProperty("stream", sObj.get("stream").getAsBoolean());
                        }
                        if (bedrockEv.has("max_distance") && bedrockEv.get("max_distance").isJsonPrimitive()) {
                            int att = Math.max(1, Math.round(bedrockEv.get("max_distance").getAsFloat()));
                            outSound.addProperty("attenuation_distance", att);
                        }
                        javaSounds.add(outSound);
                    }
                }

                if (javaSounds.isEmpty()) {
                    continue;
                }
                JsonObject eventObj = new JsonObject();
                eventObj.add("sounds", javaSounds);

                String rawKey = ev.getKey().toLowerCase(Locale.ROOT);
                if (rawKey.startsWith("oreville_ans:")) {
                    String subKey = rawKey.substring("oreville_ans:".length());
                    orevilleSounds.add(subKey, eventObj);
                } else {
                    String mapped = mapVanillaSoundKey(rawKey);
                    minecraftSounds.add(mapped, eventObj);
                }
            }
        }

        byte[] mcBytes = GSON.toJson(minecraftSounds).getBytes(StandardCharsets.UTF_8);
        byte[] ovBytes = GSON.toJson(orevilleSounds).getBytes(StandardCharsets.UTF_8);
        int totalEvents = minecraftSounds.size() + orevilleSounds.size();
        return new SoundResult(oggFiles, mcBytes, ovBytes, totalEvents, missingRefs);
    }

    private static String normalizeSoundPath(String raw) {
        String p = raw.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (p.startsWith("sounds/")) {
            p = p.substring("sounds/".length());
        }
        if (p.endsWith(".ogg")) {
            p = p.substring(0, p.length() - 4);
        }
        // Known Bedrock -> Java vanilla sound folder differences
        if (p.startsWith("mob/bogged/")) {
            // Bogged wasn't in 1.20.1 vanilla; falls back if not present
        }
        return p;
    }

    private static String mapVanillaSoundKey(String key) {
        if (key.startsWith("mob.")) {
            return "entity." + key.substring("mob.".length());
        }
        return key;
    }

    private static byte[] readAll(ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream is = zip.getInputStream(entry)) {
            return is.readAllBytes();
        }
    }
}
