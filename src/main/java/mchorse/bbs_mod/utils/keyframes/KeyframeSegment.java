package mchorse.bbs_mod.utils.keyframes;

import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;

/**
 * Keyframe segment.
 *
 * This structure contains all the necessary data to create an interpolated
 * value.
 */
public class KeyframeSegment <T>
{
    public Keyframe<T> a;
    public Keyframe<T> b;

    public Keyframe<T> preA;
    public Keyframe<T> postB;
    /** Outgoing key at b; a loop seam can start again at a different source key. */
    public Keyframe<T> nextStart;
    public float duration;
    public float offset;
    public float x;
    /** Difference between film time and the source pass of a finite loop. */
    public float timeOffset;

    public KeyframeSegment()
    {}

    public KeyframeSegment(Keyframe<T> a, Keyframe<T> b)
    {
        this.fill(a, b);
    }

    public KeyframeSegment(Keyframe<T> a, Keyframe<T> b, int index)
    {
        this.fill(a, b, index);
    }

    public void setup(Keyframe<T> a, Keyframe<T> b, float ticks)
    {
        this.fill(a, b);
        this.setup(ticks);
    }

    public void fill(Keyframe<T> a, Keyframe<T> b)
    {
        KeyframeChannel<T> channel = (KeyframeChannel<T>) a.getParent();

        this.fill(a, b, channel.indexOf(a));
    }

    /**
     * Fill with the index of {@code a} already in hand. The binary search that finds a segment
     * knows this index; taking it here keeps the neighbour lookup O(1) instead of re-scanning
     * the channel for a keyframe the caller just pulled out of it.
     */
    public void fill(Keyframe<T> a, Keyframe<T> b, int index)
    {
        this.timeOffset = 0;
        this.a = a;
        this.b = b;
        this.nextStart = b;

        KeyframeChannel<T> channel = (KeyframeChannel<T>) a.getParent();

        if (index >= 0)
        {
            Keyframe<T> preA = channel.get(channel.previousEnabledIndex(index - 1));
            Keyframe<T> postB = channel.get(channel.nextEnabledIndex(channel.nextEnabledIndex(index + 1) + 1));

            this.preA = preA == null ? a : preA;
            this.postB = postB == null ? b : postB;
        }
        else
        {
            this.preA = a;
            this.postB = b;
        }
    }

    public void setup(float ticks)
    {
        ticks -= this.timeOffset;
        float forcedDuration = this.a.getDuration();

        this.duration = forcedDuration > 0 ? forcedDuration : this.b.getTick() - this.a.getTick();
        this.offset = ticks - this.a.getTick();
        this.x = MathUtils.clamp(this.duration == 0 ? 0F : this.offset / this.duration, 0F, 1F);
    }

    public T createInterpolated()
    {
        return this.createInterpolated(null);
    }

    public T createInterpolated(T defaults)
    {
        IKeyframeFactory<T> factory = this.a.getFactory();

        if (this.isSame())
        {
            return factory.copy(defaults == null ? this.a.getValue() : factory.withDefaults(this.a.getValue(), defaults));
        }

        double duration = IInterp.context.duration;
        double startTick = IInterp.context.startTick;
        boolean isStart = IInterp.context.isStart;
        boolean isEnd = IInterp.context.isEnd;

        IInterp.context.segment(this.duration, this.a.getTick(), this.preA == this.a, this.postB == this.b);

        try
        {
            return factory.copy(factory.interpolate(this.resolved(this.preA, defaults), this.resolved(this.a, defaults),
                this.resolved(this.b, defaults), this.resolved(this.postB, defaults), this.a.getInterpolation(), this.getInterpolationProgress()));
        }
        finally
        {
            IInterp.context.segment(duration, startTick, isStart, isEnd);
        }
    }

    private Keyframe<T> resolved(Keyframe<T> key, T defaults)
    {
        if (defaults == null)
        {
            return key;
        }

        T value = key.getValue();
        T resolved = key.getFactory().withDefaults(value, defaults);

        return resolved == value ? key : new Keyframe<>(key.getId(), key.getFactory(), key.getTick(), resolved);
    }

    public float getInterpolationProgress()
    {
        return this.getInterpolationProgress(false);
    }

    /**
     * Remap time without changing the value curve. Adjacent splines share the
     * time derivative at their common key, preserving an already smooth join.
     * Cubic model animations store the interpolation on the destination key.
     */
    public float getInterpolationProgress(boolean destinationInterpolation)
    {
        float progress = this.a.remapMotion(this.x);
        Keyframe<T> interpolationKey = destinationInterpolation ? this.b : this.a;

        if (this.x <= 0F || this.x >= 1F || !this.a.supportsMotionShift()
            || !isSpline(interpolationKey) || !hasFullDuration(this.a, this.b))
        {
            return progress;
        }

        double midpoint = 0.5D + this.a.getMotionShift();
        double startSlope = (1D - midpoint) / midpoint;
        double endSlope = midpoint / (1D - midpoint);
        double start = startSlope;
        double end = endSlope;

        if (this.preA.supportsMotionShift() && hasFullDuration(this.preA, this.a)
            && isSpline(destinationInterpolation ? this.a : this.preA))
        {
            double previousMidpoint = 0.5D + this.preA.getMotionShift();

            start = Math.sqrt(startSlope * previousMidpoint / (1D - previousMidpoint));
        }

        if (this.nextStart.supportsMotionShift() && hasFullDuration(this.nextStart, this.postB)
            && isSpline(destinationInterpolation ? this.postB : this.nextStart))
        {
            double nextMidpoint = 0.5D + this.nextStart.getMotionShift();

            end = Math.sqrt(endSlope * (1D - nextMidpoint) / nextMidpoint);
        }

        if (start == startSlope && end == endSlope)
        {
            return progress;
        }

        /* Two monotone rational quadratics retain f(midpoint) = 0.5 and
         * meet with the original warp's derivative at the handle. Positive
         * derivatives keep time moving forward even at extreme shifts. */
        double middleSlope = 1D / (4D * midpoint * (1D - midpoint));

        return this.x <= midpoint
            ? (float) remapHalf(this.x / midpoint, midpoint, start, middleSlope)
            : (float) (0.5D + remapHalf((this.x - midpoint) / (1D - midpoint), 1D - midpoint, middleSlope, end));
    }

    private static boolean isSpline(Keyframe<?> keyframe)
    {
        IInterp interp = keyframe.getInterpolation();

        return interp.has(Interpolations.CUBIC) || interp.has(Interpolations.HERMITE)
            || interp.has(Interpolations.BSPLINE) || interp.has(Interpolations.AUTO)
            || interp.has(Interpolations.AUTO_CLAMPED);
    }

    private static boolean hasFullDuration(Keyframe<?> start, Keyframe<?> end)
    {
        float gap = end.getTick() - start.getTick();

        /* An explicit hold or a truncated transition has no smooth join. */
        return gap > 0F && (start.getDuration() <= 0F || start.getDuration() == gap);
    }

    private static double remapHalf(double t, double width, double start, double end)
    {
        double secant = 0.5D / width;
        double product = t * (1D - t);

        return 0.5D * (secant * t * t + start * product)
            / (secant + (start + end - 2D * secant) * product);
    }

    public boolean isSame()
    {
        return this.a == this.b;
    }

    public Keyframe<T> getClosest()
    {
        return this.x > 0.5F ? this.b : this.a;
    }
}
