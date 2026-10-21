package mchorse.bbs_mod.cubic.rig;

import mchorse.bbs_mod.cubic.RigBone;
import org.joml.Quaternionf;

/**
 * Where a rig step's answer meets the pose. A solver works out a bone's local rotation; this puts
 * it on the bone, mixed by the step's weight with what the bone had so far — the one copy of that
 * mix, so a fix to it reaches every step.
 */
public final class RigWriter
{
    private static final float EPS = 1.0e-6F;

    private RigWriter()
    {}

    /**
     * Writes the solved local rotation to the bone's orient: at full weight as it is, below it a
     * slerp towards it from {@link RigBone#evaluatedRotation}. Returns what was written — the
     * rotation the renderer applies, which the caller's parent frame advances by; not to be mutated.
     */
    public static Quaternionf blend(RigBone bone, Quaternionf solved, float weight)
    {
        Quaternionf applied = weight >= 1F - EPS ? solved : bone.evaluatedRotation().slerp(solved, weight);

        bone.setOrient(applied);

        return applied;
    }
}
