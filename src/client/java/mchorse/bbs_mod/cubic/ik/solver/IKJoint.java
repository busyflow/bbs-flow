package mchorse.bbs_mod.cubic.ik.solver;

import mchorse.bbs_mod.utils.joml.Matrices;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One directed bone of an IK chain, as the solver sees it: its local rotation —
 * the solver's variable — plus per-axis degrees of freedom, and its captured
 * and working world frames.
 *
 * <p>The variable is {@link #local}, the bone's full local rotation, the same
 * quaternion the result is written to. A FREE joint (no locks, limits or
 * stiffness) turns about any axis by incremental world rotations, so there is no
 * gimbal anywhere in its solve. A joint with any DoF setting is parametrized by
 * its ZYX rotation channels instead ({@link #angles}, radians) — its locks and
 * limits are authored on exactly those channels, so they clamp the very numbers
 * the animator sees on the rotation pads — and {@link #local} follows them.
 *
 * <p>Axis indices are {@code 0 = X, 1 = Y, 2 = Z} throughout.
 */
public final class IKJoint
{
    /* --- captured at build (the FK pose the solve starts from) --- */

    /** World pivot position at capture. */
    public final Vector3f startPosition = new Vector3f();

    /** World rotation at capture (pivot frame AFTER the bone's own rotation). */
    public final Quaternionf startWorldRotation = new Quaternionf();

    /** FK channel angles at capture, ZYX radians — the value locked axes hold. */
    public final Vector3f startAngles = new Vector3f();

    /* --- the solver variable --- */

    /** Current local rotation. Starts as the FK local rotation. */
    public final Quaternionf local = new Quaternionf();

    /** Current channel angles, ZYX radians — the variable of a constrained joint, unused by a free one. */
    public final Vector3f angles = new Vector3f();

    /* --- per-axis degrees of freedom (Blender's bone IK panel) --- */

    /** A locked axis is absent from the Jacobian and FROZEN at its FK value. */
    public final boolean[] locked = new boolean[3];

    /** Whether {@link #limitMin}/{@link #limitMax} apply on this axis. */
    public final boolean[] limited = new boolean[3];

    /** Lower rotation limit per axis, radians. */
    public final float[] limitMin = new float[3];

    /** Upper rotation limit per axis, radians. */
    public final float[] limitMax = new float[3];

    /** 0 = moves freely, approaching 1 = increasingly reluctant to move. */
    public final float[] stiffness = new float[3];

    /* --- working state, refreshed by IKTree.forward() --- */

    /** World pivot position at the current rotations. */
    public final Vector3f position = new Vector3f();

    /** World rotation of the parent frame (pivot frame BEFORE this bone's rotation). */
    public final Quaternionf parentRotation = new Quaternionf();

    /** World rotation at the current rotations. */
    public final Quaternionf worldRotation = new Quaternionf();

    /**
     * Whether the joint turns freely about every axis — no locks, limits or
     * stiffness. Free joints are solved in rotations, constrained ones in their
     * channels.
     */
    public boolean isFree()
    {
        for (int axis = 0; axis < 3; axis++)
        {
            if (this.locked[axis] || this.limited[axis] || this.stiffness[axis] != 0F)
            {
                return false;
            }
        }

        return true;
    }

    /** How willing this axis is to move: {@code 1 - stiffness}, clamped to [0, 1]. */
    public float weight(int axis)
    {
        float w = 1F - this.stiffness[axis];

        return w < 0F ? 0F : Math.min(w, 1F);
    }

    /** Clamps {@link #angles} into the enabled per-axis limits and refreshes {@link #local}; a free joint has neither. */
    public void clampLimits()
    {
        if (this.isFree())
        {
            return;
        }

        for (int axis = 0; axis < 3; axis++)
        {
            if (!this.limited[axis])
            {
                continue;
            }

            float value = get(this.angles, axis);
            float clamped = Math.max(this.limitMin[axis], Math.min(this.limitMax[axis], value));

            if (clamped != value)
            {
                set(this.angles, axis, clamped);
            }
        }

        this.syncLocal();
    }

    /** Recomposes {@link #local} from the channel angles (constrained joints). */
    public void syncLocal()
    {
        this.local.set(Matrices.toLocalRotationZYXRadians(this.angles));
    }

    static float get(Vector3f v, int axis)
    {
        return axis == 0 ? v.x : axis == 1 ? v.y : v.z;
    }

    static void set(Vector3f v, int axis, float value)
    {
        if (axis == 0) v.x = value;
        else if (axis == 1) v.y = value;
        else v.z = value;
    }
}
