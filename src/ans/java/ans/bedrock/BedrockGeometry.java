package ans.bedrock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Runtime representation of a parsed Bedrock {@code .geo.json} model including bones,
 * hierarchy, box/per-face UVs, cubes, and locators.
 */
public final class BedrockGeometry {

    public final String identifier;
    public final int textureWidth;
    public final int textureHeight;
    public final float visibleBoundsWidth;
    public final float visibleBoundsHeight;
    public final float[] visibleBoundsOffset;
    public final List<Bone> allBones = new ArrayList<>();
    public final List<Bone> rootBones = new ArrayList<>();
    public final Map<String, Bone> bonesByName = new HashMap<>();

    public BedrockGeometry(String identifier, int textureWidth, int textureHeight,
                           float visibleBoundsWidth, float visibleBoundsHeight, float[] visibleBoundsOffset) {
        this.identifier = identifier;
        this.textureWidth = Math.max(1, textureWidth);
        this.textureHeight = Math.max(1, textureHeight);
        this.visibleBoundsWidth = visibleBoundsWidth;
        this.visibleBoundsHeight = visibleBoundsHeight;
        this.visibleBoundsOffset = visibleBoundsOffset;
    }

    public Bone getBone(String name) {
        return name == null ? null : bonesByName.get(name.toLowerCase(Locale.ROOT));
    }

    public static final class Bone {
        public final String name;
        public final String nameLower;
        public final String parent;
        public final float[] pivot; // Converted Java pixels: (-bx, by, bz)
        public final float[] rotation; // Converted Java degrees: (-rx, -ry, rz)
        public final boolean mirror;
        public final String bindingExpr; // Optional MoLang binding string (attachables/armor)
        public final List<Cube> cubes = new ArrayList<>();
        public final List<Locator> locators = new ArrayList<>();
        public final List<Bone> children = new ArrayList<>();

        public Bone(String name, String parent, float[] pivot, float[] rotation, boolean mirror, String bindingExpr) {
            this.name = name;
            this.nameLower = name.toLowerCase(Locale.ROOT);
            this.parent = parent;
            this.pivot = pivot;
            this.rotation = rotation;
            this.mirror = mirror;
            this.bindingExpr = bindingExpr;
        }
    }

    public static final class Locator {
        public final String name;
        public final float[] offset; // Converted Java pixels (-x, y, z)
        public final float[] rotation;

        public Locator(String name, float[] offset, float[] rotation) {
            this.name = name;
            this.offset = offset;
            this.rotation = rotation;
        }
    }

    public enum FaceDir {
        NORTH, SOUTH, EAST, WEST, UP, DOWN
    }

    public static final class FaceUv {
        public float u0, v0, u1, v1;
        public int rotationDeg; // 0, 90, 180, 270
        public String materialInstance;

        public FaceUv(float u0, float v0, float u1, float v1, int rotationDeg, String materialInstance) {
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
            this.rotationDeg = rotationDeg;
            this.materialInstance = materialInstance;
        }
    }

    public static final class Cube {
        // Box bounds in Java space (right-handed, pixels, before 1/16 scale)
        public final float minX, minY, minZ;
        public final float maxX, maxY, maxZ;
        public final float[] pivot;
        public final float[] rotation;
        public final boolean mirror;
        public final Map<FaceDir, FaceUv> faces = new HashMap<>(6);

        public Cube(float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
                    float[] pivot, float[] rotation, boolean mirror) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.pivot = pivot;
            this.rotation = rotation;
            this.mirror = mirror;
        }
    }
}
