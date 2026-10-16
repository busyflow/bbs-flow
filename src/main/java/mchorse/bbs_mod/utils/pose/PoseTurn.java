package mchorse.bbs_mod.utils.pose;

import mchorse.bbs_mod.cubic.RigBone;
import mchorse.bbs_mod.utils.joml.Matrices;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Laying a turn of a bone's evaluated rotation into a pose value, so the bone lands on a rotation
 * measured on the live model — the inverse of how the render composes a pose onto a bone. The IK
 * bake writes the solve's turn into keyframes with it; the pose editor's fix in place writes the
 * animation's turn into the pose.
 */
public final class PoseTurn
{
    private PoseTurn()
    {}

    /**
     * A bone's state before the turn: its euler channels (radians, whichever unit the skeleton
     * keeps), its evaluated rotation, and whether the pose was the first layer to compose that
     * rotation — see {@link #apply} for why that decides the arithmetic.
     */
    public record Before(Vector3f channels, Quaternionf evaluated, boolean additive)
    {
        public static Before of(RigBone bone)
        {
            Quaternionf orient = bone.getOrient();
            boolean additive = orient == null || sameRotation(orient, bone.orientFromEuler(bone.getBoneTransform().rotate));

            return new Before(bone.getChannelRotation(new Vector3f()), bone.evaluatedRotation(), additive);
        }
    }

    /**
     * Turn the pose value so the bone lands on the target rotation. Which arithmetic does it
     * depends on how the render composes the pose onto the bone:
     *
     * <ul>
     * <li>a euler pose that is the FIRST layer to compose the bone's orientation is added to the
     * channels angle by angle (rest + actions + pose is one sum, and the orientation is built from
     * the sum), so the value gets the euler difference between the target rotation and the
     * channels, read on the channels' own branch;</li>
     * <li>a euler pose over a layer that already composed the orientation (an action's post pass)
     * multiplies in as a quaternion, so the delta turns the pose's whole rotation — static and
     * tracks together — and the value gets what changed in it;</li>
     * <li>a quaternion pose always multiplies, and the value is its last factor.</li>
     * </ul>
     *
     * <p>{@code delta} is the turn in the bone's own frame, {@code conj(evaluated) · target}, so
     * in every case the composed rotation ends up exactly at {@code target}.</p>
     *
     * @param total the bone's transform of the whole pose stack the render composes, null when it has none
     */
    public static void apply(PoseTransform value, PoseTransform total, Before before, Quaternionf delta, Quaternionf target)
    {
        if (total != null && total.rotationMode == Transform.RotationMode.QUATERNION)
        {
            if (value.rotationMode == Transform.RotationMode.QUATERNION)
            {
                value.quat.mul(delta);
            }
            else
            {
                Quaternionf turned = Matrices.toLocalRotationZYXRadians(value.rotate).mul(delta);

                Matrices.toCompatibleEulerZYXRadians(turned, value.rotate, value.rotate);
            }

            return;
        }

        if (before.additive())
        {
            Vector3f euler = Matrices.toCompatibleEulerZYXRadians(target, before.channels(), new Vector3f());

            value.rotate.add(euler.sub(before.channels()));

            return;
        }

        Vector3f base = total == null ? new Vector3f() : total.rotate;
        Quaternionf turned = Matrices.toLocalRotationZYXRadians(base).mul(delta);
        Vector3f after = Matrices.toCompatibleEulerZYXRadians(turned, base, new Vector3f());

        value.rotate.add(after.sub(base));
    }

    private static boolean sameRotation(Quaternionf a, Quaternionf b)
    {
        return Math.abs(a.dot(b)) >= 1F - 1.0E-6F;
    }
}
