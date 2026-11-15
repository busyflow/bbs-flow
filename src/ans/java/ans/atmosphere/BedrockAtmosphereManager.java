package ans.atmosphere;

import ans.bedrock.BedrockPackManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Loads and evaluates all Actions & Stuff SP2 atmospherics, client biome colors,
 * fog settings, water appearances, lighting curves, and colormaps for Java 1.20.1.
 */
public final class BedrockAtmosphereManager {

    public static final class ColorCurve {
        public final float[] constantRgb; // null if keyframed, each component 0..1
        public final TreeMap<Float, float[]> keyframes = new TreeMap<>();

        public ColorCurve(float[] constantRgb) {
            this.constantRgb = constantRgb;
        }

        public ColorCurve() {
            this.constantRgb = null;
        }

        public float[] sample(float skyAngle, float[] out) {
            if (constantRgb != null) {
                out[0] = constantRgb[0];
                out[1] = constantRgb[1];
                out[2] = constantRgb[2];
                return out;
            }
            if (keyframes.isEmpty()) {
                out[0] = out[1] = out[2] = 1f;
                return out;
            }
            if (keyframes.size() == 1) {
                float[] v = keyframes.firstEntry().getValue();
                out[0] = v[0];
                out[1] = v[1];
                out[2] = v[2];
                return out;
            }
            float t = wrap01(skyAngle);
            Map.Entry<Float, float[]> floor = keyframes.floorEntry(t);
            Map.Entry<Float, float[]> ceil = keyframes.ceilingEntry(t);
            float t0, t1;
            float[] v0, v1;
            if (floor == null) {
                // Wrap from last keyframe across 1.0 -> first keyframe
                Map.Entry<Float, float[]> last = keyframes.lastEntry();
                Map.Entry<Float, float[]> first = keyframes.firstEntry();
                t0 = last.getKey() - 1f;
                v0 = last.getValue();
                t1 = first.getKey();
                v1 = first.getValue();
            } else if (ceil == null) {
                Map.Entry<Float, float[]> first = keyframes.firstEntry();
                t0 = floor.getKey();
                v0 = floor.getValue();
                t1 = first.getKey() + 1f;
                v1 = first.getValue();
            } else if (floor.getKey().equals(ceil.getKey())) {
                float[] v = floor.getValue();
                out[0] = v[0];
                out[1] = v[1];
                out[2] = v[2];
                return out;
            } else {
                t0 = floor.getKey();
                v0 = floor.getValue();
                t1 = ceil.getKey();
                v1 = ceil.getValue();
            }
            float span = t1 - t0;
            float alpha = span > 1e-5f ? Math.max(0f, Math.min(1f, (t - t0) / span)) : 0f;
            out[0] = v0[0] + (v1[0] - v0[0]) * alpha;
            out[1] = v0[1] + (v1[1] - v0[1]) * alpha;
            out[2] = v0[2] + (v1[2] - v0[2]) * alpha;
            return out;
        }
    }

    public static final class ScalarCurve {
        public final float constantValue;
        public final boolean isConstant;
        public final TreeMap<Float, Float> keyframes = new TreeMap<>();

        public ScalarCurve(float constantValue) {
            this.constantValue = constantValue;
            this.isConstant = true;
        }

        public ScalarCurve() {
            this.constantValue = 0f;
            this.isConstant = false;
        }

        public float sample(float skyAngle) {
            if (isConstant) return constantValue;
            if (keyframes.isEmpty()) return 0f;
            if (keyframes.size() == 1) return keyframes.firstEntry().getValue();
            float t = wrap01(skyAngle);
            Map.Entry<Float, Float> floor = keyframes.floorEntry(t);
            Map.Entry<Float, Float> ceil = keyframes.ceilingEntry(t);
            float t0, t1, v0, v1;
            if (floor == null) {
                Map.Entry<Float, Float> last = keyframes.lastEntry();
                Map.Entry<Float, Float> first = keyframes.firstEntry();
                t0 = last.getKey() - 1f;
                v0 = last.getValue();
                t1 = first.getKey();
                v1 = first.getValue();
            } else if (ceil == null) {
                Map.Entry<Float, Float> first = keyframes.firstEntry();
                t0 = floor.getKey();
                v0 = floor.getValue();
                t1 = first.getKey() + 1f;
                v1 = first.getValue();
            } else if (floor.getKey().equals(ceil.getKey())) {
                return floor.getValue();
            } else {
                t0 = floor.getKey();
                v0 = floor.getValue();
                t1 = ceil.getKey();
                v1 = ceil.getValue();
            }
            float span = t1 - t0;
            float alpha = span > 1e-5f ? Math.max(0f, Math.min(1f, (t - t0) / span)) : 0f;
            return v0 + (v1 - v0) * alpha;
        }
    }

    public record AtmosphereDef(
        String identifier,
        ColorCurve skyZenithColor,
        ColorCurve skyHorizonColor,
        ScalarCurve rayleighStrength,
        ScalarCurve sunMieStrength,
        ScalarCurve moonMieStrength
    ) {
    }

    public record FogLayer(float start, float end, int rgb, boolean renderDistanceRelative) {
    }

    public record FogDef(
        String identifier,
        FogLayer air,
        FogLayer water,
        FogLayer weather
    ) {
    }

    public record BiomeDef(
        String identifier,
        int skyColorRgb,
        int waterSurfaceRgb,
        int waterFogRgb,
        int grassRgb,
        int foliageRgb,
        String fogId,
        String atmosphereId,
        String lightingId,
        String waterId
    ) {
    }

    public record LightingDef(
        String identifier,
        ColorCurve sunColor,
        ColorCurve moonColor,
        ScalarCurve skyIntensity
    ) {
    }

    private static final Map<String, String> JAVA_TO_BEDROCK_BIOME = createBiomeAliases();

    public final Map<String, AtmosphereDef> atmospheres = new LinkedHashMap<>();
    public final Map<String, BiomeDef> biomes = new LinkedHashMap<>();
    public final Map<String, FogDef> fogs = new LinkedHashMap<>();
    public final Map<String, LightingDef> lightings = new LinkedHashMap<>();
    public final Map<String, BedrockPackManager.ColormapData> colormaps = new LinkedHashMap<>();

    private int cachedBirchColor = -1;
    private int cachedSpruceColor = -1;
    private int cachedMangroveColor = -1;
    private boolean loaded = false;

    public boolean isLoaded() {
        return loaded && (!biomes.isEmpty() || !atmospheres.isEmpty());
    }

    public synchronized void loadFromPack(BedrockPackManager pack) {
        atmospheres.clear();
        biomes.clear();
        fogs.clear();
        lightings.clear();
        colormaps.clear();
        cachedBirchColor = -1;
        cachedSpruceColor = -1;
        cachedMangroveColor = -1;

        for (String json : pack.atmosphericJsons.values()) {
            parseAtmosphere(json);
        }
        for (String json : pack.fogJsons.values()) {
            parseFog(json);
        }
        for (String json : pack.lightingJsons.values()) {
            parseLighting(json);
        }
        for (String json : pack.biomeJsons.values()) {
            parseBiome(json);
        }
        colormaps.putAll(pack.colormaps);
        cachedBirchColor = sampleColormapCenter("birch.png");
        cachedSpruceColor = sampleColormapCenter("evergreen.png");
        cachedMangroveColor = sampleColormapCenter("mangrove_swamp_foliage.png");
        loaded = true;
    }

    private void parseAtmosphere(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject atmo = root.getAsJsonObject("minecraft:atmosphere_settings");
            if (atmo == null) return;
            String id = atmo.getAsJsonObject("description").get("identifier").getAsString().toLowerCase(Locale.ROOT);
            ColorCurve zenith = parseColorCurve(atmo.get("sky_zenith_color"));
            ColorCurve horizon = parseColorCurve(atmo.get("sky_horizon_color"));
            ScalarCurve rayleigh = parseScalarCurve(atmo.get("rayleigh_strength"), 2.72f);
            ScalarCurve sunMie = parseScalarCurve(atmo.get("sun_mie_strength"), 0f);
            ScalarCurve moonMie = parseScalarCurve(atmo.get("moon_mie_strength"), 0f);
            atmospheres.put(id, new AtmosphereDef(id, zenith, horizon, rayleigh, sunMie, moonMie));
        } catch (Exception ignored) {
        }
    }

    private void parseFog(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject fog = root.getAsJsonObject("minecraft:fog_settings");
            if (fog == null) return;
            String id = fog.getAsJsonObject("description").get("identifier").getAsString().toLowerCase(Locale.ROOT);
            JsonObject dist = fog.getAsJsonObject("distance");
            FogLayer air = parseFogLayer(dist != null ? dist.getAsJsonObject("air") : null, 0.3f, 1.0f, 0x99E6FF, true);
            FogLayer water = parseFogLayer(dist != null ? dist.getAsJsonObject("water") : null, 2f, 64f, 0x44AFF5, false);
            FogLayer weather = parseFogLayer(dist != null ? dist.getAsJsonObject("weather") : null, 0.02f, 0.6f, 0x3B3D42, true);
            fogs.put(id, new FogDef(id, air, water, weather));
        } catch (Exception ignored) {
        }
    }

    private static FogLayer parseFogLayer(JsonObject obj, float defStart, float defEnd, int defRgb, boolean defRel) {
        if (obj == null) return new FogLayer(defStart, defEnd, defRgb, defRel);
        float start = obj.has("fog_start") ? obj.get("fog_start").getAsFloat() : defStart;
        float end = obj.has("fog_end") ? obj.get("fog_end").getAsFloat() : defEnd;
        int rgb = obj.has("fog_color") ? parseHexRgb(obj.get("fog_color").getAsString(), defRgb) : defRgb;
        boolean rel = !obj.has("render_distance_type") || "render".equalsIgnoreCase(obj.get("render_distance_type").getAsString());
        return new FogLayer(start, end, rgb, rel);
    }

    private void parseLighting(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject light = root.getAsJsonObject("minecraft:lighting_settings");
            if (light == null) return;
            String id = light.getAsJsonObject("description").get("identifier").getAsString().toLowerCase(Locale.ROOT);
            ColorCurve sunColor = new ColorCurve(new float[]{1f, 1f, 1f});
            ColorCurve moonColor = new ColorCurve(new float[]{0.2f, 0.35f, 0.9f});
            ScalarCurve skyInt = new ScalarCurve(1f);
            if (light.has("directional_lights") && light.getAsJsonObject("directional_lights").has("orbital")) {
                JsonObject orbital = light.getAsJsonObject("directional_lights").getAsJsonObject("orbital");
                if (orbital.has("sun")) sunColor = parseColorCurve(orbital.getAsJsonObject("sun").get("color"));
                if (orbital.has("moon")) moonColor = parseColorCurve(orbital.getAsJsonObject("moon").get("color"));
            }
            if (light.has("sky") && light.getAsJsonObject("sky").has("intensity")) {
                skyInt = parseScalarCurve(light.getAsJsonObject("sky").get("intensity"), 1f);
            }
            lightings.put(id, new LightingDef(id, sunColor, moonColor, skyInt));
        } catch (Exception ignored) {
        }
    }

    private void parseBiome(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject cb = root.getAsJsonObject("minecraft:client_biome");
            if (cb == null) return;
            String id = cb.getAsJsonObject("description").get("identifier").getAsString().toLowerCase(Locale.ROOT);
            JsonObject comps = cb.getAsJsonObject("components");
            if (comps == null) return;

            int skyRgb = readCompColor(comps, "minecraft:sky_color", "sky_color", 0x6DC1FF);
            int waterRgb = readCompColor(comps, "minecraft:water_appearance", "surface_color", 0x2080C9);
            int grassRgb = readCompColor(comps, "minecraft:grass_appearance", "color", 0x73AE4E);
            int foliageRgb = readCompColor(comps, "minecraft:foliage_appearance", "color", 0x57A04E);
            String fogId = readCompId(comps, "minecraft:fog_appearance", "fog_identifier", "oreville_ans:plains");
            String defaultAtmo = switch (id) {
                case "hell" -> "oreville_ans:hell";
                case "the_end" -> "oreville_ans:kpmdws";
                default -> "oreville_ans:pisgjd";
            };
            String atmoId = readCompId(comps, "minecraft:atmosphere_identifier", "atmosphere_identifier", defaultAtmo);
            String lightId = readCompId(comps, "minecraft:lighting_identifier", "lighting_identifier", "oreville_ans:sndrxw");
            String waterId = readCompId(comps, "minecraft:water_identifier", "water_identifier", "oreville_ans:dycbzt");

            FogDef fogDef = fogs.get(fogId);
            int waterFogRgb = fogDef != null ? fogDef.water().rgb() : waterRgb;
            biomes.put(id, new BiomeDef(id, skyRgb, waterRgb, waterFogRgb, grassRgb, foliageRgb, fogId, atmoId, lightId, waterId));
        } catch (Exception ignored) {
        }
    }

    public BiomeDef resolveBiome(String mcBiomeId) {
        if (mcBiomeId == null || mcBiomeId.isEmpty()) return biomes.get("plains");
        String path = mcBiomeId.toLowerCase(Locale.ROOT);
        int colon = path.indexOf(':');
        if (colon >= 0) path = path.substring(colon + 1);
        BiomeDef direct = biomes.get(path);
        if (direct != null) return direct;
        String alias = JAVA_TO_BEDROCK_BIOME.get(path);
        if (alias != null) {
            BiomeDef aliased = biomes.get(alias);
            if (aliased != null) return aliased;
        }
        if (path.contains("end")) return biomes.getOrDefault("the_end", biomes.get("plains"));
        if (path.contains("nether") || path.contains("basalt") || path.contains("warped") || path.contains("crimson")) {
            return biomes.getOrDefault("hell", biomes.get("plains"));
        }
        return biomes.get("plains");
    }

    public AtmosphereDef resolveAtmosphere(String mcBiomeId) {
        BiomeDef b = resolveBiome(mcBiomeId);
        if (b != null) {
            AtmosphereDef a = atmospheres.get(b.atmosphereId());
            if (a != null) return a;
        }
        return atmospheres.get("oreville_ans:pisgjd");
    }

    public FogDef resolveFog(String mcBiomeId) {
        BiomeDef b = resolveBiome(mcBiomeId);
        if (b != null) {
            FogDef f = fogs.get(b.fogId());
            if (f != null) return f;
        }
        return fogs.get("oreville_ans:plains");
    }

    /** Returns packed 0xRRGGBB sky zenith color blended smoothly across skyAngle (0=noon, 0.25=sunset, 0.5=midnight, 0.75=sunrise). */
    public int getSkyZenithRgb(String mcBiomeId, float skyAngle) {
        AtmosphereDef atmo = resolveAtmosphere(mcBiomeId);
        if (atmo == null) {
            BiomeDef b = resolveBiome(mcBiomeId);
            return b != null ? b.skyColorRgb() : 0x6DC1FF;
        }
        float[] rgb = atmo.skyZenithColor().sample(skyAngle, new float[3]);
        return toRgbInt(rgb[0], rgb[1], rgb[2]);
    }

    /** Returns packed 0xRRGGBB sky horizon color. */
    public int getSkyHorizonRgb(String mcBiomeId, float skyAngle) {
        AtmosphereDef atmo = resolveAtmosphere(mcBiomeId);
        if (atmo == null) {
            return getSkyZenithRgb(mcBiomeId, skyAngle);
        }
        float[] rgb = atmo.skyHorizonColor().sample(skyAngle, new float[3]);
        return toRgbInt(rgb[0], rgb[1], rgb[2]);
    }

    /** Returns packed 0xRRGGBB fog color mixing biome air/water/weather fog with time-of-day horizon color. */
    public int getFogRgb(String mcBiomeId, float skyAngle, boolean underwater, boolean weather) {
        FogDef fog = resolveFog(mcBiomeId);
        if (underwater) {
            return fog != null ? fog.water().rgb() : 0x44AFF5;
        }
        if (weather && fog != null) {
            return fog.weather().rgb();
        }
        // Blend biome air fog with dynamic time-of-day horizon color so dawn/dusk/night horizons match cleanly
        AtmosphereDef atmo = resolveAtmosphere(mcBiomeId);
        if (atmo != null) {
            float[] hor = atmo.skyHorizonColor().sample(skyAngle, new float[3]);
            return toRgbInt(hor[0], hor[1], hor[2]);
        }
        return fog != null ? fog.air().rgb() : 0x99E6FF;
    }

    public int getCloudsRgb(String mcBiomeId, float skyAngle) {
        AtmosphereDef atmo = resolveAtmosphere(mcBiomeId);
        if (atmo == null) return 0xFFFFFF;
        float[] hor = atmo.skyHorizonColor().sample(skyAngle, new float[3]);
        // Brighten horizon tint slightly toward crisp sunlit clouds during day, rich twilight at dusk
        float r = Math.min(1f, hor[0] * 0.45f + 0.58f);
        float g = Math.min(1f, hor[1] * 0.45f + 0.58f);
        float b = Math.min(1f, hor[2] * 0.45f + 0.60f);
        // Scale by night brightness factor so midnight clouds darken naturally
        float[] zen = atmo.skyZenithColor().sample(skyAngle, new float[3]);
        float lum = Math.max(0.18f, Math.min(1f, 0.25f + (zen[0] + zen[1] + zen[2]) * 0.35f));
        return toRgbInt(r * lum, g * lum, b * lum);
    }

    public float getFogStartBlocks(String mcBiomeId, boolean underwater, boolean weather, float viewDistanceBlocks) {
        FogDef fog = resolveFog(mcBiomeId);
        if (fog == null) return viewDistanceBlocks * 0.3f;
        FogLayer layer = underwater ? fog.water() : (weather ? fog.weather() : fog.air());
        return layer.renderDistanceRelative() ? layer.start() * viewDistanceBlocks : layer.start();
    }

    public float getFogEndBlocks(String mcBiomeId, boolean underwater, boolean weather, float viewDistanceBlocks) {
        FogDef fog = resolveFog(mcBiomeId);
        if (fog == null) return viewDistanceBlocks;
        FogLayer layer = underwater ? fog.water() : (weather ? fog.weather() : fog.air());
        return layer.renderDistanceRelative() ? layer.end() * viewDistanceBlocks : layer.end();
    }

    public int getBirchFoliageColor() {
        return cachedBirchColor != -1 ? cachedBirchColor : 0x62A84B;
    }

    public int getSpruceFoliageColor() {
        return cachedSpruceColor != -1 ? cachedSpruceColor : 0x458042;
    }

    public int getMangroveFoliageColor() {
        return cachedMangroveColor != -1 ? cachedMangroveColor : 0x57A04E;
    }

    private int sampleColormapCenter(String file) {
        BedrockPackManager.ColormapData data = colormaps.get(file);
        if (data == null || data.argb().length == 0) return -1;
        int cx = data.width() / 2;
        int cy = data.height() / 2;
        return data.argb()[cy * data.width() + cx] & 0xFFFFFF;
    }

    private static ColorCurve parseColorCurve(JsonElement el) {
        if (el == null || el.isJsonNull()) return new ColorCurve(new float[]{1f, 1f, 1f});
        if (el.isJsonArray()) {
            return new ColorCurve(readRgb(el.getAsJsonArray()));
        }
        if (el.isJsonPrimitive()) {
            int rgb = parseHexRgb(el.getAsString(), 0xFFFFFF);
            return new ColorCurve(new float[]{((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f});
        }
        if (el.isJsonObject()) {
            ColorCurve curve = new ColorCurve();
            for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                try {
                    float t = Float.parseFloat(e.getKey());
                    if (e.getValue().isJsonArray()) {
                        curve.keyframes.put(t, readRgb(e.getValue().getAsJsonArray()));
                    } else if (e.getValue().isJsonPrimitive()) {
                        int rgb = parseHexRgb(e.getValue().getAsString(), 0xFFFFFF);
                        curve.keyframes.put(t, new float[]{((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f});
                    }
                } catch (Exception ignored) {
                }
            }
            return curve;
        }
        return new ColorCurve(new float[]{1f, 1f, 1f});
    }

    private static ScalarCurve parseScalarCurve(JsonElement el, float fallback) {
        if (el == null || el.isJsonNull()) return new ScalarCurve(fallback);
        if (el.isJsonPrimitive()) {
            try { return new ScalarCurve(el.getAsFloat()); } catch (Exception e) { return new ScalarCurve(fallback); }
        }
        if (el.isJsonObject()) {
            ScalarCurve curve = new ScalarCurve();
            for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                try {
                    curve.keyframes.put(Float.parseFloat(e.getKey()), e.getValue().getAsFloat());
                } catch (Exception ignored) {
                }
            }
            return curve;
        }
        return new ScalarCurve(fallback);
    }

    private static float[] readRgb(JsonArray arr) {
        float r = arr.size() > 0 ? arr.get(0).getAsFloat() : 255f;
        float g = arr.size() > 1 ? arr.get(1).getAsFloat() : 255f;
        float b = arr.size() > 2 ? arr.get(2).getAsFloat() : 255f;
        if (r > 1.0f || g > 1.0f || b > 1.0f) {
            r /= 255f;
            g /= 255f;
            b /= 255f;
        }
        return new float[]{Math.max(0f, Math.min(1f, r)), Math.max(0f, Math.min(1f, g)), Math.max(0f, Math.min(1f, b))};
    }

    private static int readCompColor(JsonObject comps, String compName, String fieldName, int fallback) {
        if (!comps.has(compName) || !comps.get(compName).isJsonObject()) return fallback;
        JsonObject c = comps.getAsJsonObject(compName);
        if (!c.has(fieldName)) return fallback;
        return parseHexRgb(c.get(fieldName).getAsString(), fallback);
    }

    private static String readCompId(JsonObject comps, String compName, String fieldName, String fallback) {
        if (!comps.has(compName) || !comps.get(compName).isJsonObject()) return fallback;
        JsonObject c = comps.getAsJsonObject(compName);
        if (!c.has(fieldName)) return fallback;
        return c.get(fieldName).getAsString().toLowerCase(Locale.ROOT);
    }

    private static int parseHexRgb(String hex, int fallback) {
        if (hex == null || hex.isEmpty()) return fallback;
        String s = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            int val = (int) Long.parseLong(s, 16);
            return val & 0xFFFFFF;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int toRgbInt(float r, float g, float b) {
        int ir = Math.max(0, Math.min(255, Math.round(r * 255f)));
        int ig = Math.max(0, Math.min(255, Math.round(g * 255f)));
        int ib = Math.max(0, Math.min(255, Math.round(b * 255f)));
        return (ir << 16) | (ig << 8) | ib;
    }

    private static float wrap01(float v) {
        float m = v % 1f;
        return m < 0f ? m + 1f : m;
    }

    private static Map<String, String> createBiomeAliases() {
        Map<String, String> m = new HashMap<>();
        m.put("nether_wastes", "hell");
        m.put("badlands", "mesa");
        m.put("eroded_badlands", "mesa_bryce");
        m.put("wooded_badlands", "mesa_plateau_stone");
        m.put("swamp", "swampland");
        m.put("snowy_plains", "ice_plains");
        m.put("ice_spikes", "ice_plains_spikes");
        m.put("snowy_taiga", "cold_taiga");
        m.put("snowy_beach", "cold_beach");
        m.put("old_growth_pine_taiga", "mega_taiga");
        m.put("old_growth_spruce_taiga", "redwood_taiga_mutated");
        m.put("old_growth_birch_forest", "birch_forest_mutated");
        m.put("windswept_hills", "extreme_hills");
        m.put("windswept_forest", "extreme_hills_plus_trees");
        m.put("windswept_gravelly_hills", "extreme_hills_mutated");
        m.put("windswept_savanna", "savanna_mutated");
        m.put("stony_shore", "stone_beach");
        m.put("mushroom_fields", "mushroom_island");
        m.put("sparse_jungle", "jungle_edge");
        m.put("bamboo_jungle", "bamboo_jungle");
        m.put("dark_forest", "roofed_forest");
        m.put("small_end_islands", "the_end");
        m.put("end_midlands", "the_end");
        m.put("end_highlands", "the_end");
        m.put("end_barrens", "the_end");
        m.put("wooded_hills", "forest_hills");
        m.put("meadow", "meadow");
        return Collections.unmodifiableMap(m);
    }
}
