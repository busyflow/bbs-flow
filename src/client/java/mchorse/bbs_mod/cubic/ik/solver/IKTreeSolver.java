package mchorse.bbs_mod.cubic.ik.solver;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Damped-least-squares IK over a whole {@link IKTree} — ONE solver for any chain
 * length and any number of merged chains, modeled on Blender's, implemented
 * from the standard DLS literature (Buss).
 *
 * <p>The solve has three parts, and only the middle one iterates:
 * <ol>
 * <li>SEED. A chain with a pole is turned rigidly about its root so that it
 * points at its goal and its bend plane contains the pole (Blender's pole
 * pre-pass, a full look-at). The plane is the chain's own reference plane,
 * carried by the root bone, so it is defined for any pose of the chain, bent or
 * straight. A chain without a pole is only turned by the part of its goal angle
 * beyond 90° (the antipodal saddle). A chain that is (nearly) straight while the
 * goal asks it to fold first takes a preferred bend, or no iteration could ever
 * fold it — see {@link #preferredBend}.</li>
 * <li>ITERATE. Each step builds the Jacobian of every effector over every free
 * degree of freedom (a free joint turns about the three world axes — no gimbal
 * anywhere; a constrained joint through its unlocked rotation channels), solves
 * the damped normal equations {@code (J·W²·Jᵀ + λ²I)·y = error} by Cholesky and
 * steps by {@code W²·Jᵀ·y}. Steps never increase the error: a clamped step is
 * retried with the limit-cut channels frozen, then backtracked.</li>
 * <li>POLE. The chain's plane is turned exactly onto the pole once more about the
 * root→goal line (root and goal sit on it, so the reach is untouched).</li>
 * </ol>
 *
 * <p>Merged trees keep poles in the seed only — a turn of one chain's root after
 * the solve would move the other chains' effectors. A chain whose root or first
 * interior joint is not fully movable cannot be turned rigidly without trampling
 * its locks: its pole acts through weak rows inside the iterations instead, see
 * {@link #buildBiases}. Everything is radians and world units; the solve is
 * stateless.
 */
public final class IKTreeSolver
{
    private static final float EPS = 1.0e-6f;

    /** Iteration step cap, radians — keeps a damped-but-large step from overshooting. */
    private static final float MAX_STEP = 0.35F;

    /** Steps smaller than this on every channel mean the solve has stalled (limits, locks, degeneracy). */
    private static final float STALL_STEP = 1.0e-6f;

    /**
     * Relative error improvement per iteration below which the solve is done
     * making progress. Checked only after {@link #MIN_ITERATIONS}.
     */
    private static final float MIN_PROGRESS = 1.0e-3f;

    /** Iterations that always run before {@link #MIN_PROGRESS} may stop the solve. */
    private static final int MIN_ITERATIONS = 8;

    /**
     * Chord-to-reach ratio from which a chain counts as straight enough to need
     * a preferred bend (fading in from here, full at a dead straight chain). A
     * dead straight chain has no first-order way to fold — every Jacobian column
     * runs perpendicular to the compression the goal asks for.
     */
    private static final float STRAIGHT_FROM = 0.98F;

    /** Row weight of a DoF-constrained chain's soft pole relative to its chain's effector weight. */
    private static final float POLE_BIAS = 0.25F;

    /** The fraction of a biased pivot's root arc within which its soft pole counts as settled. */
    private static final float POLE_BIAS_SETTLE = 0.05F;

    /** Soft pole row scale of the reach-finishing phase: a whisper next to the position rows. */
    private static final float BIAS_FINISH = 0.05F;

    /**
     * @param maxIterations DLS iteration cap; ~64 covers long chains comfortably.
     * @param tolerance world-units effector error that counts as reached.
     * @param dampingRatio λ as a fraction of the tree's largest chain reach.
     */
    public record Params(int maxIterations, float tolerance, float dampingRatio)
    {
        public static final Params DEFAULT = new Params(64, 1.0e-4f, 0.1F);
    }

    /**
     * A chain's pole inside the tree: the chain's bend plane is turned about
     * {@code rootJoint}'s root→goal line until it contains {@code polePoint},
     * then rolled by {@code poleAngle}.
     *
     * <p>Both normals live in the ROOT JOINT'S local rotation frame, signed so that
     * {@code normal × chord} points to the side the chain bulges towards, and are
     * carried by the root bone, so they never degenerate however the chain is
     * bent. {@code planeNormal} is the plane turned onto the pole — calibrated by
     * the caller from the rest pose so that the rest pose needs no turn at all.
     * {@code bendNormal} is the plane the chain itself bends in (its rest bend;
     * the same as {@code planeNormal} for a chain authored straight) — the axis a
     * straight chain's preferred bend folds about, so the seed stays in the
     * chain's own plane.
     */
    public record Pole(int rootJoint, Vector3f polePoint, float poleAngle, Vector3f planeNormal, Vector3f bendNormal)
    {
    }

    /**
     * @param error final LARGEST effector-to-goal distance, world units.
     * @param poleTwistDeg how far the post-iteration pole pass turned the chain —
     * how much the iterations drifted its plane; stays small.
     */
    public record Result(boolean reached, float error, int iterations, float poleTwistDeg)
    {
    }

    private IKTreeSolver()
    {
    }

    /**
     * Solves the tree towards its effectors' goals, mutating the joints'
     * rotations. {@code poles} may be null/empty; each entry steers one chain's
     * bend. Effector goals and weights are read from the tree.
     */
    public static Result solve(IKTree tree, Pole[] poles, Params params)
    {
        tree.forward();

        boolean single = tree.effectors.length == 1;

        /* 1. Seed. */
        if (single)
        {
            Pole pole = firstPole(poles);

            if (pole != null && fullyMovable(tree.joints[pole.rootJoint()]))
            {
                preferredBend(tree, 0, pole);
                lookAt(tree, 0, pole);
            }
            else
            {
                alignToGoal(tree, 0);

                if (pole == null)
                {
                    preferredBend(tree, 0, null);
                }
            }
        }
        else if (poles != null)
        {
            for (Pole pole : poles)
            {
                if (pole != null && pole.polePoint() != null && fullyMovable(tree.joints[pole.rootJoint()]))
                {
                    turnToPole(tree, pole);
                }
            }
        }

        /* 2. Iterate. A DoF-constrained chain's soft pole rows press at full
         * strength while the pose is shaped, then only whisper while the reach is
         * finished, so they pick the bend without bending the reach. */
        float damping = params.dampingRatio() * largestReach(tree);
        float lambdaSq = damping * damping;
        int columns = countColumns(tree);
        int iterations = 0;
        PoleBias[] biases = buildBiases(tree, poles);

        if (biases != null && columns > 0)
        {
            int budget = params.maxIterations() / 2;

            while (iterations < budget && (worstError(tree) > params.tolerance() || worstBiasExcess(tree, biases) > 0F))
            {
                iterations++;

                if (!step(tree, lambdaSq, 1F, biases))
                {
                    break;
                }
            }
        }

        float previousError = worstError(tree);

        while (iterations < params.maxIterations() && worstError(tree) > params.tolerance() && columns > 0)
        {
            iterations++;

            if (!step(tree, lambdaSq, BIAS_FINISH, biases))
            {
                break;
            }

            float error = worstError(tree);

            if (iterations >= MIN_ITERATIONS && previousError - error < previousError * MIN_PROGRESS)
            {
                break;
            }

            previousError = error;
        }

        /* 3. Exact pole pass (single chain): root and goal sit on the turn's axis,
         * so the reach is untouched. */
        float poleTwistDeg = 0F;

        if (single && poles != null)
        {
            for (Pole pole : poles)
            {
                if (pole != null && pole.polePoint() != null && fullyMovable(tree.joints[pole.rootJoint()]))
                {
                    poleTwistDeg = Math.max(poleTwistDeg, Math.abs(turnToPole(tree, pole)));
                }
            }
        }

        float worst = worstError(tree);

        return new Result(worst <= params.tolerance(), worst, iterations, poleTwistDeg);
    }

    /**
     * Soft-reach goal preprocessor: maps a target onto an effective distance
     * that approaches the chain's full reach asymptotically (C1), so the limb
     * eases into extension instead of snapping straight. {@code softness} is
     * the fraction of the reach the falloff spans; 0 = hard clamp at full
     * reach. Returns the adjusted goal.
     */
    public static Vector3f softGoal(Vector3f rootPosition, float reach, Vector3f target, float softness)
    {
        Vector3f goal = new Vector3f(target);
        float distance = rootPosition.distance(target);

        if (distance < EPS || reach < EPS)
        {
            return goal;
        }

        Vector3f direction = new Vector3f(target).sub(rootPosition).div(distance);

        if (softness > EPS)
        {
            float soft = Math.min(softness, 1F) * reach;
            float hardRange = reach - soft;

            if (distance > hardRange)
            {
                float effective = reach - soft * (float) Math.exp(-(distance - hardRange) / soft);

                goal.set(rootPosition).fma(Math.min(effective, reach), direction);
            }
        }
        else if (distance > reach)
        {
            goal.set(rootPosition).fma(reach, direction);
        }

        return goal;
    }

    /* ------------------------------------------------------------------ */
    /* Seed                                                                 */
    /* ------------------------------------------------------------------ */

    /**
     * The pole pre-pass: turns the chain rigidly about its root so that its
     * chord (root → effector) points at the goal and its reference plane contains
     * the pole, the bulge on the pole's side — then rolls by the pole angle. The
     * iterations only have to fold or unfold the chain in that plane from here, so
     * the side the chain bends to is the seed's, and the seed's is the pole's.
     * Undefined only when the pole sits on the root→goal line (the inherent pole
     * singularity) — the chain is then merely aimed.
     */
    private static void lookAt(IKTree tree, int e, Pole pole)
    {
        IKJoint root = tree.joints[pole.rootJoint()];
        IKTree.Effector effector = tree.effectors[e];
        Vector3f chord = new Vector3f(effector.position).sub(root.position);
        Vector3f goal = new Vector3f(effector.goal).sub(root.position);

        if (chord.lengthSquared() < EPS * EPS || goal.lengthSquared() < EPS * EPS)
        {
            return;
        }

        chord.normalize();
        goal.normalize();

        Vector3f normal = perpendicular(root.worldRotation.transform(new Vector3f(pole.planeNormal())), chord);
        Vector3f side = perpendicular(new Vector3f(pole.polePoint()).sub(root.position), goal);

        if (normal == null || side == null)
        {
            Quaternionf aim = new Quaternionf().rotationTo(chord, goal);

            tree.rotateJointWorld(pole.rootJoint(), aim);

            return;
        }

        Vector3f poleNormal = new Vector3f(goal).cross(side).normalize();
        Matrix3f from = new Matrix3f(chord, normal, new Vector3f(chord).cross(normal));
        Matrix3f to = new Matrix3f(goal, poleNormal, new Vector3f(goal).cross(poleNormal));
        Quaternionf turn = new Quaternionf().setFromNormalized(to.mul(from.transpose()));

        if (pole.poleAngle() != 0F)
        {
            new Quaternionf().rotationAxis(pole.poleAngle(), goal.x, goal.y, goal.z).mul(turn, turn);
        }

        tree.rotateJointWorld(pole.rootJoint(), turn);
    }

    /**
     * The exact pole pass: turns the chain about its root→goal line until its
     * reference plane contains the pole (plus the pole angle). Returns the turn,
     * degrees. Pre-only on merged trees, after the iterations on a single chain.
     */
    private static float turnToPole(IKTree tree, Pole pole)
    {
        int e = effectorUnder(tree, pole.rootJoint());

        if (e < 0)
        {
            return 0F;
        }

        IKJoint root = tree.joints[pole.rootJoint()];
        Vector3f axis = new Vector3f(tree.effectors[e].goal).sub(root.position);

        if (axis.lengthSquared() < EPS * EPS)
        {
            return 0F;
        }

        axis.normalize();

        Vector3f normal = perpendicular(root.worldRotation.transform(new Vector3f(pole.planeNormal())), axis);
        Vector3f side = perpendicular(new Vector3f(pole.polePoint()).sub(root.position), axis);

        if (normal == null || side == null)
        {
            return 0F;
        }

        Vector3f poleNormal = new Vector3f(axis).cross(side).normalize();
        float twist = (float) Math.atan2(new Vector3f(normal).cross(poleNormal).dot(axis), normal.dot(poleNormal)) + pole.poleAngle();

        twist = (float) Math.atan2(Math.sin(twist), Math.cos(twist));

        if (Math.abs(twist) < EPS)
        {
            return 0F;
        }

        tree.rotateJointWorld(pole.rootJoint(), new Quaternionf().rotationAxis(twist, axis.x, axis.y, axis.z));

        return (float) Math.toDegrees(twist);
    }

    /**
     * Gives a (nearly) straight chain its preferred bend — the rigger's pre-bent
     * knee, supplied when the rig has none. The bend is the even arc whose chord
     * is the goal's distance (a two-bone chain lands almost exactly, a long one
     * curls evenly), every free interior joint folding its share about one axis.
     * With a pole the axis is the chain's own bend plane and the tip swings AWAY
     * from the bulge side — once the look-at re-aims the chord at the goal, the
     * joints sit on the bulge side. Without a pole there is no side to prefer: the
     * tip swings towards the goal's own offset from the chain's line, the side the
     * descent would fold to anyway (a goal dead on that line takes a fixed side).
     *
     * <p>Nothing here can snap: the arc is zero when the goal needs no folding
     * (at or beyond the chain's current chord — so a chain switched on in its
     * rest pose stays exactly in it), grows continuously as the goal comes
     * closer, and fades out as the chain's own FK bend grows (a chain already
     * bent past {@link #STRAIGHT_FROM} of its reach folds along its own bend).
     */
    private static void preferredBend(IKTree tree, int e, Pole pole)
    {
        int rootIndex = pole == null ? tree.rootOf(e) : pole.rootJoint();
        IKJoint root = tree.joints[rootIndex];
        IKTree.Effector effector = tree.effectors[e];
        float reach = tree.reach(e);
        Vector3f chordDir = new Vector3f(effector.position).sub(root.position);
        float chord = chordDir.length();

        if (reach < EPS || chord < EPS)
        {
            return;
        }

        float straight = (chord / reach - STRAIGHT_FROM) / (1F - STRAIGHT_FROM);
        float ratio = root.position.distance(effector.goal) / chord;

        if (straight <= 0F || ratio >= 1F)
        {
            return;
        }

        straight = Math.min(1F, straight);
        chordDir.div(chord);

        Vector3f normal;

        if (pole != null)
        {
            normal = root.worldRotation.transform(new Vector3f(pole.bendNormal())).normalize();
        }
        else
        {
            /* normal = lateral × chord, so the −share fold below swings the tip towards the lateral offset. */
            Vector3f lateral = perpendicular(new Vector3f(effector.goal).sub(root.position), chordDir);

            if (lateral == null)
            {
                lateral = stablePerpendicular(chordDir);
            }

            normal = lateral.cross(chordDir).normalize();
        }

        int segments = 1;

        for (int j = effector.joint; j != rootIndex && j >= 0; j = tree.parentIndex[j])
        {
            segments++;
        }

        float share = arcTurn(ratio) / segments * straight * straight * (3F - 2F * straight);

        for (int j = childTowards(tree, rootIndex, effector.joint); j >= 0; j = j == effector.joint ? -1 : childTowards(tree, j, effector.joint))
        {
            if (tree.joints[j].isFree())
            {
                tree.rotateJointWorld(j, new Quaternionf().rotationAxis(-share, normal.x, normal.y, normal.z));
            }
        }
    }

    /**
     * The total turning angle of a circular arc whose chord is {@code ratio} of
     * its length: solves {@code sin(θ/2) / (θ/2) = ratio} (Newton from the
     * small-angle estimate), 0 at ratio 1, a full circle at ratio 0.
     */
    private static float arcTurn(float ratio)
    {
        if (ratio >= 1F)
        {
            return 0F;
        }

        if (ratio <= 0F)
        {
            return (float) (Math.PI * 2);
        }

        double x = Math.min(Math.PI, Math.sqrt(6.0 * (1.0 - ratio)));

        for (int i = 0; i < 8; i++)
        {
            double f = Math.sin(x) / x - ratio;
            double df = (x * Math.cos(x) - Math.sin(x)) / (x * x);

            if (Math.abs(df) < 1.0e-12)
            {
                break;
            }

            x = Math.max(1.0e-6, Math.min(Math.PI, x - f / df));
        }

        return (float) (2.0 * x);
    }

    /**
     * Pre-aligns a single chain WITHOUT a pole towards a goal in the BACK
     * half-space: when the goal sits more than 90° away from the current
     * root→effector direction, the chain's root rigidly turns the branch by the
     * EXCESS over 90° (zero at the boundary — poses stay continuous in the goal;
     * 90° at the exact antipode). This is the cure for the antipodal stall: with
     * the goal opposite the chain, every Jacobian column runs perpendicular to the
     * error and the iterations have no gradient to follow. Goals in the front
     * half-space are left alone — descent handles them. Skipped when the root has
     * locked or limited axes.
     */
    private static void alignToGoal(IKTree tree, int e)
    {
        int rootIndex = tree.rootOf(e);
        IKJoint root = tree.joints[rootIndex];

        if (!fullyMovable(root))
        {
            return;
        }

        IKTree.Effector effector = tree.effectors[e];
        Vector3f effectorDir = new Vector3f(effector.position).sub(root.position);
        Vector3f goalDir = new Vector3f(effector.goal).sub(root.position);

        if (effectorDir.lengthSquared() < EPS * EPS || goalDir.lengthSquared() < EPS * EPS)
        {
            return;
        }

        effectorDir.normalize();
        goalDir.normalize();

        float excess = effectorDir.angle(goalDir) - (float) (Math.PI / 2.0);

        if (excess <= 0F)
        {
            return;
        }

        Vector3f axis = new Vector3f(effectorDir).cross(goalDir);

        if (axis.lengthSquared() < EPS * EPS)
        {
            /* Exact antipode: the turn side is genuinely ambiguous — pick a deterministic perpendicular. */
            axis = stablePerpendicular(effectorDir);
        }
        else
        {
            axis.normalize();
        }

        tree.rotateJointWorld(rootIndex, new Quaternionf().rotationAxis(excess, axis.x, axis.y, axis.z));
    }

    /* ------------------------------------------------------------------ */
    /* Soft pole of DoF-constrained chains                                  */
    /* ------------------------------------------------------------------ */

    /**
     * One soft-pole attractor: three weak Jacobian rows pulling the pivot of
     * {@code joint} towards {@code target} (the pivot's two-segment spot on the
     * pole side). {@code moved} marks the joints whose channels the rows engage —
     * the chain's joints above the pivot, up to and including the chain root.
     */
    private record PoleBias(int joint, Vector3f target, float weight, float settle, boolean[] moved)
    {
    }

    /**
     * The pole of a DoF-CONSTRAINED chain (root or first interior joint not fully
     * movable), enforced softly through the rows: for every interior pivot of the
     * chain, a weak positional pull towards the spot the law of cosines puts it at
     * on the POLE SIDE of the root→goal line. The rows respect locks, limits and
     * stiffness by construction. Fully movable chains are excluded — their seed
     * already holds the side. {@code null} when nothing qualifies.
     */
    private static PoleBias[] buildBiases(IKTree tree, Pole[] poles)
    {
        if (poles == null)
        {
            return null;
        }

        List<PoleBias> biases = null;

        for (Pole pole : poles)
        {
            if (pole == null || pole.polePoint() == null)
            {
                continue;
            }

            int e = effectorUnder(tree, pole.rootJoint());

            if (e < 0)
            {
                continue;
            }

            IKTree.Effector effector = tree.effectors[e];
            int rootIndex = pole.rootJoint();
            int elbowIndex = childTowards(tree, rootIndex, effector.joint);

            if (elbowIndex < 0)
            {
                continue;
            }

            if (fullyMovable(tree.joints[rootIndex]) && fullyMovable(tree.joints[elbowIndex]))
            {
                continue;
            }

            Vector3f root = tree.joints[rootIndex].position;
            Vector3f goalDir = new Vector3f(effector.goal).sub(root);
            float goalDistance = goalDir.length();

            if (goalDistance < EPS)
            {
                continue;
            }

            goalDir.div(goalDistance);

            Vector3f side = perpendicular(new Vector3f(pole.polePoint()).sub(root), goalDir);

            if (side == null)
            {
                continue;
            }

            int count = 0;

            for (int j = effector.joint; j != rootIndex; j = tree.parentIndex[j])
            {
                count++;
            }

            int[] path = new int[count];
            int fill = count;

            for (int j = effector.joint; j != rootIndex; j = tree.parentIndex[j])
            {
                path[--fill] = j;
            }

            float totalArc = effector.startPosition.distance(tree.joints[effector.joint].startPosition);

            for (int k = 0; k < count; k++)
            {
                int above = k == 0 ? rootIndex : path[k - 1];

                totalArc += tree.joints[path[k]].startPosition.distance(tree.joints[above].startPosition);
            }

            float arc = 0F;

            for (int k = 0; k < count; k++)
            {
                int above = k == 0 ? rootIndex : path[k - 1];

                arc += tree.joints[path[k]].startPosition.distance(tree.joints[above].startPosition);

                float a = arc;
                float b = totalArc - arc;

                if (a < EPS || b < EPS)
                {
                    continue;
                }

                /* β capped at 120°: a goal inside the two-segment annulus must not
                 * land the spot at −goalDir with the side signal gone. */
                float cosBeta = Math.min(1F, Math.max(-0.5F, (goalDistance * goalDistance + a * a - b * b) / (2F * goalDistance * a)));
                float sinBeta = (float) Math.sqrt(Math.max(0F, 1F - cosBeta * cosBeta));
                Vector3f target = new Vector3f(root).fma(a * cosBeta, goalDir).fma(a * sinBeta, side);
                boolean[] moved = new boolean[tree.joints.length];

                for (int j = tree.parentIndex[path[k]]; j >= 0; j = tree.parentIndex[j])
                {
                    moved[j] = true;

                    if (j == rootIndex)
                    {
                        break;
                    }
                }

                if (biases == null)
                {
                    biases = new ArrayList<>();
                }

                biases.add(new PoleBias(path[k], target, POLE_BIAS * effector.weight, POLE_BIAS_SETTLE * a, moved));
            }
        }

        return biases == null ? null : biases.toArray(new PoleBias[0]);
    }

    /** The largest distance a biased pivot still sits beyond its settle band; 0 = all settled. */
    private static float worstBiasExcess(IKTree tree, PoleBias[] biases)
    {
        if (biases == null)
        {
            return 0F;
        }

        float worst = 0F;

        for (PoleBias bias : biases)
        {
            worst = Math.max(worst, tree.joints[bias.joint()].position.distance(bias.target()) - bias.settle());
        }

        return worst;
    }

    /* ------------------------------------------------------------------ */
    /* Iteration                                                            */
    /* ------------------------------------------------------------------ */

    /** One damped-least-squares iteration; false when the step stalls. */
    private static boolean step(IKTree tree, float lambdaSq, float biasScale, PoleBias[] biases)
    {
        IKJoint[] joints = tree.joints;
        int n = joints.length;
        int k = tree.effectors.length;
        float errorBefore = combinedError(tree, biasScale, biases);

        /* Row layout: 3 position rows per effector, plus 3 pull rows per soft pole attractor. */
        int rows = k * 3;
        int[] biasOffset = null;

        if (biases != null)
        {
            biasOffset = new int[biases.length];

            for (int p = 0; p < biases.length; p++)
            {
                biasOffset[p] = rows;
                rows += 3;
            }
        }

        float[] b = new float[rows];

        for (int e = 0; e < k; e++)
        {
            IKTree.Effector effector = tree.effectors[e];

            b[e * 3] = effector.weight * (effector.goal.x - effector.position.x);
            b[e * 3 + 1] = effector.weight * (effector.goal.y - effector.position.y);
            b[e * 3 + 2] = effector.weight * (effector.goal.z - effector.position.z);
        }

        if (biases != null)
        {
            for (int p = 0; p < biases.length; p++)
            {
                PoleBias bias = biases[p];
                Vector3f pivot = tree.joints[bias.joint()].position;
                float pull = bias.weight() * biasScale;
                int at = biasOffset[p];

                b[at] = pull * (bias.target().x - pivot.x);
                b[at + 1] = pull * (bias.target().y - pivot.y);
                b[at + 2] = pull * (bias.target().z - pivot.z);
            }
        }

        /* Weighted Jacobian columns: per degree of freedom a rows-long vector —
         * colWeight · rowWeight · (axis × (point − pivot)) for every effector or
         * biased pivot the joint moves. A channel pinned by its limits never enters. */
        float[] columns = new float[n * 3 * rows];
        boolean[] frozen = new boolean[n * 3];
        Vector3f axis = new Vector3f();
        Vector3f lever = new Vector3f();

        for (int i = 0; i < n; i++)
        {
            IKJoint joint = joints[i];

            for (int c = 0; c < 3; c++)
            {
                float w = movableWeight(joint, c);

                if (w <= 0F)
                {
                    frozen[i * 3 + c] = true;
                    continue;
                }

                tree.channelAxis(i, c, axis);

                int at = (i * 3 + c) * rows;

                for (int e = 0; e < k; e++)
                {
                    if (!tree.moves(e, i))
                    {
                        continue;
                    }

                    IKTree.Effector effector = tree.effectors[e];
                    int offset = at + e * 3;

                    lever.set(effector.position).sub(joint.position);
                    axis.cross(lever, lever).mul(w * effector.weight);

                    columns[offset] = lever.x;
                    columns[offset + 1] = lever.y;
                    columns[offset + 2] = lever.z;
                }

                if (biases != null)
                {
                    for (int p = 0; p < biases.length; p++)
                    {
                        PoleBias bias = biases[p];

                        if (!bias.moved()[i])
                        {
                            continue;
                        }

                        int offset = at + biasOffset[p];

                        lever.set(tree.joints[bias.joint()].position).sub(joint.position);
                        axis.cross(lever, lever).mul(w * bias.weight() * biasScale);

                        columns[offset] = lever.x;
                        columns[offset + 1] = lever.y;
                        columns[offset + 2] = lever.z;
                    }
                }
            }
        }

        Snapshot saved = new Snapshot(tree);

        /* Phase 1: the full damped Gauss-Newton step. */
        float[] deltas = solveDeltas(tree, columns, frozen, b, lambdaSq);

        if (deltas == null)
        {
            return false;
        }

        float largest = applyDeltas(tree, deltas, 1F);

        tree.forward();

        if (combinedError(tree, biasScale, biases) <= errorBefore)
        {
            return largest > STALL_STEP;
        }

        /* Phase 2 (active set): when the limit clamp CUT part of that step, what
         * was applied is no longer a descent direction — freeze the cut channels
         * and re-solve over what can actually move. Free joints are never cut. */
        boolean cut = false;

        for (int i = 0; i < n; i++)
        {
            IKJoint joint = joints[i];

            if (joint.isFree())
            {
                continue;
            }

            for (int c = 0; c < 3; c++)
            {
                int at = i * 3 + c;

                if (!frozen[at] && deltas[at] != 0F && Math.abs(IKJoint.get(joint.angles, c) - (IKJoint.get(saved.angles[i], c) + deltas[at])) > 1.0e-7f)
                {
                    frozen[at] = true;
                    cut = true;
                }
            }
        }

        saved.restore(tree);

        if (cut)
        {
            deltas = solveDeltas(tree, columns, frozen, b, lambdaSq);

            if (deltas != null)
            {
                largest = applyDeltas(tree, deltas, 1F);
                tree.forward();

                if (combinedError(tree, biasScale, biases) <= errorBefore)
                {
                    return largest > STALL_STEP;
                }

                saved.restore(tree);
            }
        }

        /* Phase 3 (backtracking): the step overshot — retry at halved scales; if
         * even the smallest scale is worse, this is the local best — stop. */
        if (deltas != null)
        {
            float scale = 0.5F;

            for (int attempt = 0; attempt < 6; attempt++)
            {
                largest = applyDeltas(tree, deltas, scale);
                tree.forward();

                if (combinedError(tree, biasScale, biases) <= errorBefore)
                {
                    return largest > STALL_STEP;
                }

                saved.restore(tree);
                scale *= 0.5F;
            }
        }

        tree.forward();

        return false;
    }

    /** The joints' variables at one moment, to undo a rejected step. */
    private static final class Snapshot
    {
        final Quaternionf[] locals;
        final Vector3f[] angles;

        Snapshot(IKTree tree)
        {
            this.locals = new Quaternionf[tree.joints.length];
            this.angles = new Vector3f[tree.joints.length];

            for (int i = 0; i < tree.joints.length; i++)
            {
                this.locals[i] = new Quaternionf(tree.joints[i].local);
                this.angles[i] = new Vector3f(tree.joints[i].angles);
            }
        }

        void restore(IKTree tree)
        {
            for (int i = 0; i < tree.joints.length; i++)
            {
                tree.joints[i].local.set(this.locals[i]);
                tree.joints[i].angles.set(this.angles[i]);
            }
        }
    }

    /**
     * Solves the damped normal equations over the unfrozen columns and returns
     * the per-channel deltas {@code W·Jᵀ·y}, the whole vector scaled down when its
     * largest entry exceeds {@link #MAX_STEP} (direction preserved — a
     * per-channel clamp would degenerate the step into a vector of signs);
     * {@code null} when the system is singular.
     */
    private static float[] solveDeltas(IKTree tree, float[] columns, boolean[] frozen, float[] b, float lambdaSq)
    {
        IKJoint[] joints = tree.joints;
        int n = joints.length;
        int rows = b.length;
        float[] a = new float[rows * rows];

        for (int r = 0; r < rows; r++)
        {
            a[r * rows + r] = lambdaSq;
        }

        for (int at = 0; at < n * 3; at++)
        {
            if (frozen[at])
            {
                continue;
            }

            int base = at * rows;

            for (int r = 0; r < rows; r++)
            {
                float cr = columns[base + r];

                if (cr == 0F)
                {
                    continue;
                }

                for (int s = r; s < rows; s++)
                {
                    a[r * rows + s] += cr * columns[base + s];
                }
            }
        }

        float[] y = solveCholesky(a, b, rows);

        if (y == null)
        {
            return null;
        }

        float[] deltas = new float[n * 3];
        float largest = 0F;

        for (int i = 0; i < n; i++)
        {
            IKJoint joint = joints[i];

            for (int c = 0; c < 3; c++)
            {
                int at = i * 3 + c;

                if (frozen[at])
                {
                    continue;
                }

                float delta = 0F;
                int base = at * rows;

                for (int r = 0; r < rows; r++)
                {
                    delta += columns[base + r] * y[r];
                }

                deltas[at] = movableWeight(joint, c) * delta;
                largest = Math.max(largest, Math.abs(deltas[at]));
            }
        }

        if (largest > MAX_STEP)
        {
            float scale = MAX_STEP / largest;

            for (int at = 0; at < n * 3; at++)
            {
                deltas[at] *= scale;
            }
        }

        return deltas;
    }

    /**
     * Solves the symmetric positive-definite system A·y = b (upper triangle of A
     * filled) by Cholesky decomposition; {@code null} when not positive definite.
     */
    private static float[] solveCholesky(float[] a, float[] b, int size)
    {
        for (int i = 0; i < size; i++)
        {
            for (int j = 0; j <= i; j++)
            {
                float sum = a[Math.min(i, j) * size + Math.max(i, j)];

                for (int m = 0; m < j; m++)
                {
                    sum -= a[i * size + m] * a[j * size + m];
                }

                if (i == j)
                {
                    if (sum <= 0F)
                    {
                        return null;
                    }

                    a[i * size + i] = (float) Math.sqrt(sum);
                }
                else
                {
                    a[i * size + j] = sum / a[j * size + j];
                }
            }
        }

        float[] y = new float[size];

        for (int i = 0; i < size; i++)
        {
            float sum = b[i];

            for (int m = 0; m < i; m++)
            {
                sum -= a[i * size + m] * y[m];
            }

            y[i] = sum / a[i * size + i];
        }

        for (int i = size - 1; i >= 0; i--)
        {
            float sum = y[i];

            for (int m = i + 1; m < size; m++)
            {
                sum -= a[m * size + i] * y[m];
            }

            y[i] = sum / a[i * size + i];
        }

        return y;
    }

    /** Applies {@code scale}-sized deltas onto the joints; returns the largest applied delta. */
    private static float applyDeltas(IKTree tree, float[] deltas, float scale)
    {
        float largest = 0F;

        for (int i = 0; i < tree.joints.length; i++)
        {
            float dx = deltas[i * 3] * scale;
            float dy = deltas[i * 3 + 1] * scale;
            float dz = deltas[i * 3 + 2] * scale;

            largest = Math.max(largest, Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))));

            if (dx != 0F || dy != 0F || dz != 0F)
            {
                tree.step(i, dx, dy, dz);
            }
        }

        return largest;
    }

    /* ------------------------------------------------------------------ */
    /* Helpers                                                              */
    /* ------------------------------------------------------------------ */

    /** Whether every axis of the joint may move — no locks, limits or zeroed weight. */
    private static boolean fullyMovable(IKJoint joint)
    {
        for (int c = 0; c < 3; c++)
        {
            if (joint.locked[c] || joint.limited[c] || joint.weight(c) <= 0F)
            {
                return false;
            }
        }

        return true;
    }

    /** How much this channel may move at all: 0 when locked, weightless, or pinned by min == max limits. */
    private static float movableWeight(IKJoint joint, int axis)
    {
        if (joint.locked[axis] || (joint.limited[axis] && joint.limitMin[axis] >= joint.limitMax[axis]))
        {
            return 0F;
        }

        return joint.weight(axis);
    }

    /** How many channels can move at all — zero means there is nothing to solve with. */
    private static int countColumns(IKTree tree)
    {
        int columns = 0;

        for (IKJoint joint : tree.joints)
        {
            for (int c = 0; c < 3; c++)
            {
                if (movableWeight(joint, c) > 0F)
                {
                    columns++;
                }
            }
        }

        return columns;
    }

    /** The weighted least-squares error a step must not increase — positions and soft poles. */
    private static float combinedError(IKTree tree, float biasScale, PoleBias[] biases)
    {
        float sum = 0F;

        for (IKTree.Effector effector : tree.effectors)
        {
            float distance = effector.position.distance(effector.goal) * effector.weight;

            sum += distance * distance;
        }

        if (biases != null)
        {
            for (PoleBias bias : biases)
            {
                float pull = tree.joints[bias.joint()].position.distance(bias.target()) * bias.weight() * biasScale;

                sum += pull * pull;
            }
        }

        return (float) Math.sqrt(sum);
    }

    /** The largest plain effector-to-goal distance — the user-facing "did it reach". */
    private static float worstError(IKTree tree)
    {
        float worst = 0F;

        for (IKTree.Effector effector : tree.effectors)
        {
            worst = Math.max(worst, effector.position.distance(effector.goal));
        }

        return worst;
    }

    private static float largestReach(IKTree tree)
    {
        float largest = 0F;

        for (int e = 0; e < tree.effectors.length; e++)
        {
            largest = Math.max(largest, tree.reach(e));
        }

        return largest;
    }

    private static Pole firstPole(Pole[] poles)
    {
        return poles == null || poles.length == 0 || poles[0] == null || poles[0].polePoint() == null ? null : poles[0];
    }

    /** The first effector whose branch passes through the given joint. */
    private static int effectorUnder(IKTree tree, int joint)
    {
        for (int e = 0; e < tree.effectors.length; e++)
        {
            if (tree.moves(e, joint))
            {
                return e;
            }
        }

        return -1;
    }

    /** The next joint after {@code from} on the path down to {@code descendant}; -1 when none. */
    private static int childTowards(IKTree tree, int from, int descendant)
    {
        int j = descendant;
        int previous = -1;

        while (j >= 0 && j != from)
        {
            previous = j;
            j = tree.parentIndex[j];
        }

        return j == from ? previous : -1;
    }

    /** {@code v} minus its component along unit {@code axis}, normalized; {@code null} when degenerate. */
    private static Vector3f perpendicular(Vector3f v, Vector3f axis)
    {
        v.fma(-v.dot(axis), axis);

        return v.lengthSquared() < EPS * EPS ? null : v.normalize();
    }

    /** A deterministic unit perpendicular to {@code dir}: cross with world Z, falling back to world Y. */
    private static Vector3f stablePerpendicular(Vector3f dir)
    {
        Vector3f perp = new Vector3f(dir).cross(0F, 0F, 1F);

        if (perp.lengthSquared() < EPS * EPS)
        {
            perp.set(dir).cross(0F, 1F, 0F);
        }

        return perp.normalize();
    }
}
