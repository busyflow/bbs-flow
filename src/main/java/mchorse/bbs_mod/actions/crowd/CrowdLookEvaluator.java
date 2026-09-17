package mchorse.bbs_mod.actions.crowd;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.interps.AutoBezier;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.InterpContext;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.BezierUtils;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

/** Exact, allocation-light evaluator shared by server playback and client render sampling. */
public final class CrowdLookEvaluator
{
    private static final double DEFAULT_TARGET_EYE_HEIGHT = 1.62D;

    private CrowdLookEvaluator()
    {}

    public static Sample sample(Film film, String crowdReplayId, float filmTick)
    {
        return sample(film, film == null ? null : (Replay) film.replays.get(crowdReplayId), filmTick);
    }

    public static Sample sample(Film film, Replay crowdReplay, float filmTick)
    {
        return sample(film, crowdReplay, filmTick, null);
    }

    public static Sample sample(Film film, Replay crowdReplay, float filmTick, TargetResolver resolver)
    {
        if (film == null || crowdReplay == null || crowdReplay.keyframes.crowdLookTarget.isEmpty())
        {
            return null;
        }

        float localTick = localTick(crowdReplay, filmTick);
        Keyframe<String> first = crowdReplay.keyframes.crowdLookTarget.get(0);

        if (first == null || localTick < first.getTick())
        {
            return null;
        }

        KeyframeSegment<String> segment = crowdReplay.keyframes.crowdLookTarget.find(localTick);

        if (segment == null)
        {
            return null;
        }

        CrowdLookTarget controlA = CrowdLookTarget.parse(segment.a.getValue());
        CrowdLookTarget controlB = CrowdLookTarget.parse(segment.b.getValue());
        Replay targetA = (Replay) film.replays.get(controlA.replayId());
        Replay targetB = (Replay) film.replays.get(controlB.replayId());

        if (targetA == null && targetB == null)
        {
            return null;
        }

        Vec3d a = targetA == null ? null : targetPosition(targetA, filmTick, resolver);
        Vec3d b = targetB == null ? null : targetPosition(targetB, filmTick, resolver);
        double progress = segment.isSame() ? 0D : progress(segment);

        CrowdLookTarget control = progress >= 0.5D ? controlB : controlA;

        return new Sample(a, b, MathUtils.clamp(progress, 0D, 1D), control);
    }

    /** Return wrapped yaw and pitch. Angles, rather than target coordinates, are interpolated so
     * opposite-side targets never make the crowd spin the long way around or pass a singularity. */
    public static boolean rotation(double eyeX, double eyeY, double eyeZ, Sample sample, float[] output)
    {
        float curYaw = output != null && output.length > 0 ? output[0] : 0F;
        float curPitch = output != null && output.length > 1 ? output[1] : 0F;

        return rotation(eyeX, eyeY, eyeZ, curYaw, curPitch, sample, output);
    }

    public static boolean rotation(double eyeX, double eyeY, double eyeZ, float currentYaw, float currentPitch, Sample sample, float[] output)
    {
        if (output == null || output.length < 2 || sample == null)
        {
            return false;
        }

        float yawA = currentYaw;
        float pitchA = currentPitch;
        boolean hasA = false;

        if (sample.a != null)
        {
            if (rotationTo(eyeX, eyeY, eyeZ, sample.a, output))
            {
                yawA = output[0];
                pitchA = output[1];
                hasA = true;
            }
        }

        float yawB = currentYaw;
        float pitchB = currentPitch;
        boolean hasB = false;

        if (sample.b != null)
        {
            if (rotationTo(eyeX, eyeY, eyeZ, sample.b, output))
            {
                yawB = output[0];
                pitchB = output[1];
                hasB = true;
            }
        }

        if (!hasA && !hasB)
        {
            output[0] = currentYaw;
            output[1] = currentPitch;

            return false;
        }

        if (!hasA)
        {
            yawA = currentYaw;
            pitchA = currentPitch;
        }

        if (!hasB)
        {
            yawB = currentYaw;
            pitchB = currentPitch;
        }

        if (sample.progress <= 0D)
        {
            output[0] = yawA;
            output[1] = pitchA;

            return true;
        }

        if (sample.progress >= 1D)
        {
            output[0] = yawB;
            output[1] = pitchB;

            return true;
        }

        float progress = (float) sample.progress;
        output[0] = MathHelper.wrapDegrees(yawA + MathHelper.wrapDegrees(yawB - yawA) * progress);
        output[1] = MathHelper.clamp(MathHelper.lerp(progress, pitchA, pitchB), -90F, 90F);

        return true;
    }

    private static boolean rotationTo(double eyeX, double eyeY, double eyeZ, Vec3d target, float[] output)
    {
        double dx = target.x - eyeX;
        double dy = target.y - eyeY;
        double dz = target.z - eyeZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        if (horizontal < 0.000001D && Math.abs(dy) < 0.000001D)
        {
            return false;
        }

        output[0] = (float) (Math.atan2(dz, dx) * (180D / Math.PI) - 90D);
        output[1] = MathHelper.clamp((float) (-Math.atan2(dy, horizontal) * (180D / Math.PI)), -90F, 90F);

        return true;
    }

    private static double progress(KeyframeSegment<String> segment)
    {
        if (segment.a.getInterpolation().has(Interpolations.BEZIER))
        {
            return BezierUtils.get(0D, 1D,
                segment.a.getTick(), segment.b.getTick(),
                segment.a.rx, segment.a.ry, segment.b.lx, segment.b.ly,
                segment.x);
        }

        if (segment.a.getInterpolation().has(Interpolations.AUTO) || segment.a.getInterpolation().has(Interpolations.AUTO_CLAMPED))
        {
            return AutoBezier.get(0D, 0D, 1D, 1D,
                segment.preA.getTick(), segment.a.getTick(), segment.b.getTick(), segment.postB.getTick(),
                segment.a.getInterpolation().has(Interpolations.AUTO_CLAMPED), segment.x);
        }

        double oldDuration = IInterp.context.duration;
        double oldStartTick = IInterp.context.startTick;
        IInterp.context.segment(segment.duration, segment.a.getTick());

        try
        {
            InterpContext context = new InterpContext().set(0D, 0D, 1D, 1D, segment.x);
            context.segment(segment.duration, segment.a.getTick());
            context.isStart = segment.preA == segment.a;
            context.isEnd = segment.postB == segment.b;

            return segment.a.getInterpolation().interpolate(context);
        }
        finally
        {
            IInterp.context.segment(oldDuration, oldStartTick);
        }
    }

    private static Vec3d replayPosition(Replay replay, float filmTick)
    {
        float tick = localTick(replay, filmTick);
        double x = replay.keyframes.x.interpolate(tick);
        double y = replay.keyframes.y.interpolate(tick);
        double z = replay.keyframes.z.interpolate(tick);

        if (replay.relative.get())
        {
            Vector3d origin = replay.getRelativeOrigin();
            x += origin.x;
            y += origin.y;
            z += origin.z;
        }

        return new Vec3d(x, y + DEFAULT_TARGET_EYE_HEIGHT, z);
    }

    private static Vec3d targetPosition(Replay replay, float filmTick, TargetResolver resolver)
    {
        Vec3d resolved = resolver == null ? null : resolver.resolve(replay, filmTick);

        return resolved == null ? replayPosition(replay, filmTick) : resolved;
    }

    private static float localTick(Replay replay, float filmTick)
    {
        int loop = replay.looping.get();

        if (loop <= 0)
        {
            return filmTick;
        }

        float wrapped = filmTick % loop;

        return wrapped < 0F ? wrapped + loop : wrapped;
    }

    public record Sample(Vec3d a, Vec3d b, double progress, CrowdLookTarget control)
    {}

    /** Supplies the live rendered position of a target replay when one exists. */
    @FunctionalInterface
    public interface TargetResolver
    {
        Vec3d resolve(Replay replay, float filmTick);
    }
}
