package ans.bedrock;

import ans.bedrock.BedrockGeometry.Bone;
import ans.bedrock.BedrockGeometry.Cube;
import ans.bedrock.BedrockGeometry.FaceDir;
import ans.bedrock.BedrockGeometry.FaceUv;
import ans.bedrock.BedrockGeometry.Locator;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Parses Bedrock 1.12+ ({@code minecraft:geometry}) and legacy 1.8/1.10 ({@code geometry.*})
 * JSON files into normalized {@link BedrockGeometry} models, plus provides fallbacks for the 5
 * vanilla built-in geometry identifiers referenced by Actions &amp; Stuff.
 */
public final class BedrockGeometryLoader {

    private BedrockGeometryLoader() {
    }

    public static Map<String, BedrockGeometry> parseFile(String jsonText) {
        Map<String, BedrockGeometry> out = new LinkedHashMap<>();
        if (jsonText == null || jsonText.isBlank()) {
            return out;
        }
        JsonObject root = JsonParser.parseString(jsonText).getAsJsonObject();

        // Format >= 1.12.0: "minecraft:geometry": [ ... ]
        if (root.has("minecraft:geometry") && root.get("minecraft:geometry").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("minecraft:geometry")) {
                if (!el.isJsonObject()) continue;
                BedrockGeometry geo = parseModernGeometry(el.getAsJsonObject());
                if (geo != null) {
                    out.put(geo.identifier.toLowerCase(Locale.ROOT), geo);
                }
            }
            return out;
        }

        // Legacy 1.8 / 1.10 format: top-level "geometry.<name>": { ... }
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            String key = entry.getKey();
            if ("format_version".equals(key) || !entry.getValue().isJsonObject()) continue;
            // Strip optional ":geometry.parent" suffix on key name when registering primary id
            String primaryId = key.contains(":") ? key.substring(0, key.indexOf(':')) : key;
            BedrockGeometry geo = parseLegacyGeometry(primaryId, entry.getValue().getAsJsonObject());
            if (geo != null) {
                out.put(primaryId.toLowerCase(Locale.ROOT), geo);
            }
        }
        return out;
    }

    private static BedrockGeometry parseModernGeometry(JsonObject obj) {
        JsonObject desc = obj.has("description") && obj.get("description").isJsonObject()
            ? obj.getAsJsonObject("description") : new JsonObject();
        String id = str(desc, "identifier", "geometry.unknown");
        int texW = Math.max(1, integer(desc, "texture_width", 64));
        int texH = Math.max(1, integer(desc, "texture_height", 64));
        float vbW = flt(desc, "visible_bounds_width", 2f);
        float vbH = flt(desc, "visible_bounds_height", 2f);
        float[] vbOff = vec3(desc.get("visible_bounds_offset"), 0f, 0f, 0f);

        BedrockGeometry geo = new BedrockGeometry(id, texW, texH, vbW, vbH, vbOff);
        if (obj.has("bones") && obj.get("bones").isJsonArray()) {
            parseBones(obj.getAsJsonArray("bones"), geo, texW, texH);
        }
        linkHierarchy(geo);
        return geo;
    }

    private static BedrockGeometry parseLegacyGeometry(String id, JsonObject obj) {
        int texW = Math.max(1, integer(obj, "texturewidth", integer(obj, "texture_width", 64)));
        int texH = Math.max(1, integer(obj, "textureheight", integer(obj, "texture_height", 64)));
        float vbW = flt(obj, "visible_bounds_width", 2f);
        float vbH = flt(obj, "visible_bounds_height", 2f);
        float[] vbOff = vec3(obj.get("visible_bounds_offset"), 0f, 0f, 0f);

        BedrockGeometry geo = new BedrockGeometry(id, texW, texH, vbW, vbH, vbOff);
        if (obj.has("bones") && obj.get("bones").isJsonArray()) {
            parseBones(obj.getAsJsonArray("bones"), geo, texW, texH);
        }
        linkHierarchy(geo);
        return geo;
    }

    private static void parseBones(JsonArray bonesArr, BedrockGeometry geo, int texW, int texH) {
        for (JsonElement bEl : bonesArr) {
            if (!bEl.isJsonObject()) continue;
            JsonObject bObj = bEl.getAsJsonObject();
            String name = str(bObj, "name", "");
            if (name.isEmpty()) continue;
            String parent = bObj.has("parent") && bObj.get("parent").isJsonPrimitive() ? bObj.get("parent").getAsString() : null;
            float[] rawPivot = vec3(bObj.get("pivot"), 0f, 0f, 0f);
            float[] rawRot = vec3(bObj.has("rotation") ? bObj.get("rotation") : bObj.get("bind_pose_rotation"), 0f, 0f, 0f);
            boolean boneMirror = bObj.has("mirror") && bObj.get("mirror").isJsonPrimitive() && bObj.get("mirror").getAsBoolean();
            String binding = bObj.has("binding") && bObj.get("binding").isJsonPrimitive() ? bObj.get("binding").getAsString() : null;

            // Bedrock -> Java coordinate reflection (mirror X): pivot.x = -pivot.x, rot.x = -rot.x, rot.y = -rot.y
            Bone bone = new Bone(
                name,
                parent,
                new float[]{-rawPivot[0], rawPivot[1], rawPivot[2]},
                new float[]{-rawRot[0], -rawRot[1], rawRot[2]},
                boneMirror,
                binding
            );

            if (bObj.has("cubes") && bObj.get("cubes").isJsonArray()) {
                for (JsonElement cEl : bObj.getAsJsonArray("cubes")) {
                    if (!cEl.isJsonObject()) continue;
                    Cube cube = parseCube(cEl.getAsJsonObject(), boneMirror, bone.pivot, texW, texH);
                    if (cube != null) {
                        bone.cubes.add(cube);
                    }
                }
            }

            if (bObj.has("locators") && bObj.get("locators").isJsonObject()) {
                for (Map.Entry<String, JsonElement> lEnt : bObj.getAsJsonObject("locators").entrySet()) {
                    float[] off;
                    float[] lrot = new float[]{0f, 0f, 0f};
                    if (lEnt.getValue().isJsonArray()) {
                        off = vec3(lEnt.getValue(), 0f, 0f, 0f);
                    } else if (lEnt.getValue().isJsonObject()) {
                        JsonObject lo = lEnt.getValue().getAsJsonObject();
                        off = vec3(lo.get("offset"), 0f, 0f, 0f);
                        lrot = vec3(lo.get("rotation"), 0f, 0f, 0f);
                    } else {
                        continue;
                    }
                    bone.locators.add(new Locator(
                        lEnt.getKey(),
                        new float[]{-off[0], off[1], off[2]},
                        new float[]{-lrot[0], -lrot[1], lrot[2]}
                    ));
                }
            }

            geo.allBones.add(bone);
            geo.bonesByName.put(bone.nameLower, bone);
        }
    }

    private static Cube parseCube(JsonObject cObj, boolean boneMirror, float[] bonePivot, int texW, int texH) {
        float[] origin = vec3(cObj.get("origin"), 0f, 0f, 0f);
        float[] size = vec3(cObj.get("size"), 0f, 0f, 0f);
        float inflate = flt(cObj, "inflate", 0f);
        boolean mirror = cObj.has("mirror") && cObj.get("mirror").isJsonPrimitive()
            ? cObj.get("mirror").getAsBoolean()
            : boneMirror;

        float[] rawPivot = cObj.has("pivot") ? vec3(cObj.get("pivot"), 0f, 0f, 0f) : null;
        float[] pivot = rawPivot != null
            ? new float[]{-rawPivot[0], rawPivot[1], rawPivot[2]}
            : bonePivot;
        float[] rawRot = vec3(cObj.get("rotation"), 0f, 0f, 0f);
        float[] rotation = new float[]{-rawRot[0], -rawRot[1], rawRot[2]};

        // In Java right-handed space: mirror X means origin.x becomes [-origin.x - size.x .. -origin.x]
        float minX = -origin[0] - size[0] - inflate;
        float minY = origin[1] - inflate;
        float minZ = origin[2] - inflate;
        float maxX = -origin[0] + inflate;
        float maxY = origin[1] + size[1] + inflate;
        float maxZ = origin[2] + size[2] + inflate;

        Cube cube = new Cube(minX, minY, minZ, maxX, maxY, maxZ, pivot, rotation, mirror);

        JsonElement uvEl = cObj.get("uv");
        if (uvEl != null && uvEl.isJsonArray()) {
            JsonArray arr = uvEl.getAsJsonArray();
            float u = arr.size() > 0 ? arr.get(0).getAsFloat() : 0f;
            float v = arr.size() > 1 ? arr.get(1).getAsFloat() : 0f;
            assignBoxUv(cube, u, v, size[0], size[1], size[2], mirror, texW, texH);
        } else if (uvEl != null && uvEl.isJsonObject()) {
            assignPerFaceUv(cube, uvEl.getAsJsonObject(), mirror, texW, texH);
        }
        return cube;
    }

    private static void assignBoxUv(Cube cube, float u, float v, float sx, float sy, float sz,
                                    boolean mirror, int texW, int texH) {
        float w = (float) Math.floor(sx);
        float h = (float) Math.floor(sy);
        float d = (float) Math.floor(sz);

        // Standard Bedrock/Blockbench box-UV layout matching Java X-mirrored coordinates
        FaceUv north = uvRect(u + d, v + d, w, h, texW, texH, mirror);
        FaceUv south = uvRect(u + d + w + d, v + d, w, h, texW, texH, mirror);
        FaceUv westSide = uvRect(u + d + w, v + d, d, h, texW, texH, mirror);
        FaceUv eastSide = uvRect(u, v + d, d, h, texW, texH, mirror);
        FaceUv up = uvRect(u + d, v, w, d, texW, texH, mirror);
        FaceUv down = uvRect(u + d + w, v, w, d, texW, texH, mirror);

        cube.faces.put(FaceDir.NORTH, north);
        cube.faces.put(FaceDir.SOUTH, south);
        cube.faces.put(FaceDir.EAST, mirror ? westSide : eastSide);
        cube.faces.put(FaceDir.WEST, mirror ? eastSide : westSide);
        cube.faces.put(FaceDir.UP, up);
        cube.faces.put(FaceDir.DOWN, down);
    }

    private static FaceUv uvRect(float u, float v, float uw, float vh, int texW, int texH, boolean mirrorU) {
        float u0 = mirrorU ? (u + uw) / texW : u / texW;
        float u1 = mirrorU ? u / texW : (u + uw) / texW;
        return new FaceUv(u0, v / texH, u1, (v + vh) / texH, 0, null);
    }

    private static void assignPerFaceUv(Cube cube, JsonObject uvObj, boolean mirror, int texW, int texH) {
        for (FaceDir dir : FaceDir.values()) {
            String jsonKey = switch (dir) {
                case NORTH -> "north";
                case SOUTH -> "south";
                case UP -> "up";
                case DOWN -> "down";
                case EAST -> "east";
                case WEST -> "west";
            };
            if (!uvObj.has(jsonKey) || !uvObj.get(jsonKey).isJsonObject()) {
                continue;
            }
            JsonObject fObj = uvObj.getAsJsonObject(jsonKey);
            float[] uv = vec2(fObj.get("uv"));
            float[] uvSize = fObj.has("uv_size") ? vec2(fObj.get("uv_size")) : new float[]{0f, 0f};
            int rot = integer(fObj, "uv_rotation", 0);
            String mat = fObj.has("material_instance") && fObj.get("material_instance").isJsonPrimitive()
                ? fObj.get("material_instance").getAsString() : null;

            float u0 = uv[0] / texW;
            float v0 = uv[1] / texH;
            float u1 = (uv[0] + uvSize[0]) / texW;
            float v1 = (uv[1] + uvSize[1]) / texH;
            cube.faces.put(dir, new FaceUv(u0, v0, u1, v1, rot, mat));
        }
    }

    private static void linkHierarchy(BedrockGeometry geo) {
        for (Bone b : geo.allBones) {
            if (b.parent != null && !b.parent.isEmpty()) {
                Bone p = geo.bonesByName.get(b.parent.toLowerCase(Locale.ROOT));
                if (p != null) {
                    p.children.add(b);
                    continue;
                }
            }
            geo.rootBones.add(b);
        }
    }

    /** Registers vanilla built-in geometries referenced in pack entity/attachable JSONs. */
    public static void addBuiltinFallbacks(Map<String, BedrockGeometry> registry) {
        registry.computeIfAbsent("geometry.shield", k -> {
            BedrockGeometry g = new BedrockGeometry("geometry.shield", 64, 64, 1.5f, 1.5f, new float[]{0f, 0f, 0f});
            Bone root = new Bone("shield", null, new float[]{0f, 0f, 0f}, new float[]{0f, 0f, 0f}, false, "q.item_slot_to_bone_name(c.item_slot)");
            root.cubes.add(new Cube(-6f, -11f, -2f, 6f, 11f, -1f, root.pivot, root.rotation, false));
            root.cubes.add(new Cube(-1f, -3f, -1f, 1f, 3f, 5f, root.pivot, root.rotation, false));
            assignBoxUv(root.cubes.get(0), 0f, 0f, 12f, 22f, 1f, false, 64, 64);
            assignBoxUv(root.cubes.get(1), 26f, 0f, 2f, 6f, 6f, false, 64, 64);
            g.allBones.add(root);
            g.rootBones.add(root);
            g.bonesByName.put(root.nameLower, root);
            return g;
        });
        registry.computeIfAbsent("geometry.cape", k -> {
            BedrockGeometry g = new BedrockGeometry("geometry.cape", 64, 32, 1.5f, 2f, new float[]{0f, 1f, 0f});
            Bone root = new Bone("cape", "body", new float[]{0f, 24f, 3f}, new float[]{0f, 0f, 0f}, false, null);
            Cube c = new Cube(-5f, 8f, 3f, 5f, 24f, 4f, root.pivot, root.rotation, false);
            assignBoxUv(c, 0f, 0f, 10f, 16f, 1f, false, 64, 32);
            root.cubes.add(c);
            g.allBones.add(root);
            g.rootBones.add(root);
            g.bonesByName.put(root.nameLower, root);
            return g;
        });
        registry.computeIfAbsent("geometry.elytra", k -> {
            BedrockGeometry g = new BedrockGeometry("geometry.elytra", 64, 32, 1.5f, 2f, new float[]{0f, 1f, 0f});
            Bone rw = new Bone("right_wing", "body", new float[]{-5f, 24f, 2f}, new float[]{0f, 0f, 0f}, false, null);
            Cube rc = new Cube(-15f, 4f, 2f, -5f, 24f, 4f, rw.pivot, rw.rotation, false);
            assignBoxUv(rc, 22f, 0f, 10f, 20f, 2f, false, 64, 32);
            rw.cubes.add(rc);
            Bone lw = new Bone("left_wing", "body", new float[]{5f, 24f, 2f}, new float[]{0f, 0f, 0f}, true, null);
            Cube lc = new Cube(5f, 4f, 2f, 15f, 24f, 4f, lw.pivot, lw.rotation, true);
            assignBoxUv(lc, 22f, 0f, 10f, 20f, 2f, true, 64, 32);
            lw.cubes.add(lc);
            g.allBones.add(rw);
            g.allBones.add(lw);
            g.rootBones.add(rw);
            g.rootBones.add(lw);
            g.bonesByName.put(rw.nameLower, rw);
            g.bonesByName.put(lw.nameLower, lw);
            return g;
        });
        registry.computeIfAbsent("geometry.humanoid.custom", k -> createHumanoidGeometry("geometry.humanoid.custom", false));
        registry.computeIfAbsent("geometry.humanoid.customslim", k -> createHumanoidGeometry("geometry.humanoid.customslim", true));
        registry.computeIfAbsent("geometry.evocation_fang", k -> {
            BedrockGeometry g = new BedrockGeometry("geometry.evocation_fang", 64, 32, 1f, 1.5f, new float[]{0f, 0.75f, 0f});
            Bone base = new Bone("base", null, new float[]{0f, 24f, 0f}, new float[]{0f, 0f, 0f}, false, null);
            Cube bc = new Cube(-5f, 0f, -5f, 5f, 12f, 5f, base.pivot, base.rotation, false);
            assignBoxUv(bc, 0f, 0f, 10f, 12f, 10f, false, 64, 32);
            base.cubes.add(bc);
            g.allBones.add(base);
            g.rootBones.add(base);
            g.bonesByName.put(base.nameLower, base);
            return g;
        });
    }

    private static BedrockGeometry createHumanoidGeometry(String id, boolean slim) {
        final int texW = 64;
        final int texH = 64;
        BedrockGeometry geom = new BedrockGeometry(id, texW, texH, 1.5f, 2f, new float[]{0f, 1f, 0f});

        addEmptyBone(geom, "root", null, 0f, 0f, 0f);
        addEmptyBone(geom, "waist", "root", 0f, 12f, 0f);
        addHumanoidBone(geom, "body", "waist", 0f, 24f, 0f, -4f, 12f, -2f, 8f, 12f, 4f, 16f, 16f, 0f, false, texW, texH);
        addHumanoidBone(geom, "jacket", "body", 0f, 24f, 0f, -4f, 12f, -2f, 8f, 12f, 4f, 16f, 32f, 0.25f, false, texW, texH);
        addHumanoidBone(geom, "head", "body", 0f, 24f, 0f, -4f, 24f, -4f, 8f, 8f, 8f, 0f, 0f, 0f, false, texW, texH);
        addHumanoidBone(geom, "hat", "head", 0f, 24f, 0f, -4f, 24f, -4f, 8f, 8f, 8f, 32f, 0f, 0.5f, false, texW, texH);

        float armW = slim ? 3f : 4f;
        float rightArmX = slim ? -7f : -8f;
        float armPivotY = slim ? 21.5f : 22f;

        addHumanoidBone(geom, "rightArm", "body", -5f, armPivotY, 0f, rightArmX, 12f, -2f, armW, 12f, 4f, 40f, 16f, 0f, false, texW, texH);
        addHumanoidBone(geom, "rightSleeve", "rightArm", -5f, armPivotY, 0f, rightArmX, 12f, -2f, armW, 12f, 4f, 40f, 32f, 0.25f, false, texW, texH);
        addEmptyBone(geom, "rightItem", "rightArm", -6f, 15f, 1f);

        addHumanoidBone(geom, "leftArm", "body", 5f, armPivotY, 0f, 4f, 12f, -2f, armW, 12f, 4f, 32f, 48f, 0f, false, texW, texH);
        addHumanoidBone(geom, "leftSleeve", "leftArm", 5f, armPivotY, 0f, 4f, 12f, -2f, armW, 12f, 4f, 48f, 48f, 0.25f, false, texW, texH);
        addEmptyBone(geom, "leftItem", "leftArm", 6f, 15f, 1f);

        addHumanoidBone(geom, "rightLeg", "root", -1.9f, 12f, 0f, -3.9f, 0f, -2f, 4f, 12f, 4f, 0f, 16f, 0f, false, texW, texH);
        addHumanoidBone(geom, "rightPants", "rightLeg", -1.9f, 12f, 0f, -3.9f, 0f, -2f, 4f, 12f, 4f, 0f, 32f, 0.25f, false, texW, texH);

        addHumanoidBone(geom, "leftLeg", "root", 1.9f, 12f, 0f, -0.1f, 0f, -2f, 4f, 12f, 4f, 16f, 48f, 0f, false, texW, texH);
        addHumanoidBone(geom, "leftPants", "leftLeg", 1.9f, 12f, 0f, -0.1f, 0f, -2f, 4f, 12f, 4f, 0f, 48f, 0.25f, false, texW, texH);

        addEmptyBone(geom, "cape", "body", 0f, 24f, 3f);

        linkHierarchy(geom);
        return geom;
    }

    private static void addEmptyBone(BedrockGeometry geom, String name, String parent, float px, float py, float pz) {
        Bone bone = new Bone(name, parent, new float[]{-px, py, pz}, new float[]{0f, 0f, 0f}, false, null);
        geom.allBones.add(bone);
        geom.bonesByName.put(bone.nameLower, bone);
    }

    private static void addHumanoidBone(BedrockGeometry geom,
                                        String name,
                                        String parent,
                                        float px, float py, float pz,
                                        float ox, float oy, float oz,
                                        float sx, float sy, float sz,
                                        float u, float v,
                                        float inflate,
                                        boolean mirror,
                                        int texW, int texH) {
        Bone bone = new Bone(name, parent, new float[]{-px, py, pz}, new float[]{0f, 0f, 0f}, mirror, null);
        Cube cube = new Cube(-ox - sx - inflate, oy - inflate, oz - inflate,
            -ox + inflate, oy + sy + inflate, oz + sz + inflate, bone.pivot, bone.rotation, mirror);
        assignBoxUv(cube, u, v, sx, sy, sz, mirror, texW, texH);
        bone.cubes.add(cube);
        geom.allBones.add(bone);
        geom.bonesByName.put(bone.nameLower, bone);
    }

    private static String str(JsonObject o, String k, String def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : def;
    }

    private static int integer(JsonObject o, String k, int def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsInt() : def;
    }

    private static float flt(JsonObject o, String k, float def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsFloat() : def;
    }

    private static float[] vec2(JsonElement el) {
        if (el != null && el.isJsonArray()) {
            JsonArray a = el.getAsJsonArray();
            return new float[]{a.size() > 0 ? a.get(0).getAsFloat() : 0f, a.size() > 1 ? a.get(1).getAsFloat() : 0f};
        }
        return new float[]{0f, 0f};
    }

    private static float[] vec3(JsonElement el, float dx, float dy, float dz) {
        if (el != null && el.isJsonArray()) {
            JsonArray a = el.getAsJsonArray();
            return new float[]{
                a.size() > 0 ? a.get(0).getAsFloat() : dx,
                a.size() > 1 ? a.get(1).getAsFloat() : dy,
                a.size() > 2 ? a.get(2).getAsFloat() : dz
            };
        }
        return new float[]{dx, dy, dz};
    }
}
