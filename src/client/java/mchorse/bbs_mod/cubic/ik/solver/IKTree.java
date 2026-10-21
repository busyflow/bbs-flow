package mchorse.bbs_mod.cubic.ik.solver;

import mchorse.bbs_mod.utils.joml.Matrices;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * An IK tree in world (model) space: the union of one or more OVERLAPPING
 * chains solved together, Blender-style — joints (bones), each hanging under its
 * nearest captured ancestor, and one positional effector per merged chain. A
 * single chain is simply the linear, one-effector case.
 *
 * <p>The forward model is the RIGID re-rotation of the captured start frames,
 * generalized from the chain to a forest: a joint's parent frame is its
 * captured parent frame carried by how far its nearest captured ANCESTOR's
 * world rotation moved (bones between them, if any, hold their rotation during
 * the solve, so they ride inside the captured relation):
 *
 * <pre>
 * ΔW(a)        = worldRot[a] · startWorldRot[a]⁻¹
 * parentRot[i] = ΔW(anc(i)) · startParentRot[i]
 * worldRot[i]  = parentRot[i] · local[i]
 * p[i]         = p[anc(i)] + ΔW(anc(i)) · (startPos[i] − startPos[anc(i)])
 * </pre>
 *
 * A joint with no captured ancestor is a ROOT of the forest: its pivot and
 * parent frame are frozen for the solve. Effectors ride their chain's last
 * directed joint the same rigid way. Exact for any ancestor/link scale (scale
 * lives inside the captured segments).
 */
public final class IKTree
{
    /** Joints in parents-first order (a joint's ancestor sits earlier in the array). */
    public final IKJoint[] joints;

    /** Index of each joint's nearest captured ancestor; -1 = a root of the forest. */
    public final int[] parentIndex;

    /** Captured parent frame of each joint (its MODEL parent's world rotation at capture). */
    public final Quaternionf[] startParentRotation;

    /** One per merged chain: where its effector is and what it wants. */
    public final Effector[] effectors;

    /**
     * A chain's positional goal inside the tree: the effector point rides
     * {@code joint} (the chain's last directed bone); {@code weight} is the
     * chain's solve priority when goals conflict — every Jacobian row of this
     * effector is scaled by it, so a lighter goal yields to a heavier one.
     */
    public static final class Effector
    {
        /** Index of the chain's last directed joint in {@link #joints}. */
        public final int joint;

        /** World effector position at capture. */
        public final Vector3f startPosition = new Vector3f();

        /** World effector position at the current rotations, refreshed by {@link #forward()}. */
        public final Vector3f position = new Vector3f();

        /** The goal this effector reaches for, world space. */
        public final Vector3f goal = new Vector3f();

        /** Solve priority, 0..1. */
        public float weight = 1F;

        public Effector(int joint)
        {
            this.joint = joint;
        }
    }

    /** [effector][joint]: whether the joint's turn moves that effector (tree ancestry). */
    private final boolean[][] moves;

    public IKTree(int jointCount, int effectorCount)
    {
        this.joints = new IKJoint[jointCount];
        this.parentIndex = new int[jointCount];
        this.startParentRotation = new Quaternionf[jointCount];
        this.effectors = new Effector[effectorCount];
        this.moves = new boolean[effectorCount][jointCount];

        for (int i = 0; i < jointCount; i++)
        {
            this.joints[i] = new IKJoint();
            this.parentIndex[i] = -1;
            this.startParentRotation[i] = new Quaternionf();
        }
    }

    /** Sets effector {@code e} to ride joint {@code joint}; call after parentIndex is filled. */
    public Effector effector(int e, int joint)
    {
        Effector effector = new Effector(joint);

        this.effectors[e] = effector;

        for (int j = joint; j >= 0; j = this.parentIndex[j])
        {
            this.moves[e][j] = true;
        }

        return effector;
    }

    /** Whether turning joint {@code joint} moves effector {@code e}. */
    public boolean moves(int e, int joint)
    {
        return this.moves[e][joint];
    }

    /** Total captured arc length from the effector's forest root down to effector {@code e} — its reach. */
    public float reach(int e)
    {
        Effector effector = this.effectors[e];
        float total = effector.startPosition.distance(this.joints[effector.joint].startPosition);
        int j = effector.joint;

        while (this.parentIndex[j] >= 0)
        {
            int parent = this.parentIndex[j];

            total += this.joints[j].startPosition.distance(this.joints[parent].startPosition);
            j = parent;
        }

        return total;
    }

    /** The forest-root joint the effector's branch hangs from. */
    public int rootOf(int e)
    {
        int j = this.effectors[e].joint;

        while (this.parentIndex[j] >= 0)
        {
            j = this.parentIndex[j];
        }

        return j;
    }

    /**
     * Re-poses the whole tree from the current {@link IKJoint#local}: refreshes
     * every joint's {@code position}/{@code parentRotation}/{@code worldRotation}
     * and every effector's {@code position}. See the class doc for the model.
     */
    public void forward()
    {
        for (int i = 0; i < this.joints.length; i++)
        {
            IKJoint joint = this.joints[i];
            int parent = this.parentIndex[i];

            if (parent < 0)
            {
                joint.position.set(joint.startPosition);
                joint.parentRotation.set(this.startParentRotation[i]);
            }
            else
            {
                IKJoint ancestor = this.joints[parent];
                Quaternionf delta = delta(ancestor);

                joint.parentRotation.set(delta).mul(this.startParentRotation[i]);

                Vector3f segment = new Vector3f(joint.startPosition).sub(ancestor.startPosition);

                delta.transform(segment);
                joint.position.set(ancestor.position).add(segment);
            }

            joint.worldRotation.set(joint.parentRotation).mul(joint.local);
        }

        for (Effector effector : this.effectors)
        {
            IKJoint last = this.joints[effector.joint];
            Vector3f segment = new Vector3f(effector.startPosition).sub(last.startPosition);

            delta(last).transform(segment);
            effector.position.set(last.position).add(segment);
        }
    }

    /** How far the joint's world rotation moved from capture: {@code worldRot · startWorldRot⁻¹}. */
    private static Quaternionf delta(IKJoint joint)
    {
        return new Quaternionf(joint.worldRotation).mul(new Quaternionf(joint.startWorldRotation).conjugate());
    }

    /**
     * The world axis the given degree of freedom currently turns about — the
     * Jacobian column direction. A free joint turns about the three world axes
     * (any orthonormal triad spans its rotations equally). A constrained joint
     * turns through its ZYX channels: channel Z about the parent frame's Z,
     * channel Y about the Z-rotated Y, channel X about the fully rotated X (Rx
     * commutes with its own axis).
     */
    public Vector3f channelAxis(int jointIndex, int axis, Vector3f dest)
    {
        IKJoint joint = this.joints[jointIndex];

        if (joint.isFree())
        {
            return dest.set(axis == 0 ? 1F : 0F, axis == 1 ? 1F : 0F, axis == 2 ? 1F : 0F);
        }

        if (axis == 0)
        {
            return joint.worldRotation.transform(dest.set(1F, 0F, 0F));
        }

        if (axis == 1)
        {
            return new Quaternionf(joint.parentRotation).rotateZ(joint.angles.z).transform(dest.set(0F, 1F, 0F));
        }

        return joint.parentRotation.transform(dest.set(0F, 0F, 1F));
    }

    /**
     * Steps joint {@code index} by one increment per degree of freedom (radians,
     * matching {@link #channelAxis}): a free joint turns by the world rotation
     * vector {@code (dx, dy, dz)} about its pivot, a constrained joint adds them
     * to its channels and clamps them into its limits. Does NOT re-run
     * {@link #forward()} — the caller steps every joint first.
     */
    public void step(int index, float dx, float dy, float dz)
    {
        IKJoint joint = this.joints[index];

        if (joint.isFree())
        {
            float angle = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (angle < 1.0e-12f)
            {
                return;
            }

            Quaternionf turn = new Quaternionf().rotationAxis(angle, dx / angle, dy / angle, dz / angle);

            /* local' = P⁻¹ · turn · P · local: the turn applied in world about the pivot. */
            joint.local.set(new Quaternionf(joint.parentRotation).conjugate().mul(turn).mul(joint.parentRotation).mul(joint.local)).normalize();

            return;
        }

        joint.angles.add(dx, dy, dz);
        joint.clampLimits();
    }

    /**
     * Rigidly rotates joint {@code index} — and with it everything below — by the
     * world rotation {@code delta} about its own pivot, and re-runs
     * {@link #forward()}. A constrained joint folds the turn into its channels
     * (compatible-euler, anchored to the current angles so winding survives) and
     * clamps them back into its limits.
     */
    public void rotateJointWorld(int index, Quaternionf delta)
    {
        IKJoint joint = this.joints[index];
        Quaternionf world = new Quaternionf(delta).mul(joint.worldRotation);
        Quaternionf local = new Quaternionf(joint.parentRotation).conjugate().mul(world);

        if (joint.isFree())
        {
            joint.local.set(local).normalize();
        }
        else
        {
            Matrices.toCompatibleEulerZYXRadians(local, joint.angles, joint.angles);
            joint.clampLimits();
        }

        this.forward();
    }
}
