package mchorse.bbs_mod.tools;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

public class GenerateSplashModel
{
    private static final String PARTICULAR_DIR = "C:/Users/jacqu/Downloads/particular/src/main/resources/assets/particular/textures/particle";
    private static final Color WATER_COLOR = new Color(0x3F, 0x76, 0xE4); // Vanilla water tint

    public static void main(String[] args) throws Exception
    {
        System.out.println("Starting Water Splash Generator...");

        // 1. Build composite atlas (256x64)
        BufferedImage atlas = new BufferedImage(256, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = atlas.createGraphics();

        // 9 column frames
        for (int i = 1; i <= 9; i++)
        {
            File splashFile = new File(PARTICULAR_DIR, "water_splash_" + i + ".png");
            File foamFile = new File(PARTICULAR_DIR, "water_splash_foam_" + i + ".png");

            BufferedImage splash = ImageIO.read(splashFile);
            BufferedImage foam = ImageIO.read(foamFile);

            BufferedImage comp = new BufferedImage(16, 32, BufferedImage.TYPE_INT_ARGB);

            for (int y = 0; y < 32; y++)
            {
                for (int x = 0; x < 16; x++)
                {
                    int pSplash = splash.getRGB(x, y);
                    int aSplash = (pSplash >> 24) & 0xFF;

                    int pFoam = foam.getRGB(x, y);
                    int aFoam = (pFoam >> 24) & 0xFF;

                    if (aSplash == 0 && aFoam == 0) continue;

                    // Tint splash with water color
                    float fSplashA = aSplash / 255.0f;
                    float fSplashR = ((pSplash >> 16) & 0xFF) / 255.0f * (WATER_COLOR.getRed() / 255.0f);
                    float fSplashG = ((pSplash >> 8) & 0xFF) / 255.0f * (WATER_COLOR.getGreen() / 255.0f);
                    float fSplashB = (pSplash & 0xFF) / 255.0f * (WATER_COLOR.getBlue() / 255.0f);

                    // Foam is white highlights
                    float fFoamA = aFoam / 255.0f;
                    float fFoamR = ((pFoam >> 16) & 0xFF) / 255.0f;
                    float fFoamG = ((pFoam >> 8) & 0xFF) / 255.0f;
                    float fFoamB = (pFoam & 0xFF) / 255.0f;

                    // Alpha blend foam over splash
                    float outA = fFoamA + fSplashA * (1.0f - fFoamA);
                    if (outA > 0.001f)
                    {
                        float outR = (fFoamR * fFoamA + fSplashR * fSplashA * (1.0f - fFoamA)) / outA;
                        float outG = (fFoamG * fFoamA + fSplashG * fSplashA * (1.0f - fFoamA)) / outA;
                        float outB = (fFoamB * fFoamA + fSplashB * fSplashA * (1.0f - fFoamA)) / outA;

                        int iA = Math.min(255, Math.round(outA * 255));
                        int iR = Math.min(255, Math.round(outR * 255));
                        int iG = Math.min(255, Math.round(outG * 255));
                        int iB = Math.min(255, Math.round(outB * 255));

                        comp.setRGB(x, y, (iA << 24) | (iR << 16) | (iG << 8) | iB);
                    }
                }
            }

            g2d.drawImage(comp, (i - 1) * 16, 0, null);
        }

        // 9 ring frames
        for (int i = 1; i <= 9; i++)
        {
            File ringFile = new File(PARTICULAR_DIR, "water_splash_ring_" + i + ".png");
            BufferedImage ring = ImageIO.read(ringFile);

            BufferedImage compRing = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 16; y++)
            {
                for (int x = 0; x < 16; x++)
                {
                    int pRing = ring.getRGB(x, y);
                    int aRing = (pRing >> 24) & 0xFF;
                    if (aRing == 0) continue;

                    // Ring has subtle water tint with foam highlight
                    float fA = aRing / 255.0f;
                    float val = ((pRing >> 16) & 0xFF) / 255.0f;
                    float r = val * 0.7f + (WATER_COLOR.getRed() / 255.0f) * 0.3f;
                    float g = val * 0.8f + (WATER_COLOR.getGreen() / 255.0f) * 0.3f;
                    float b = val * 0.9f + (WATER_COLOR.getBlue() / 255.0f) * 0.3f;

                    int iA = Math.min(255, Math.round(fA * 255));
                    int iR = Math.min(255, Math.round(r * 255));
                    int iG = Math.min(255, Math.round(g * 255));
                    int iB = Math.min(255, Math.round(b * 255));

                    compRing.setRGB(x, y, (iA << 24) | (iR << 16) | (iG << 8) | iB);
                }
            }

            g2d.drawImage(compRing, (i - 1) * 16, 32, null);
        }

        // Droplet texture at (144, 32), 4x4
        BufferedImage drop = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        int dropColor = (240 << 24) | (210 << 16) | (235 << 8) | 255; // Crisp water-foam drop
        drop.setRGB(1, 1, dropColor);
        drop.setRGB(1, 2, dropColor);
        drop.setRGB(2, 1, dropColor);
        drop.setRGB(2, 2, dropColor);
        g2d.drawImage(drop, 144, 32, null);

        g2d.dispose();

        // Convert atlas to PNG bytes and base64
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(atlas, "png", baos);
        byte[] pngBytes = baos.toByteArray();
        String base64Png = Base64.getEncoder().encodeToString(pngBytes);

        System.out.println("Atlas generated: 256x64, PNG bytes: " + pngBytes.length);

        // Generate Blockbench (.bbmodel), BBS (.bbs.json), Bedrock (.geo.json & .animation.json), and config.json
        String bbmodelJson = generateBBModel(base64Png);
        String bbsJson = generateBbsJson();
        String geoJson = generateGeoJson();
        String animJson = generateBedrockAnimationJson();
        String configJson = generateConfigJson();

        // Save files to Downloads
        File downloadsDir = new File("C:/Users/jacqu/Downloads");
        writeFile(new File(downloadsDir, "water_splash.bbmodel"), bbmodelJson);
        writeFile(new File(downloadsDir, "water_splash.geo.json"), geoJson);
        writeFile(new File(downloadsDir, "water_splash.animation.json"), animJson);
        File dlPng = new File(downloadsDir, "water_splash.png");
        ImageIO.write(atlas, "png", dlPng);
        System.out.println("Saved all assets to Downloads folder!");

        // Target Modrinth profiles
        String[] profiles = {"Joofy - Gta", "1.20.1", "bbs 2", "bbs cml", "clone civ", "Iron Golem civ"};
        for (String p : profiles)
        {
            String[] targetSubfolders = {"water_splash", "effects/water_splash"};
            for (String sub : targetSubfolders)
            {
                File modelDir = new File("C:/Users/jacqu/AppData/Roaming/ModrinthApp/profiles/" + p + "/config/bbs/assets/models/" + sub);
                modelDir.mkdirs();

                writeFile(new File(modelDir, "water_splash.bbmodel"), bbmodelJson);
                writeFile(new File(modelDir, "model.bbs.json"), bbsJson);
                writeFile(new File(modelDir, "water_splash.geo.json"), geoJson);
                writeFile(new File(modelDir, "water_splash.animation.json"), animJson);
                writeFile(new File(modelDir, "config.json"), configJson);

                File destPng = new File(modelDir, "model.png");
                ImageIO.write(atlas, "png", destPng);

                System.out.println("Deployed complete model suite to: " + modelDir.getAbsolutePath());
            }

            // Also deploy to saves/City/bbs/assets/models if profile is Joofy - Gta
            if ("Joofy - Gta".equals(p))
            {
                for (String sub : targetSubfolders)
                {
                    File worldModelDir = new File("C:/Users/jacqu/AppData/Roaming/ModrinthApp/profiles/" + p + "/saves/City/bbs/assets/models/" + sub);
                    worldModelDir.mkdirs();

                    writeFile(new File(worldModelDir, "water_splash.bbmodel"), bbmodelJson);
                    writeFile(new File(worldModelDir, "model.bbs.json"), bbsJson);
                    writeFile(new File(worldModelDir, "water_splash.geo.json"), geoJson);
                    writeFile(new File(worldModelDir, "water_splash.animation.json"), animJson);
                    writeFile(new File(worldModelDir, "config.json"), configJson);

                    File destPng = new File(worldModelDir, "model.png");
                    ImageIO.write(atlas, "png", destPng);

                    System.out.println("Deployed to world assets: " + worldModelDir.getAbsolutePath());
                }
            }
        }

        System.out.println("All models generated and deployed successfully!");
    }

    private static void writeFile(File file, String content) throws Exception
    {
        try (FileWriter fw = new FileWriter(file, StandardCharsets.UTF_8))
        {
            fw.write(content);
        }
    }

    private static String generateConfigJson()
    {
        return "{\n  \"culling\": false,\n  \"procedural\": false\n}\n";
    }

    private static String generateBBModel(String base64Png)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"meta\": {\n");
        sb.append("    \"format_version\": \"4.10\",\n");
        sb.append("    \"model_format\": \"free\",\n");
        sb.append("    \"box_uv\": false\n");
        sb.append("  },\n");
        sb.append("  \"name\": \"water_splash\",\n");
        sb.append("  \"model_identifier\": \"\",\n");
        sb.append("  \"visible_box\": [3, 3, 3],\n");
        sb.append("  \"variable_placeholders\": \"\",\n");
        sb.append("  \"variable_placeholder_buttons\": [],\n");
        sb.append("  \"timeline_setups\": [],\n");
        sb.append("  \"unhandled_root_fields\": {},\n");
        sb.append("  \"resolution\": {\"width\": 256, \"height\": 64},\n");

        // Elements
        sb.append("  \"elements\": [\n");
        List<String> elementJsons = new ArrayList<>();

        Map<String, String> boneToUuid = new LinkedHashMap<>();
        Map<String, List<String>> boneElements = new LinkedHashMap<>();

        // 1. Outer Splash frames (width ~ 15.36 units, height 8.0 units)
        float outerR = 7.68f;
        float outerH = 8.0f;
        for (int f = 1; f <= 9; f++)
        {
            String boneName = "outer_f" + f;
            String boneUuid = UUID.randomUUID().toString();
            boneToUuid.put(boneName, boneUuid);
            List<String> elemUuids = new ArrayList<>();

            int u1 = (f - 1) * 16;
            int u2 = u1 + 16;
            int v1 = 0;
            int v2 = 32;

            elemUuids.add(addWall(elementJsons, "outer_w_" + f, -outerR, 0, -outerR, -outerR, outerH, outerR, u1, v1, u2, v2, "x"));
            elemUuids.add(addWall(elementJsons, "outer_s_" + f, -outerR, 0, outerR, outerR, outerH, outerR, u1, v1, u2, v2, "z"));
            elemUuids.add(addWall(elementJsons, "outer_e_" + f, outerR, 0, -outerR, outerR, outerH, outerR, u1, v1, u2, v2, "x"));
            elemUuids.add(addWall(elementJsons, "outer_n_" + f, -outerR, 0, -outerR, outerR, outerH, -outerR, u1, v1, u2, v2, "z"));

            boneElements.put(boneName, elemUuids);
        }

        // 2. Outer Ring frames (size 15.36 units)
        for (int f = 1; f <= 9; f++)
        {
            String boneName = "ring_f" + f;
            String boneUuid = UUID.randomUUID().toString();
            boneToUuid.put(boneName, boneUuid);
            List<String> elemUuids = new ArrayList<>();

            int u1 = (f - 1) * 16;
            int u2 = u1 + 16;
            int v1 = 32;
            int v2 = 48;

            elemUuids.add(addPlaneY(elementJsons, "ring_" + f, -outerR, 0.05f, -outerR, outerR, 0.05f, outerR, u1, v1, u2, v2));
            boneElements.put(boneName, elemUuids);
        }

        // 3. Inner Splash frames (width ~ 10.14 units, height 16.0 units)
        float innerR = 5.07f;
        float innerH = 16.0f;
        for (int f = 1; f <= 9; f++)
        {
            String boneName = "inner_f" + f;
            String boneUuid = UUID.randomUUID().toString();
            boneToUuid.put(boneName, boneUuid);
            List<String> elemUuids = new ArrayList<>();

            int u1 = (f - 1) * 16;
            int u2 = u1 + 16;
            int v1 = 0;
            int v2 = 32;

            elemUuids.add(addWall(elementJsons, "inner_w_" + f, -innerR, 0, -innerR, -innerR, innerH, innerR, u1, v1, u2, v2, "x"));
            elemUuids.add(addWall(elementJsons, "inner_s_" + f, -innerR, 0, innerR, innerR, innerH, innerR, u1, v1, u2, v2, "z"));
            elemUuids.add(addWall(elementJsons, "inner_e_" + f, innerR, 0, -innerR, innerR, innerH, innerR, u1, v1, u2, v2, "x"));
            elemUuids.add(addWall(elementJsons, "inner_n_" + f, -innerR, 0, -innerR, innerR, innerH, -innerR, u1, v1, u2, v2, "z"));

            boneElements.put(boneName, elemUuids);
        }

        // 4. Inner Ring frames (size 10.14 units)
        for (int f = 1; f <= 9; f++)
        {
            String boneName = "inner_ring_f" + f;
            String boneUuid = UUID.randomUUID().toString();
            boneToUuid.put(boneName, boneUuid);
            List<String> elemUuids = new ArrayList<>();

            int u1 = (f - 1) * 16;
            int u2 = u1 + 16;
            int v1 = 32;
            int v2 = 48;

            elemUuids.add(addPlaneY(elementJsons, "inner_ring_" + f, -innerR, 0.08f, -innerR, innerR, 0.08f, innerR, u1, v1, u2, v2));
            boneElements.put(boneName, elemUuids);
        }

        // 5. Droplet elements (16 droplets)
        for (int d = 1; d <= 16; d++)
        {
            String boneName = "drop_" + d;
            String boneUuid = UUID.randomUUID().toString();
            boneToUuid.put(boneName, boneUuid);
            List<String> elemUuids = new ArrayList<>();

            elemUuids.add(addCube(elementJsons, "drop_cube_" + d, -0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 144, 32, 148, 36));
            boneElements.put(boneName, elemUuids);
        }

        sb.append(String.join(",\n", elementJsons));
        sb.append("\n  ],\n");

        // Outliner (Hierarchy)
        sb.append("  \"outliner\": [\n");
        sb.append("    {\n");
        sb.append("      \"name\": \"root\",\n");
        sb.append("      \"origin\": [0, 0, 0],\n");
        sb.append("      \"color\": 0,\n");
        String rootUuid = UUID.randomUUID().toString();
        boneToUuid.put("root", rootUuid);
        sb.append("      \"uuid\": \"").append(rootUuid).append("\",\n");
        sb.append("      \"export\": true,\n");
        sb.append("      \"mirror_uv\": false,\n");
        sb.append("      \"isOpen\": true,\n");
        sb.append("      \"locked\": false,\n");
        sb.append("      \"visibility\": true,\n");
        sb.append("      \"autouv\": 0,\n");
        sb.append("      \"children\": [\n");

        List<String> parentGroupJsons = new ArrayList<>();
        parentGroupJsons.add(buildParentGroup("outer_splash", boneToUuid, boneElements, "outer_f", 9));
        parentGroupJsons.add(buildParentGroup("outer_ring", boneToUuid, boneElements, "ring_f", 9));
        parentGroupJsons.add(buildParentGroup("inner_splash", boneToUuid, boneElements, "inner_f", 9));
        parentGroupJsons.add(buildParentGroup("inner_ring", boneToUuid, boneElements, "inner_ring_f", 9));
        parentGroupJsons.add(buildParentGroup("droplets", boneToUuid, boneElements, "drop_", 16));

        sb.append(String.join(",\n", parentGroupJsons));
        sb.append("\n      ]\n");
        sb.append("    }\n");
        sb.append("  ],\n");

        // Textures
        sb.append("  \"textures\": [\n");
        sb.append("    {\n");
        sb.append("      \"path\": \"model.png\",\n");
        sb.append("      \"name\": \"model.png\",\n");
        sb.append("      \"folder\": \"\",\n");
        sb.append("      \"namespace\": \"\",\n");
        sb.append("      \"id\": \"0\",\n");
        sb.append("      \"width\": 256,\n");
        sb.append("      \"height\": 64,\n");
        sb.append("      \"uv_width\": 256,\n");
        sb.append("      \"uv_height\": 64,\n");
        sb.append("      \"particle\": false,\n");
        sb.append("      \"use_as_default\": false,\n");
        sb.append("      \"layers_enabled\": false,\n");
        sb.append("      \"sync_to_project\": \"\",\n");
        sb.append("      \"render_mode\": \"normal\",\n");
        sb.append("      \"render_sides\": \"auto\",\n");
        sb.append("      \"pbr_channel\": \"color\",\n");
        sb.append("      \"frame_time\": 1,\n");
        sb.append("      \"frame_order_type\": \"loop\",\n");
        sb.append("      \"frame_order\": \"\",\n");
        sb.append("      \"frame_interpolate\": false,\n");
        sb.append("      \"visible\": true,\n");
        sb.append("      \"internal\": true,\n");
        sb.append("      \"saved\": true,\n");
        sb.append("      \"uuid\": \"").append(UUID.randomUUID().toString()).append("\",\n");
        sb.append("      \"relative_path\": \"model.png\",\n");
        sb.append("      \"source\": \"data:image/png;base64,").append(base64Png).append("\"\n");
        sb.append("    }\n");
        sb.append("  ],\n");

        // Animations
        sb.append("  \"animations\": [\n");
        sb.append(generateBBModelAnimation(boneToUuid));
        sb.append("  ],\n");
        sb.append("  \"animation_variable_placeholders\": \"\\n\"\n");
        sb.append("}\n");

        return sb.toString();
    }

    private static String buildParentGroup(String parentName, Map<String, String> boneToUuid, Map<String, List<String>> boneElements, String childPrefix, int count)
    {
        String pUuid = UUID.randomUUID().toString();
        boneToUuid.put(parentName, pUuid);
        StringBuilder sb = new StringBuilder();
        sb.append("        {\n");
        sb.append("          \"name\": \"").append(parentName).append("\",\n");
        sb.append("          \"origin\": [0, 0, 0],\n");
        sb.append("          \"color\": 1,\n");
        sb.append("          \"uuid\": \"").append(pUuid).append("\",\n");
        sb.append("          \"export\": true,\n");
        sb.append("          \"mirror_uv\": false,\n");
        sb.append("          \"isOpen\": false,\n");
        sb.append("          \"locked\": false,\n");
        sb.append("          \"visibility\": true,\n");
        sb.append("          \"autouv\": 0,\n");
        sb.append("          \"children\": [\n");

        List<String> children = new ArrayList<>();
        for (int i = 1; i <= count; i++)
        {
            String cName = childPrefix + i;
            String cUuid = boneToUuid.get(cName);
            List<String> elemIds = boneElements.get(cName);

            StringBuilder cb = new StringBuilder();
            cb.append("            {\n");
            cb.append("              \"name\": \"").append(cName).append("\",\n");
            cb.append("              \"origin\": [0, 0, 0],\n");
            cb.append("              \"color\": 2,\n");
            cb.append("              \"uuid\": \"").append(cUuid).append("\",\n");
            cb.append("              \"export\": true,\n");
            cb.append("              \"mirror_uv\": false,\n");
            cb.append("              \"isOpen\": false,\n");
            cb.append("              \"locked\": false,\n");
            cb.append("              \"visibility\": true,\n");
            cb.append("              \"autouv\": 0,\n");
            cb.append("              \"children\": [\n");
            for (int k = 0; k < elemIds.size(); k++)
            {
                cb.append("                \"").append(elemIds.get(k)).append("\"");
                if (k < elemIds.size() - 1) cb.append(",");
                cb.append("\n");
            }
            cb.append("              ]\n");
            cb.append("            }");
            children.add(cb.toString());
        }

        sb.append(String.join(",\n", children));
        sb.append("\n          ]\n");
        sb.append("        }");
        return sb.toString();
    }

    private static String addWall(List<String> elements, String name, float fromX, float fromY, float fromZ, float toX, float toY, float toZ, int u1, int v1, int u2, int v2, String axis)
    {
        String uuid = UUID.randomUUID().toString();
        String faceProps;
        if ("x".equals(axis))
        {
            faceProps = String.format(Locale.ROOT,
                "\"west\":{\"uv\":[%d,%d,%d,%d],\"texture\":0},\"east\":{\"uv\":[%d,%d,%d,%d],\"texture\":0}",
                u1, v1, u2, v2, u2, v1, u1, v2);
        }
        else
        {
            faceProps = String.format(Locale.ROOT,
                "\"north\":{\"uv\":[%d,%d,%d,%d],\"texture\":0},\"south\":{\"uv\":[%d,%d,%d,%d],\"texture\":0}",
                u1, v1, u2, v2, u2, v1, u1, v2);
        }

        String json = String.format(Locale.ROOT,
            "    {\"name\":\"%s\",\"box_uv\":false,\"rescale\":false,\"locked\":false,\"render_order\":\"default\",\"allow_mirror_modeling\":true,\"from\":[%.2f,%.2f,%.2f],\"to\":[%.2f,%.2f,%.2f],\"origin\":[0,0,0],\"faces\":{%s},\"type\":\"cube\",\"uuid\":\"%s\"}",
            name, fromX, fromY, fromZ, toX, toY, toZ, faceProps, uuid);
        elements.add(json);
        return uuid;
    }

    private static String addPlaneY(List<String> elements, String name, float fromX, float y, float fromZ, float toX, float y2, float toZ, int u1, int v1, int u2, int v2)
    {
        String uuid = UUID.randomUUID().toString();
        String faceProps = String.format(Locale.ROOT,
            "\"up\":{\"uv\":[%d,%d,%d,%d],\"texture\":0},\"down\":{\"uv\":[%d,%d,%d,%d],\"texture\":0}",
            u1, v1, u2, v2, u1, v1, u2, v2);

        String json = String.format(Locale.ROOT,
            "    {\"name\":\"%s\",\"box_uv\":false,\"rescale\":false,\"locked\":false,\"render_order\":\"default\",\"allow_mirror_modeling\":true,\"from\":[%.2f,%.2f,%.2f],\"to\":[%.2f,%.2f,%.2f],\"origin\":[0,0,0],\"faces\":{%s},\"type\":\"cube\",\"uuid\":\"%s\"}",
            name, fromX, y, fromZ, toX, y2, toZ, faceProps, uuid);
        elements.add(json);
        return uuid;
    }

    private static String addCube(List<String> elements, String name, float fromX, float fromY, float fromZ, float toX, float toY, float toZ, int u1, int v1, int u2, int v2)
    {
        String uuid = UUID.randomUUID().toString();
        String faceProps = String.format(Locale.ROOT,
            "\"north\":{\"uv\":[%d,%d,%d,%d],\"texture\":0},\"south\":{\"uv\":[%d,%d,%d,%d],\"texture\":0},\"east\":{\"uv\":[%d,%d,%d,%d],\"texture\":0},\"west\":{\"uv\":[%d,%d,%d,%d],\"texture\":0},\"up\":{\"uv\":[%d,%d,%d,%d],\"texture\":0},\"down\":{\"uv\":[%d,%d,%d,%d],\"texture\":0}",
            u1, v1, u2, v2, u1, v1, u2, v2, u1, v1, u2, v2, u1, v1, u2, v2, u1, v1, u2, v2, u1, v1, u2, v2);

        String json = String.format(Locale.ROOT,
            "    {\"name\":\"%s\",\"box_uv\":false,\"rescale\":false,\"locked\":false,\"render_order\":\"default\",\"allow_mirror_modeling\":true,\"from\":[%.2f,%.2f,%.2f],\"to\":[%.2f,%.2f,%.2f],\"origin\":[0,0,0],\"faces\":{%s},\"type\":\"cube\",\"uuid\":\"%s\"}",
            name, fromX, fromY, fromZ, toX, toY, toZ, faceProps, uuid);
        elements.add(json);
        return uuid;
    }

    private static String generateBBModelAnimation(Map<String, String> boneToUuid)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("    {\n");
        sb.append("      \"uuid\": \"").append(UUID.randomUUID().toString()).append("\",\n");
        sb.append("      \"name\": \"splash\",\n");
        sb.append("      \"loop\": \"once\",\n");
        sb.append("      \"override\": false,\n");
        sb.append("      \"length\": 1.35,\n");
        sb.append("      \"snapping\": 20,\n");
        sb.append("      \"selected\": true,\n");
        sb.append("      \"group_name\": \"\",\n");
        sb.append("      \"anim_time_update\": \"\",\n");
        sb.append("      \"blend_weight\": \"\",\n");
        sb.append("      \"start_delay\": \"\",\n");
        sb.append("      \"loop_delay\": \"\",\n");
        sb.append("      \"animators\": {\n");

        List<String> animators = new ArrayList<>();

        // Parent bone animators
        animators.add(scaleSmoothAnimator(boneToUuid.get("outer_splash"), "outer_splash", 0.0f, 0.9f, 0.8f, 1.0f));
        animators.add(scaleSmoothAnimator(boneToUuid.get("outer_ring"), "outer_ring", 0.0f, 0.9f, 0.8f, 1.0f));

        animators.add(scaleSmoothWindowAnimator(boneToUuid.get("inner_splash"), "inner_splash", 0.4f, 1.3f, 0.8f, 1.0f));
        animators.add(scaleSmoothWindowAnimator(boneToUuid.get("inner_ring"), "inner_ring", 0.4f, 1.3f, 0.8f, 1.0f));

        // Frame animators: 0.1s slices
        for (int f = 1; f <= 9; f++)
        {
            float tStart = (f - 1) * 0.1f;
            float tEnd = f * 0.1f;
            animators.add(stepFrameAnimator(boneToUuid.get("outer_f" + f), "outer_f" + f, tStart, tEnd));
            animators.add(stepFrameAnimator(boneToUuid.get("ring_f" + f), "ring_f" + f, tStart, tEnd));
        }

        for (int f = 1; f <= 9; f++)
        {
            float tStart = 0.4f + (f - 1) * 0.1f;
            float tEnd = 0.4f + f * 0.1f;
            animators.add(stepFrameAnimator(boneToUuid.get("inner_f" + f), "inner_f" + f, tStart, tEnd));
            animators.add(stepFrameAnimator(boneToUuid.get("inner_ring_f" + f), "inner_ring_f" + f, tStart, tEnd));
        }

        // Droplet animators:
        for (int d = 1; d <= 8; d++)
        {
            double angle = (d - 1) * (2.0 * Math.PI / 8.0) + 0.1;
            float vHoriz = 10.0f + (d % 3) * 2.0f;
            float vx = (float) (Math.cos(angle) * vHoriz);
            float vz = (float) (Math.sin(angle) * vHoriz);
            float vy0 = 18.0f + (d % 2) * 4.0f;
            float g = 45.0f;
            float tEnd = 2.0f * vy0 / g;
            tEnd = Math.min(0.85f, tEnd);

            animators.add(dropletAnimator(boneToUuid.get("drop_" + d), "drop_" + d, 0.0f, tEnd, vx, vz, vy0, g));
        }

        for (int d = 9; d <= 16; d++)
        {
            int idx = d - 8;
            double angle = (idx - 1) * (2.0 * Math.PI / 8.0) + 0.35;
            float vHoriz = 3.5f + (idx % 3) * 1.5f;
            float vx = (float) (Math.cos(angle) * vHoriz);
            float vz = (float) (Math.sin(angle) * vHoriz);
            float vy0 = 30.0f + (idx % 3) * 3.0f;
            float g = 52.0f;
            float tStart = 0.4f;
            float tDuration = 2.0f * vy0 / g;
            float tEnd = Math.min(1.3f, tStart + tDuration);

            animators.add(dropletAnimator(boneToUuid.get("drop_" + d), "drop_" + d, tStart, tEnd, vx, vz, vy0, g));
        }

        sb.append(String.join(",\n", animators));
        sb.append("\n      }\n");
        sb.append("    }\n");
        return sb.toString();
    }

    private static String stepFrameAnimator(String uuid, String name, float tStart, float tEnd)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("        \"").append(uuid).append("\": {\n");
        sb.append("          \"name\": \"").append(name).append("\",\n");
        sb.append("          \"type\": \"bone\",\n");
        sb.append("          \"rotation_global\": false,\n");
        sb.append("          \"quaternion_interpolation\": false,\n");
        sb.append("          \"keyframes\": [\n");

        List<String> kfs = new ArrayList<>();
        if (tStart > 0.001f)
        {
            kfs.add(String.format(Locale.ROOT,
                "            {\"channel\":\"scale\",\"time\":0.0,\"color\":-1,\"interpolation\":\"step\",\"uuid\":\"%s\",\"data_points\":[{\"x\":\"0\",\"y\":\"0\",\"z\":\"0\"}]}",
                UUID.randomUUID().toString()));
        }
        kfs.add(String.format(Locale.ROOT,
            "            {\"channel\":\"scale\",\"time\":%.2f,\"color\":-1,\"interpolation\":\"step\",\"uuid\":\"%s\",\"data_points\":[{\"x\":\"1\",\"y\":\"1\",\"z\":\"1\"}]}",
            tStart, UUID.randomUUID().toString()));
        kfs.add(String.format(Locale.ROOT,
            "            {\"channel\":\"scale\",\"time\":%.2f,\"color\":-1,\"interpolation\":\"step\",\"uuid\":\"%s\",\"data_points\":[{\"x\":\"0\",\"y\":\"0\",\"z\":\"0\"}]}",
            tEnd, UUID.randomUUID().toString()));

        sb.append(String.join(",\n", kfs));
        sb.append("\n          ]\n");
        sb.append("        }");
        return sb.toString();
    }

    private static String scaleSmoothAnimator(String uuid, String name, float tStart, float tEnd, float sStart, float sEnd)
    {
        return String.format(Locale.ROOT,
            "        \"%s\": {\n" +
            "          \"name\": \"%s\",\n" +
            "          \"type\": \"bone\",\n" +
            "          \"rotation_global\": false,\n" +
            "          \"quaternion_interpolation\": false,\n" +
            "          \"keyframes\": [\n" +
            "            {\"channel\":\"scale\",\"time\":%.2f,\"color\":-1,\"interpolation\":\"linear\",\"uuid\":\"%s\",\"data_points\":[{\"x\":\"%.2f\",\"y\":\"1.0\",\"z\":\"%.2f\"}]},\n" +
            "            {\"channel\":\"scale\",\"time\":%.2f,\"color\":-1,\"interpolation\":\"linear\",\"uuid\":\"%s\",\"data_points\":[{\"x\":\"%.2f\",\"y\":\"1.0\",\"z\":\"%.2f\"}]}\n" +
            "          ]\n" +
            "        }",
            uuid, name,
            tStart, UUID.randomUUID().toString(), sStart, sStart,
            tEnd, UUID.randomUUID().toString(), sEnd, sEnd);
    }

    private static String scaleSmoothWindowAnimator(String uuid, String name, float tStart, float tEnd, float sStart, float sEnd)
    {
        return String.format(Locale.ROOT,
            "        \"%s\": {\n" +
            "          \"name\": \"%s\",\n" +
            "          \"type\": \"bone\",\n" +
            "          \"rotation_global\": false,\n" +
            "          \"quaternion_interpolation\": false,\n" +
            "          \"keyframes\": [\n" +
            "            {\"channel\":\"scale\",\"time\":0.0,\"color\":-1,\"interpolation\":\"step\",\"uuid\":\"%s\",\"data_points\":[{\"x\":\"0\",\"y\":\"0\",\"z\":\"0\"}]},\n" +
            "            {\"channel\":\"scale\",\"time\":%.2f,\"color\":-1,\"interpolation\":\"linear\",\"uuid\":\"%s\",\"data_points\":[{\"x\":\"%.2f\",\"y\":\"1.0\",\"z\":\"%.2f\"}]},\n" +
            "            {\"channel\":\"scale\",\"time\":%.2f,\"color\":-1,\"interpolation\":\"linear\",\"uuid\":\"%s\",\"data_points\":[{\"x\":\"%.2f\",\"y\":\"1.0\",\"z\":\"%.2f\"}]},\n" +
            "            {\"channel\":\"scale\",\"time\":%.2f,\"color\":-1,\"interpolation\":\"step\",\"uuid\":\"%s\",\"data_points\":[{\"x\":\"0\",\"y\":\"0\",\"z\":\"0\"}]}\n" +
            "          ]\n" +
            "        }",
            uuid, name,
            UUID.randomUUID().toString(),
            tStart, UUID.randomUUID().toString(), sStart, sStart,
            tEnd, UUID.randomUUID().toString(), sEnd, sEnd,
            tEnd + 0.01f, UUID.randomUUID().toString());
    }

    private static String dropletAnimator(String uuid, String name, float tStart, float tEnd, float vx, float vz, float vy0, float g)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("        \"").append(uuid).append("\": {\n");
        sb.append("          \"name\": \"").append(name).append("\",\n");
        sb.append("          \"type\": \"bone\",\n");
        sb.append("          \"rotation_global\": false,\n");
        sb.append("          \"quaternion_interpolation\": false,\n");
        sb.append("          \"keyframes\": [\n");

        List<String> kfs = new ArrayList<>();
        if (tStart > 0.001f)
        {
            kfs.add(String.format(Locale.ROOT,
                "            {\"channel\":\"scale\",\"time\":0.0,\"color\":-1,\"interpolation\":\"step\",\"uuid\":\"%s\",\"data_points\":[{\"x\":\"0\",\"y\":\"0\",\"z\":\"0\"}]}",
                UUID.randomUUID().toString()));
        }
        kfs.add(String.format(Locale.ROOT,
            "            {\"channel\":\"scale\",\"time\":%.2f,\"color\":-1,\"interpolation\":\"step\",\"uuid\":\"%s\",\"data_points\":[{\"x\":\"1\",\"y\":\"1\",\"z\":\"1\"}]}",
            tStart, UUID.randomUUID().toString()));
        kfs.add(String.format(Locale.ROOT,
            "            {\"channel\":\"scale\",\"time\":%.2f,\"color\":-1,\"interpolation\":\"step\",\"uuid\":\"%s\",\"data_points\":[{\"x\":\"0\",\"y\":\"0\",\"z\":\"0\"}]}",
            tEnd, UUID.randomUUID().toString()));

        int steps = 6;
        for (int i = 0; i <= steps; i++)
        {
            float dt = (tEnd - tStart) * (i / (float) steps);
            float time = tStart + dt;
            float px = vx * dt;
            float pz = vz * dt;
            float py = Math.max(0.0f, vy0 * dt - 0.5f * g * dt * dt);

            kfs.add(String.format(Locale.ROOT,
                "            {\"channel\":\"position\",\"time\":%.2f,\"color\":-1,\"interpolation\":\"linear\",\"uuid\":\"%s\",\"data_points\":[{\"x\":\"%.2f\",\"y\":\"%.2f\",\"z\":\"%.2f\"}]}",
                time, UUID.randomUUID().toString(), px, py, pz));
        }

        sb.append(String.join(",\n", kfs));
        sb.append("\n          ]\n");
        sb.append("        }");
        return sb.toString();
    }

    private static String generateBbsJson()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"version\": \"0.7.2\",\n");
        sb.append("  \"animations\": {\n");
        sb.append("    \"splash\": {\n");
        sb.append("      \"duration\": 1.35,\n");
        sb.append("      \"groups\": {\n");

        List<String> animGroups = new ArrayList<>();

        // Parent scales
        animGroups.add(String.format(Locale.ROOT,
            "        \"outer_splash\": {\"scale\": [[0.0, \"linear\", 0.8, 1.0, 0.8], [0.9, \"linear\", 1.0, 1.0, 1.0]]}"));
        animGroups.add(String.format(Locale.ROOT,
            "        \"outer_ring\": {\"scale\": [[0.0, \"linear\", 0.8, 1.0, 0.8], [0.9, \"linear\", 1.0, 1.0, 1.0]]}"));
        animGroups.add(String.format(Locale.ROOT,
            "        \"inner_splash\": {\"scale\": [[0.0, \"step\", 0, 0, 0], [0.4, \"linear\", 0.8, 1.0, 0.8], [1.3, \"linear\", 1.0, 1.0, 1.0], [1.31, \"step\", 0, 0, 0]]}"));
        animGroups.add(String.format(Locale.ROOT,
            "        \"inner_ring\": {\"scale\": [[0.0, \"step\", 0, 0, 0], [0.4, \"linear\", 0.8, 1.0, 0.8], [1.3, \"linear\", 1.0, 1.0, 1.0], [1.31, \"step\", 0, 0, 0]]}"));

        // Frame animators
        for (int f = 1; f <= 9; f++)
        {
            float tStart = (f - 1) * 0.1f;
            float tEnd = f * 0.1f;
            animGroups.add(bbsStepFrame("outer_f" + f, tStart, tEnd));
            animGroups.add(bbsStepFrame("ring_f" + f, tStart, tEnd));
        }

        for (int f = 1; f <= 9; f++)
        {
            float tStart = 0.4f + (f - 1) * 0.1f;
            float tEnd = 0.4f + f * 0.1f;
            animGroups.add(bbsStepFrame("inner_f" + f, tStart, tEnd));
            animGroups.add(bbsStepFrame("inner_ring_f" + f, tStart, tEnd));
        }

        // Droplet animators
        for (int d = 1; d <= 8; d++)
        {
            double angle = (d - 1) * (2.0 * Math.PI / 8.0) + 0.1;
            float vHoriz = 10.0f + (d % 3) * 2.0f;
            float vx = (float) (Math.cos(angle) * vHoriz);
            float vz = (float) (Math.sin(angle) * vHoriz);
            float vy0 = 18.0f + (d % 2) * 4.0f;
            float g = 45.0f;
            float tEnd = Math.min(0.85f, 2.0f * vy0 / g);
            animGroups.add(bbsDroplet("drop_" + d, 0.0f, tEnd, vx, vz, vy0, g));
        }

        for (int d = 9; d <= 16; d++)
        {
            int idx = d - 8;
            double angle = (idx - 1) * (2.0 * Math.PI / 8.0) + 0.35;
            float vHoriz = 3.5f + (idx % 3) * 1.5f;
            float vx = (float) (Math.cos(angle) * vHoriz);
            float vz = (float) (Math.sin(angle) * vHoriz);
            float vy0 = 30.0f + (idx % 3) * 3.0f;
            float g = 52.0f;
            float tStart = 0.4f;
            float tEnd = Math.min(1.3f, tStart + 2.0f * vy0 / g);
            animGroups.add(bbsDroplet("drop_" + d, tStart, tEnd, vx, vz, vy0, g));
        }

        sb.append(String.join(",\n", animGroups));
        sb.append("\n      }\n");
        sb.append("    }\n");
        sb.append("  },\n");

        // Model hierarchy
        sb.append("  \"model\": {\n");
        sb.append("    \"texture\": [256, 64],\n");
        sb.append("    \"groups\": {\n");

        List<String> modelGroups = new ArrayList<>();

        modelGroups.add("      \"root\": {\"origin\": [0, 0, 0]}");
        modelGroups.add("      \"outer_splash\": {\"origin\": [0, 0, 0], \"parent\": \"root\"}");
        modelGroups.add("      \"outer_ring\": {\"origin\": [0, 0, 0], \"parent\": \"root\"}");
        modelGroups.add("      \"inner_splash\": {\"origin\": [0, 0, 0], \"parent\": \"root\"}");
        modelGroups.add("      \"inner_ring\": {\"origin\": [0, 0, 0], \"parent\": \"root\"}");
        modelGroups.add("      \"droplets\": {\"origin\": [0, 0, 0], \"parent\": \"root\"}");

        float outerR = 7.68f;
        float outerH = 8.0f;
        for (int f = 1; f <= 9; f++)
        {
            int u1 = (f - 1) * 16;
            int u2 = u1 + 16;
            modelGroups.add(bbsWallGroup("outer_f" + f, "outer_splash", outerR, outerH, u1, 0, u2, 32));
        }

        for (int f = 1; f <= 9; f++)
        {
            int u1 = (f - 1) * 16;
            int u2 = u1 + 16;
            modelGroups.add(bbsPlaneGroup("ring_f" + f, "outer_ring", outerR, 0.05f, u1, 32, u2, 48));
        }

        float innerR = 5.07f;
        float innerH = 16.0f;
        for (int f = 1; f <= 9; f++)
        {
            int u1 = (f - 1) * 16;
            int u2 = u1 + 16;
            modelGroups.add(bbsWallGroup("inner_f" + f, "inner_splash", innerR, innerH, u1, 0, u2, 32));
        }

        for (int f = 1; f <= 9; f++)
        {
            int u1 = (f - 1) * 16;
            int u2 = u1 + 16;
            modelGroups.add(bbsPlaneGroup("inner_ring_f" + f, "inner_ring", innerR, 0.08f, u1, 32, u2, 48));
        }

        for (int d = 1; d <= 16; d++)
        {
            modelGroups.add(bbsCubeGroup("drop_" + d, "droplets", 144, 32, 148, 36));
        }

        sb.append(String.join(",\n", modelGroups));
        sb.append("\n    }\n");
        sb.append("  }\n");
        sb.append("}\n");

        return sb.toString();
    }

    private static String bbsStepFrame(String name, float tStart, float tEnd)
    {
        List<String> keys = new ArrayList<>();
        if (tStart > 0.001f)
        {
            keys.add("[0.0, \"step\", 0, 0, 0]");
        }
        keys.add(String.format(Locale.ROOT, "[%.2f, \"step\", 1, 1, 1]", tStart));
        keys.add(String.format(Locale.ROOT, "[%.2f, \"step\", 0, 0, 0]", tEnd));
        return String.format(Locale.ROOT, "        \"%s\": {\"scale\": [%s]}", name, String.join(", ", keys));
    }

    private static String bbsDroplet(String name, float tStart, float tEnd, float vx, float vz, float vy0, float g)
    {
        List<String> scaleKeys = new ArrayList<>();
        if (tStart > 0.001f)
        {
            scaleKeys.add("[0.0, \"step\", 0, 0, 0]");
        }
        scaleKeys.add(String.format(Locale.ROOT, "[%.2f, \"step\", 1, 1, 1]", tStart));
        scaleKeys.add(String.format(Locale.ROOT, "[%.2f, \"step\", 0, 0, 0]", tEnd));

        List<String> posKeys = new ArrayList<>();
        int steps = 6;
        for (int i = 0; i <= steps; i++)
        {
            float dt = (tEnd - tStart) * (i / (float) steps);
            float time = tStart + dt;
            float px = vx * dt;
            float pz = vz * dt;
            float py = Math.max(0.0f, vy0 * dt - 0.5f * g * dt * dt);
            posKeys.add(String.format(Locale.ROOT, "[%.2f, \"linear\", %.2f, %.2f, %.2f]", time, px, py, pz));
        }

        return String.format(Locale.ROOT,
            "        \"%s\": {\"scale\": [%s], \"translate\": [%s]}",
            name, String.join(", ", scaleKeys), String.join(", ", posKeys));
    }

    private static String bbsWallGroup(String name, String parent, float r, float h, int u1, int v1, int u2, int v2)
    {
        String wCube = String.format(Locale.ROOT,
            "{\"origin\":[0,0,0],\"from\":[%.2f,0,%.2f],\"size\":[0.01,%.2f,%.2f],\"uvs\":{\"left\":[%d,%d,%d,%d],\"right\":[%d,%d,%d,%d]}}",
            -r, -r, h, 2 * r, u1, v1, u2, v2, u2, v1, u1, v2);
        String sCube = String.format(Locale.ROOT,
            "{\"origin\":[0,0,0],\"from\":[%.2f,0,%.2f],\"size\":[%.2f,%.2f,0.01],\"uvs\":{\"back\":[%d,%d,%d,%d],\"front\":[%d,%d,%d,%d]}}",
            -r, r, 2 * r, h, u1, v1, u2, v2, u2, v1, u1, v2);
        String eCube = String.format(Locale.ROOT,
            "{\"origin\":[0,0,0],\"from\":[%.2f,0,%.2f],\"size\":[0.01,%.2f,%.2f],\"uvs\":{\"right\":[%d,%d,%d,%d],\"left\":[%d,%d,%d,%d]}}",
            r, -r, h, 2 * r, u1, v1, u2, v2, u2, v1, u1, v2);
        String nCube = String.format(Locale.ROOT,
            "{\"origin\":[0,0,0],\"from\":[%.2f,0,%.2f],\"size\":[%.2f,%.2f,0.01],\"uvs\":{\"front\":[%d,%d,%d,%d],\"back\":[%d,%d,%d,%d]}}",
            -r, -r, 2 * r, h, u1, v1, u2, v2, u2, v1, u1, v2);

        return String.format(Locale.ROOT,
            "      \"%s\": {\"origin\": [0, 0, 0], \"parent\": \"%s\", \"cubes\": [%s, %s, %s, %s]}",
            name, parent, wCube, sCube, eCube, nCube);
    }

    private static String bbsPlaneGroup(String name, String parent, float r, float y, int u1, int v1, int u2, int v2)
    {
        String plane = String.format(Locale.ROOT,
            "{\"origin\":[0,0,0],\"from\":[%.2f,%.2f,%.2f],\"size\":[%.2f,0.01,%.2f],\"uvs\":{\"top\":[%d,%d,%d,%d],\"bottom\":[%d,%d,%d,%d]}}",
            -r, y, -r, 2 * r, 2 * r, u1, v1, u2, v2, u1, v1, u2, v2);
        return String.format(Locale.ROOT,
            "      \"%s\": {\"origin\": [0, 0, 0], \"parent\": \"%s\", \"cubes\": [%s]}",
            name, parent, plane);
    }

    private static String bbsCubeGroup(String name, String parent, int u1, int v1, int u2, int v2)
    {
        String c = String.format(Locale.ROOT,
            "{\"origin\":[0,0,0],\"from\":[-0.5,-0.5,-0.5],\"size\":[1,1,1],\"uvs\":{\"front\":[%d,%d,%d,%d],\"back\":[%d,%d,%d,%d],\"right\":[%d,%d,%d,%d],\"left\":[%d,%d,%d,%d],\"top\":[%d,%d,%d,%d],\"bottom\":[%d,%d,%d,%d]}}",
            u1, v1, u2, v2, u1, v1, u2, v2, u1, v1, u2, v2, u1, v1, u2, v2, u1, v1, u2, v2, u1, v1, u2, v2);
        return String.format(Locale.ROOT,
            "      \"%s\": {\"origin\": [0, 0, 0], \"parent\": \"%s\", \"cubes\": [%s]}",
            name, parent, c);
    }

    private static String generateGeoJson()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"format_version\": \"1.12.0\",\n");
        sb.append("  \"minecraft:geometry\": [\n");
        sb.append("    {\n");
        sb.append("      \"description\": {\n");
        sb.append("        \"identifier\": \"geometry.water_splash\",\n");
        sb.append("        \"texture_width\": 256,\n");
        sb.append("        \"texture_height\": 64,\n");
        sb.append("        \"visible_bounds_width\": 3,\n");
        sb.append("        \"visible_bounds_height\": 3,\n");
        sb.append("        \"visible_bounds_offset\": [0, 1.5, 0]\n");
        sb.append("      },\n");
        sb.append("      \"bones\": [\n");

        List<String> bones = new ArrayList<>();
        bones.add("        {\"name\": \"root\", \"pivot\": [0, 0, 0]}");
        bones.add("        {\"name\": \"outer_splash\", \"parent\": \"root\", \"pivot\": [0, 0, 0]}");
        bones.add("        {\"name\": \"outer_ring\", \"parent\": \"root\", \"pivot\": [0, 0, 0]}");
        bones.add("        {\"name\": \"inner_splash\", \"parent\": \"root\", \"pivot\": [0, 0, 0]}");
        bones.add("        {\"name\": \"inner_ring\", \"parent\": \"root\", \"pivot\": [0, 0, 0]}");
        bones.add("        {\"name\": \"droplets\", \"parent\": \"root\", \"pivot\": [0, 0, 0]}");

        float outerR = 7.68f;
        float outerH = 8.0f;
        for (int f = 1; f <= 9; f++)
        {
            int u1 = (f - 1) * 16;
            bones.add(geoWallBone("outer_f" + f, "outer_splash", outerR, outerH, u1, 0));
            bones.add(geoPlaneBone("ring_f" + f, "outer_ring", outerR, 0.05f, u1, 32));
        }

        float innerR = 5.07f;
        float innerH = 16.0f;
        for (int f = 1; f <= 9; f++)
        {
            int u1 = (f - 1) * 16;
            bones.add(geoWallBone("inner_f" + f, "inner_splash", innerR, innerH, u1, 0));
            bones.add(geoPlaneBone("inner_ring_f" + f, "inner_ring", innerR, 0.08f, u1, 32));
        }

        for (int d = 1; d <= 16; d++)
        {
            bones.add(String.format(Locale.ROOT,
                "        {\"name\": \"drop_%d\", \"parent\": \"droplets\", \"pivot\": [0, 0, 0], \"cubes\": [{\"origin\": [-0.5, -0.5, -0.5], \"size\": [1, 1, 1], \"uv\": [144, 32]}]}",
                d));
        }

        sb.append(String.join(",\n", bones));
        sb.append("\n      ]\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String geoWallBone(String name, String parent, float r, float h, int u, int v)
    {
        return String.format(Locale.ROOT,
            "        {\"name\": \"%s\", \"parent\": \"%s\", \"pivot\": [0, 0, 0], \"cubes\": [" +
            "{\"origin\": [%.2f, 0, %.2f], \"size\": [0.01, %.2f, %.2f], \"uv\": [%d, %d]}," +
            "{\"origin\": [%.2f, 0, %.2f], \"size\": [%.2f, %.2f, 0.01], \"uv\": [%d, %d]}," +
            "{\"origin\": [%.2f, 0, %.2f], \"size\": [0.01, %.2f, %.2f], \"uv\": [%d, %d]}," +
            "{\"origin\": [%.2f, 0, %.2f], \"size\": [%.2f, %.2f, 0.01], \"uv\": [%d, %d]}" +
            "]}",
            name, parent,
            -r, -r, h, 2 * r, u, v,
            -r, r, 2 * r, h, u, v,
            r, -r, h, 2 * r, u, v,
            -r, -r, 2 * r, h, u, v);
    }

    private static String geoPlaneBone(String name, String parent, float r, float y, int u, int v)
    {
        return String.format(Locale.ROOT,
            "        {\"name\": \"%s\", \"parent\": \"%s\", \"pivot\": [0, 0, 0], \"cubes\": [" +
            "{\"origin\": [%.2f, %.2f, %.2f], \"size\": [%.2f, 0.01, %.2f], \"uv\": [%d, %d]}" +
            "]}",
            name, parent, -r, y, -r, 2 * r, 2 * r, u, v);
    }

    private static String generateBedrockAnimationJson()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"format_version\": \"1.8.0\",\n");
        sb.append("  \"animations\": {\n");
        sb.append("    \"animation.water_splash.splash\": {\n");
        sb.append("      \"loop\": false,\n");
        sb.append("      \"animation_length\": 1.35,\n");
        sb.append("      \"bones\": {\n");

        List<String> bones = new ArrayList<>();

        bones.add("        \"outer_splash\": {\"scale\": {\"0.0\": [0.8, 1.0, 0.8], \"0.9\": [1.0, 1.0, 1.0]}}");
        bones.add("        \"outer_ring\": {\"scale\": {\"0.0\": [0.8, 1.0, 0.8], \"0.9\": [1.0, 1.0, 1.0]}}");
        bones.add("        \"inner_splash\": {\"scale\": {\"0.0\": [0, 0, 0], \"0.4\": [0.8, 1.0, 0.8], \"1.3\": [1.0, 1.0, 1.0], \"1.31\": [0, 0, 0]}}");
        bones.add("        \"inner_ring\": {\"scale\": {\"0.0\": [0, 0, 0], \"0.4\": [0.8, 1.0, 0.8], \"1.3\": [1.0, 1.0, 1.0], \"1.31\": [0, 0, 0]}}");

        for (int f = 1; f <= 9; f++)
        {
            float tStart = (f - 1) * 0.1f;
            float tEnd = f * 0.1f;
            bones.add(bedrockStepFrame("outer_f" + f, tStart, tEnd));
            bones.add(bedrockStepFrame("ring_f" + f, tStart, tEnd));
        }

        for (int f = 1; f <= 9; f++)
        {
            float tStart = 0.4f + (f - 1) * 0.1f;
            float tEnd = 0.4f + f * 0.1f;
            bones.add(bedrockStepFrame("inner_f" + f, tStart, tEnd));
            bones.add(bedrockStepFrame("inner_ring_f" + f, tStart, tEnd));
        }

        for (int d = 1; d <= 8; d++)
        {
            double angle = (d - 1) * (2.0 * Math.PI / 8.0) + 0.1;
            float vHoriz = 10.0f + (d % 3) * 2.0f;
            float vx = (float) (Math.cos(angle) * vHoriz);
            float vz = (float) (Math.sin(angle) * vHoriz);
            float vy0 = 18.0f + (d % 2) * 4.0f;
            float g = 45.0f;
            float tEnd = Math.min(0.85f, 2.0f * vy0 / g);
            bones.add(bedrockDroplet("drop_" + d, 0.0f, tEnd, vx, vz, vy0, g));
        }

        for (int d = 9; d <= 16; d++)
        {
            int idx = d - 8;
            double angle = (idx - 1) * (2.0 * Math.PI / 8.0) + 0.35;
            float vHoriz = 3.5f + (idx % 3) * 1.5f;
            float vx = (float) (Math.cos(angle) * vHoriz);
            float vz = (float) (Math.sin(angle) * vHoriz);
            float vy0 = 30.0f + (idx % 3) * 3.0f;
            float g = 52.0f;
            float tStart = 0.4f;
            float tEnd = Math.min(1.3f, tStart + 2.0f * vy0 / g);
            bones.add(bedrockDroplet("drop_" + d, tStart, tEnd, vx, vz, vy0, g));
        }

        sb.append(String.join(",\n", bones));
        sb.append("\n      }\n");
        sb.append("    }\n");
        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String bedrockStepFrame(String name, float tStart, float tEnd)
    {
        List<String> entries = new ArrayList<>();
        if (tStart > 0.001f)
        {
            entries.add("\"0.0\": [0, 0, 0]");
        }
        entries.add(String.format(Locale.ROOT, "\"%.2f\": [1, 1, 1]", tStart));
        entries.add(String.format(Locale.ROOT, "\"%.2f\": [0, 0, 0]", tEnd));
        return String.format(Locale.ROOT, "        \"%s\": {\"scale\": {%s}}", name, String.join(", ", entries));
    }

    private static String bedrockDroplet(String name, float tStart, float tEnd, float vx, float vz, float vy0, float g)
    {
        List<String> scaleEntries = new ArrayList<>();
        if (tStart > 0.001f)
        {
            scaleEntries.add("\"0.0\": [0, 0, 0]");
        }
        scaleEntries.add(String.format(Locale.ROOT, "\"%.2f\": [1, 1, 1]", tStart));
        scaleEntries.add(String.format(Locale.ROOT, "\"%.2f\": [0, 0, 0]", tEnd));

        List<String> posEntries = new ArrayList<>();
        int steps = 6;
        for (int i = 0; i <= steps; i++)
        {
            float dt = (tEnd - tStart) * (i / (float) steps);
            float time = tStart + dt;
            float px = vx * dt;
            float pz = vz * dt;
            float py = Math.max(0.0f, vy0 * dt - 0.5f * g * dt * dt);
            posEntries.add(String.format(Locale.ROOT, "\"%.2f\": [%.2f, %.2f, %.2f]", time, px, py, pz));
        }

        return String.format(Locale.ROOT,
            "        \"%s\": {\"scale\": {%s}, \"position\": {%s}}",
            name, String.join(", ", scaleEntries), String.join(", ", posEntries));
    }
}
