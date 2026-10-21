package mchorse.bbs_mod.ui.utils;

import org.joml.Matrix4f;

/**
 * In-memory, session-only slot holding one world-space matrix captured from a transform editor
 * ("copy world transform"). Kept off the OS/JSON clipboard on purpose — it is a transient, in-world
 * cache used to pin an element to the same world orientation/scale/position at another tick, much
 * like the image clipboard keeps pixels in memory rather than on the system clipboard.
 */
public class WorldTransformClipboard
{
    private static Matrix4f matrix;
    private static final java.util.Map<String, Matrix4f> bones = new java.util.LinkedHashMap<>();

    public static void set(Matrix4f matrix)
    {
        WorldTransformClipboard.matrix = matrix == null ? null : new Matrix4f(matrix);
        WorldTransformClipboard.bones.clear();
    }

    public static void setBones(java.util.Map<String, Matrix4f> boneMatrices)
    {
        WorldTransformClipboard.bones.clear();

        if (boneMatrices != null && !boneMatrices.isEmpty())
        {
            for (java.util.Map.Entry<String, Matrix4f> entry : boneMatrices.entrySet())
            {
                WorldTransformClipboard.bones.put(entry.getKey(), new Matrix4f(entry.getValue()));
            }

            WorldTransformClipboard.matrix = new Matrix4f(boneMatrices.values().iterator().next());
        }
        else
        {
            WorldTransformClipboard.matrix = null;
        }
    }

    public static boolean has()
    {
        return WorldTransformClipboard.matrix != null || !WorldTransformClipboard.bones.isEmpty();
    }

    public static boolean hasBones()
    {
        return !WorldTransformClipboard.bones.isEmpty();
    }

    /** A copy of the stored matrix, or {@code null} when nothing has been captured. */
    public static Matrix4f get()
    {
        return WorldTransformClipboard.matrix == null ? null : new Matrix4f(WorldTransformClipboard.matrix);
    }

    public static java.util.Map<String, Matrix4f> getBones()
    {
        java.util.Map<String, Matrix4f> copy = new java.util.LinkedHashMap<>();

        for (java.util.Map.Entry<String, Matrix4f> entry : WorldTransformClipboard.bones.entrySet())
        {
            copy.put(entry.getKey(), new Matrix4f(entry.getValue()));
        }

        return copy;
    }

    public static Matrix4f getBone(String bone)
    {
        Matrix4f m = WorldTransformClipboard.bones.get(bone);

        return m == null ? null : new Matrix4f(m);
    }

    public static boolean hasBone(String bone)
    {
        return WorldTransformClipboard.bones.containsKey(bone);
    }

    public static void clear()
    {
        WorldTransformClipboard.matrix = null;
        WorldTransformClipboard.bones.clear();
    }
}
