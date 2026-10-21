package mchorse.bbs_mod.cubic.render;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.RigBone;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.rig.BonePassport;
import mchorse.bbs_mod.cubic.rig.RigWriter;
import mchorse.bbs_mod.utils.joml.Matrices;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Directs solver output (physics) onto bones as evaluated orientations: each bone's local rotation
 * is rebuilt from its solved segment, blended against the evaluated FK base by the stage weight,
 * and written to {@code orient} — the channels stay read-only FK truth (the constraint-stack
 * contract; see {@link mchorse.bbs_mod.cubic.data.model.ModelGroup#orient}).
 */
public final class ModelRotationBlender
{
    private static final float EPS = 1.0e-6f;

    private ModelRotationBlender()
    {
    }

    /**
     * Rebuilds each bone's local rotation from its solved segment {@code positions[i] →
     * positions[i + 1]} (keeping the FK twist about the bone's axis) and writes it blended by
     * {@code weight}. The parent frame advances by the applied rotation, the same frame the
     * renderer establishes for children. With a point past the last bone ({@code positions} one
     * longer than {@code ids}) the last bone is aimed too, along its rest axis past the tip.
     */
    public static void applyWeightedRotations(IModel model, Quaternionf rootParentRotation, List<String> ids, Vector3f[] positions, float weight)
    {
        float factor = clamp01(weight);

        if (factor <= EPS)
        {
            return;
        }

        BonePassport passport = BonePassport.of(model);

        if (passport == null || rootParentRotation == null || ids == null || positions == null || ids.isEmpty() || positions.length < 2)
        {
            return;
        }

        /* A bone with nothing to aim (on its child's pivot) keeps its rotation, and on a cubic model
         * the next bone hangs in the frame that rotation makes. A BOBJ bone still leaves the frame as
         * it was — the known gap, aiming the next bone as if this one did not turn; it is fixed on
         * its own, not here. */
        boolean turnsWhenSkipped = model instanceof Model;
        Quaternionf parentWorld = new Quaternionf(rootParentRotation);
        int boneCount = ids.size();
        int rotCount = boneCount - 1 + (positions.length >= boneCount + 1 ? 1 : 0);

        for (int i = 0; i < rotCount; i++)
        {
            RigBone bone = model.getBone(ids.get(i));
            Vector3f restAxis = bone == null ? null : passport.restAxisPastTip(ids.get(i), i + 1 < boneCount ? ids.get(i + 1) : null, i > 0 ? ids.get(i - 1) : null);

            if (restAxis == null)
            {
                return;
            }

            Quaternionf base = bone.evaluatedRotation();
            Quaternionf localRot = BonePassport.aim(restAxis.mul(passport.scale()), positions[i], positions[i + 1], parentWorld);

            if (localRot == null)
            {
                if (turnsWhenSkipped)
                {
                    parentWorld.mul(base);
                }

                continue;
            }

            localRot.mul(Matrices.twistAbout(base, restAxis));
            parentWorld.mul(RigWriter.blend(bone, localRot, factor));
        }
    }

    private static float clamp01(float value)
    {
        if (value < 0F)
        {
            return 0F;
        }

        return Math.min(value, 1F);
    }
}
