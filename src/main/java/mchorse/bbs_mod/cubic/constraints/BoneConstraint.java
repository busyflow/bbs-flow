package mchorse.bbs_mod.cubic.constraints;

import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.interps.AutoBezier;
import mchorse.bbs_mod.utils.interps.IInterp;

/**
 * One bone's limits — the value of the bone's "constraints" property, both as the form's static
 * setting and as a film track's keyframe (one type for both, so no separate animatable mirror is
 * ever needed). Per axis: the lock keeps the axis where the keys put it, the limit holds the angle
 * within [min, max] degrees (the numbers the animator sees on the rotation pads), and the stiffness
 * 0..1 makes an IK solve reluctant to turn it, shifting the bend to freer joints.
 *
 * <p>The one set of limits every step reads: the IK solve all of it, the physics the angle limits,
 * and the last step of the frame clamps the result to the locks and limits. The numbers and the
 * stiffness interpolate; the switches step. An all-default instance is neutral: the bone is free,
 * as if the value didn't exist.</p>
 *
 * <p>The move and scale locks are the hand's alone: no step of the frame reads them, the gizmo and
 * its hotkeys leave a locked axis where it is — the rotation locks restrain the hand as well.</p>
 */
public class BoneConstraint implements IMapSerializable
{
    public static final float DEFAULT_MIN = -180F;
    public static final float DEFAULT_MAX = 180F;

    public static final BoneConstraint DEFAULT = new BoneConstraint();

    /* Per-axis switches, and the only switches there are: the limits ARE their axes, so there is
     * nothing left for a master toggle to say that turning every axis off does not already say. */
    public boolean lockX;
    public boolean lockY;
    public boolean lockZ;
    public boolean limitX;
    public boolean limitY;
    public boolean limitZ;
    public float minX = DEFAULT_MIN;
    public float minY = DEFAULT_MIN;
    public float minZ = DEFAULT_MIN;
    public float maxX = DEFAULT_MAX;
    public float maxY = DEFAULT_MAX;
    public float maxZ = DEFAULT_MAX;
    public float stiffnessX;
    public float stiffnessY;
    public float stiffnessZ;
    public boolean lockTranslateX;
    public boolean lockTranslateY;
    public boolean lockTranslateZ;
    public boolean lockScaleX;
    public boolean lockScaleY;
    public boolean lockScaleZ;

    /** Whether the bone is held at all: an axis locked or limited. */
    public boolean isActive()
    {
        return this.lockX || this.lockY || this.lockZ || this.isLimited();
    }

    /** Whether an axis' angle is held within its limits. */
    public boolean isLimited()
    {
        return this.limitX || this.limitY || this.limitZ;
    }

    /** Whether an IK solve may turn the bone freely: nothing locked, limited or stiff. */
    public boolean isFree()
    {
        return !this.isActive() && this.stiffnessX <= 0F && this.stiffnessY <= 0F && this.stiffnessZ <= 0F;
    }

    /** Whether the hand may not move or scale the bone along some axis. */
    public boolean locksTranslateOrScale()
    {
        return this.lockTranslateX || this.lockTranslateY || this.lockTranslateZ
            || this.lockScaleX || this.lockScaleY || this.lockScaleZ;
    }

    public void identity()
    {
        this.lockX = false;
        this.lockY = false;
        this.lockZ = false;
        this.limitX = false;
        this.limitY = false;
        this.limitZ = false;
        this.minX = DEFAULT_MIN;
        this.minY = DEFAULT_MIN;
        this.minZ = DEFAULT_MIN;
        this.maxX = DEFAULT_MAX;
        this.maxY = DEFAULT_MAX;
        this.maxZ = DEFAULT_MAX;
        this.stiffnessX = 0F;
        this.stiffnessY = 0F;
        this.stiffnessZ = 0F;
        this.lockTranslateX = false;
        this.lockTranslateY = false;
        this.lockTranslateZ = false;
        this.lockScaleX = false;
        this.lockScaleY = false;
        this.lockScaleZ = false;
    }

    /**
     * Reads the IK's own joint freedom, saved before it became part of the limits: a bone's
     * {@code joint} in forms of the 2026-09-20 release, the {@code bones} of an IK preset. Its
     * locks and stiffness come over; its limits too, on an axis this constraint doesn't limit —
     * where both limited an axis, this constraint's numbers stay.
     */
    public void readJoint(MapType joint)
    {
        boolean[] lock = bools(joint, "lock");
        boolean[] limited = bools(joint, "limited");
        float[] min = floats(joint, "min", DEFAULT_MIN);
        float[] max = floats(joint, "max", DEFAULT_MAX);
        float[] stiffness = floats(joint, "stiffness", 0F);

        this.lockX = lock[0];
        this.lockY = lock[1];
        this.lockZ = lock[2];
        this.stiffnessX = MathUtils.clamp(stiffness[0], 0F, 1F);
        this.stiffnessY = MathUtils.clamp(stiffness[1], 0F, 1F);
        this.stiffnessZ = MathUtils.clamp(stiffness[2], 0F, 1F);

        if (limited[0] && !this.limitX)
        {
            this.limitX = true;
            this.minX = min[0];
            this.maxX = max[0];
        }

        if (limited[1] && !this.limitY)
        {
            this.limitY = true;
            this.minY = min[1];
            this.maxY = max[1];
        }

        if (limited[2] && !this.limitZ)
        {
            this.limitZ = true;
            this.minZ = min[2];
            this.maxZ = max[2];
        }
    }

    private static boolean[] bools(MapType data, String key)
    {
        ListType list = data.has(key, BaseType.TYPE_LIST) ? data.getList(key) : new ListType();

        return new boolean[] {list.getBool(0), list.getBool(1), list.getBool(2)};
    }

    private static float[] floats(MapType data, String key, float def)
    {
        ListType list = data.has(key, BaseType.TYPE_LIST) ? data.getList(key) : null;
        float[] values = new float[3];

        for (int i = 0; i < 3; i++)
        {
            BaseType element = list == null ? null : list.get(i);

            values[i] = BaseType.isNumeric(element) ? element.asNumeric().floatValue() : def;
        }

        return values;
    }

    public void lerp(BoneConstraint preA, BoneConstraint a, BoneConstraint b, BoneConstraint postB, IInterp interp, float x)
    {
        this.minX = (float) interp.interpolate(IInterp.context.set(preA.minX, a.minX, b.minX, postB.minX, x));
        this.minY = (float) interp.interpolate(IInterp.context.set(preA.minY, a.minY, b.minY, postB.minY, x));
        this.minZ = (float) interp.interpolate(IInterp.context.set(preA.minZ, a.minZ, b.minZ, postB.minZ, x));
        this.maxX = (float) interp.interpolate(IInterp.context.set(preA.maxX, a.maxX, b.maxX, postB.maxX, x));
        this.maxY = (float) interp.interpolate(IInterp.context.set(preA.maxY, a.maxY, b.maxY, postB.maxY, x));
        this.maxZ = (float) interp.interpolate(IInterp.context.set(preA.maxZ, a.maxZ, b.maxZ, postB.maxZ, x));
        this.stiffnessX = (float) interp.interpolate(IInterp.context.set(preA.stiffnessX, a.stiffnessX, b.stiffnessX, postB.stiffnessX, x));
        this.stiffnessY = (float) interp.interpolate(IInterp.context.set(preA.stiffnessY, a.stiffnessY, b.stiffnessY, postB.stiffnessY, x));
        this.stiffnessZ = (float) interp.interpolate(IInterp.context.set(preA.stiffnessZ, a.stiffnessZ, b.stiffnessZ, postB.stiffnessZ, x));
        this.stepSwitches(a);
    }

    public void autoLerp(BoneConstraint preA, BoneConstraint a, BoneConstraint b, BoneConstraint postB, float pt, float at, float bt, float qt, boolean clamped, float x)
    {
        this.minX = (float) AutoBezier.get(preA.minX, a.minX, b.minX, postB.minX, pt, at, bt, qt, clamped, x);
        this.minY = (float) AutoBezier.get(preA.minY, a.minY, b.minY, postB.minY, pt, at, bt, qt, clamped, x);
        this.minZ = (float) AutoBezier.get(preA.minZ, a.minZ, b.minZ, postB.minZ, pt, at, bt, qt, clamped, x);
        this.maxX = (float) AutoBezier.get(preA.maxX, a.maxX, b.maxX, postB.maxX, pt, at, bt, qt, clamped, x);
        this.maxY = (float) AutoBezier.get(preA.maxY, a.maxY, b.maxY, postB.maxY, pt, at, bt, qt, clamped, x);
        this.maxZ = (float) AutoBezier.get(preA.maxZ, a.maxZ, b.maxZ, postB.maxZ, pt, at, bt, qt, clamped, x);
        this.stiffnessX = (float) AutoBezier.get(preA.stiffnessX, a.stiffnessX, b.stiffnessX, postB.stiffnessX, pt, at, bt, qt, clamped, x);
        this.stiffnessY = (float) AutoBezier.get(preA.stiffnessY, a.stiffnessY, b.stiffnessY, postB.stiffnessY, pt, at, bt, qt, clamped, x);
        this.stiffnessZ = (float) AutoBezier.get(preA.stiffnessZ, a.stiffnessZ, b.stiffnessZ, postB.stiffnessZ, pt, at, bt, qt, clamped, x);
        this.stepSwitches(a);
    }

    /** The switches don't interpolate: they hold the earlier keyframe's state until the next one. */
    private void stepSwitches(BoneConstraint a)
    {
        this.lockX = a.lockX;
        this.lockY = a.lockY;
        this.lockZ = a.lockZ;
        this.limitX = a.limitX;
        this.limitY = a.limitY;
        this.limitZ = a.limitZ;
        this.lockTranslateX = a.lockTranslateX;
        this.lockTranslateY = a.lockTranslateY;
        this.lockTranslateZ = a.lockTranslateZ;
        this.lockScaleX = a.lockScaleX;
        this.lockScaleY = a.lockScaleY;
        this.lockScaleZ = a.lockScaleZ;
    }

    public BoneConstraint copy()
    {
        BoneConstraint constraint = new BoneConstraint();

        constraint.copy(this);

        return constraint;
    }

    public void copy(BoneConstraint other)
    {
        this.lockX = other.lockX;
        this.lockY = other.lockY;
        this.lockZ = other.lockZ;
        this.limitX = other.limitX;
        this.limitY = other.limitY;
        this.limitZ = other.limitZ;
        this.minX = other.minX;
        this.minY = other.minY;
        this.minZ = other.minZ;
        this.maxX = other.maxX;
        this.maxY = other.maxY;
        this.maxZ = other.maxZ;
        this.stiffnessX = other.stiffnessX;
        this.stiffnessY = other.stiffnessY;
        this.stiffnessZ = other.stiffnessZ;
        this.lockTranslateX = other.lockTranslateX;
        this.lockTranslateY = other.lockTranslateY;
        this.lockTranslateZ = other.lockTranslateZ;
        this.lockScaleX = other.lockScaleX;
        this.lockScaleY = other.lockScaleY;
        this.lockScaleZ = other.lockScaleZ;
    }

    public boolean isDefault()
    {
        return this.equals(DEFAULT);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }

        if (obj instanceof BoneConstraint constraint)
        {
            return this.lockX == constraint.lockX
                && this.lockY == constraint.lockY
                && this.lockZ == constraint.lockZ
                && this.limitX == constraint.limitX
                && this.limitY == constraint.limitY
                && this.limitZ == constraint.limitZ
                && this.minX == constraint.minX
                && this.minY == constraint.minY
                && this.minZ == constraint.minZ
                && this.maxX == constraint.maxX
                && this.maxY == constraint.maxY
                && this.maxZ == constraint.maxZ
                && this.stiffnessX == constraint.stiffnessX
                && this.stiffnessY == constraint.stiffnessY
                && this.stiffnessZ == constraint.stiffnessZ
                && this.lockTranslateX == constraint.lockTranslateX
                && this.lockTranslateY == constraint.lockTranslateY
                && this.lockTranslateZ == constraint.lockTranslateZ
                && this.lockScaleX == constraint.lockScaleX
                && this.lockScaleY == constraint.lockScaleY
                && this.lockScaleZ == constraint.lockScaleZ;
        }

        return false;
    }

    @Override
    public void toData(MapType data)
    {
        /* The locks and the stiffness only when there are any: limits saved before them read the same. */
        if (this.lockX || this.lockY || this.lockZ)
        {
            data.putBool("lock_x", this.lockX);
            data.putBool("lock_y", this.lockY);
            data.putBool("lock_z", this.lockZ);
        }

        data.putBool("limit_x", this.limitX);
        data.putBool("limit_y", this.limitY);
        data.putBool("limit_z", this.limitZ);
        data.putDouble("min_x", this.minX);
        data.putDouble("min_y", this.minY);
        data.putDouble("min_z", this.minZ);
        data.putDouble("max_x", this.maxX);
        data.putDouble("max_y", this.maxY);
        data.putDouble("max_z", this.maxZ);

        if (this.stiffnessX > 0F || this.stiffnessY > 0F || this.stiffnessZ > 0F)
        {
            data.putDouble("stiffness_x", this.stiffnessX);
            data.putDouble("stiffness_y", this.stiffnessY);
            data.putDouble("stiffness_z", this.stiffnessZ);
        }

        if (this.lockTranslateX || this.lockTranslateY || this.lockTranslateZ)
        {
            data.putBool("lock_translate_x", this.lockTranslateX);
            data.putBool("lock_translate_y", this.lockTranslateY);
            data.putBool("lock_translate_z", this.lockTranslateZ);
        }

        if (this.lockScaleX || this.lockScaleY || this.lockScaleZ)
        {
            data.putBool("lock_scale_x", this.lockScaleX);
            data.putBool("lock_scale_y", this.lockScaleY);
            data.putBool("lock_scale_z", this.lockScaleZ);
        }
    }

    @Override
    public void fromData(MapType data)
    {
        this.lockX = data.getBool("lock_x", DEFAULT.lockX);
        this.lockY = data.getBool("lock_y", DEFAULT.lockY);
        this.lockZ = data.getBool("lock_z", DEFAULT.lockZ);
        this.limitX = data.getBool("limit_x", DEFAULT.limitX);
        this.limitY = data.getBool("limit_y", DEFAULT.limitY);
        this.limitZ = data.getBool("limit_z", DEFAULT.limitZ);
        this.minX = (float) data.getDouble("min_x", DEFAULT.minX);
        this.minY = (float) data.getDouble("min_y", DEFAULT.minY);
        this.minZ = (float) data.getDouble("min_z", DEFAULT.minZ);
        this.maxX = (float) data.getDouble("max_x", DEFAULT.maxX);
        this.maxY = (float) data.getDouble("max_y", DEFAULT.maxY);
        this.maxZ = (float) data.getDouble("max_z", DEFAULT.maxZ);
        this.stiffnessX = MathUtils.clamp((float) data.getDouble("stiffness_x", DEFAULT.stiffnessX), 0F, 1F);
        this.stiffnessY = MathUtils.clamp((float) data.getDouble("stiffness_y", DEFAULT.stiffnessY), 0F, 1F);
        this.stiffnessZ = MathUtils.clamp((float) data.getDouble("stiffness_z", DEFAULT.stiffnessZ), 0F, 1F);
        this.lockTranslateX = data.getBool("lock_translate_x", DEFAULT.lockTranslateX);
        this.lockTranslateY = data.getBool("lock_translate_y", DEFAULT.lockTranslateY);
        this.lockTranslateZ = data.getBool("lock_translate_z", DEFAULT.lockTranslateZ);
        this.lockScaleX = data.getBool("lock_scale_x", DEFAULT.lockScaleX);
        this.lockScaleY = data.getBool("lock_scale_y", DEFAULT.lockScaleY);
        this.lockScaleZ = data.getBool("lock_scale_z", DEFAULT.lockScaleZ);
    }
}
