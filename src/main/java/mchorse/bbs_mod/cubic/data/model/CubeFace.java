package mchorse.bbs_mod.cubic.data.model;

import org.joml.Vector3f;

import java.util.Locale;

/**
 * One of the six box faces of a cube, identified by the local normal baked into its quads. Lets an
 * unwrap or a weld name a side ("top", "bottom", ...).
 */
public enum CubeFace
{
    FRONT(0, 0, -1),
    BACK(0, 0, 1),
    RIGHT(1, 0, 0),
    LEFT(-1, 0, 0),
    TOP(0, 1, 0),
    BOTTOM(0, -1, 0);

    public final Vector3f normal;

    CubeFace(float x, float y, float z)
    {
        this.normal = new Vector3f(x, y, z);
    }

    public static CubeFace fromName(String name)
    {
        if (name == null)
        {
            return null;
        }

        try
        {
            /* The root locale: a Turkish one upper-cases "right" with a dotted I, which names no face. */
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }
    }
}
