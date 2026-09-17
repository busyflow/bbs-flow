package mchorse.bbs_mod.utils.animation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Per-actor animation phase: the one number that stops a crowd of replays marching in step.
 *
 * <p>Nothing about a film's playback is per-actor. Two replays walking the same speed accumulate
 * the same limb distance, so vanilla's swing lands on the same frame of the same cosine for both;
 * two replays that start moving on the same tick start their model's walk animation on the same
 * tick, so it loops in lockstep for as long as they walk. Both are correct, and together they are
 * the thing that reads as uncanny - a dozen people whose arms all reach forward at once.</p>
 *
 * <p>A phase is a fraction of one cycle, {@code [0, 1)}, held on the replay and applied where the
 * animation is read rather than where it is stored. Reading is the important half: the accumulator
 * is reset by scrubbing, by re-entering the film, by the entity being rebuilt, and an offset baked
 * into it would be lost every time. Applied on read it survives all of that, and the same number
 * shifts the procedural swing and the model's own loop by the same fraction, so an actor's arms,
 * legs and walk cycle stay in agreement with each other while disagreeing with everyone else.</p>
 */
public class DesyncPhase
{
    /**
     * How much limb distance one full swing takes.
     *
     * <p>The swing is {@code cos(limbPos * 0.6662)} (see {@code ProceduralAnimator}), so a whole
     * cycle is {@code 2pi / 0.6662} of accumulated distance. Shifting by exactly this is a no-op,
     * which is the property that makes a fraction of it a phase.</p>
     */
    public static final float LIMB_CYCLE = (float) (Math.PI * 2D / 0.6662D);

    /**
     * Fraction of its slot a stratified phase may wander, leaving the rest as a gap.
     *
     * <p>Purely random phases clump - with four actors it is unremarkable to draw two within a
     * twentieth of a cycle of each other, and those two still visibly march together, which is the
     * complaint. Slots guarantee the spacing and the jitter keeps the result from looking like a
     * fixed pattern.</p>
     */
    private static final float JITTER = 0.7F;

    /** Wrap any phase into {@code [0, 1)}, including a negative one. */
    public static float normalize(float phase)
    {
        if (!Float.isFinite(phase))
        {
            return 0F;
        }

        float wrapped = phase % 1F;

        return wrapped < 0F ? wrapped + 1F : wrapped;
    }

    /** The limb-distance offset a phase stands for. */
    public static float limbOffset(float phase)
    {
        return normalize(phase) * LIMB_CYCLE;
    }

    /**
     * Shift a looping animation's playhead by a phase, staying inside the animation.
     *
     * <p>The tick is wrapped rather than clamped because the animation loops: a phase of 0.9 on a
     * clip 20 ticks long asks for tick 18 onwards, and past the end it belongs at the start.</p>
     *
     * @param tick     the playhead the animation reached on its own
     * @param phase    fraction of the loop to shift by
     * @param duration length of the loop in ticks
     */
    public static float offsetTicks(float tick, float phase, float duration)
    {
        if (duration <= 0F)
        {
            return tick;
        }

        float shifted = (tick + normalize(phase) * duration) % duration;

        return shifted < 0F ? shifted + duration : shifted;
    }

    /**
     * {@code count} phases spread over the cycle, in a random order.
     *
     * <p>Spread rather than drawn independently, for the reason {@link #JITTER} gives. The order is
     * shuffled so that the caller can hand these out in list order without the first replay always
     * being the one left near zero - two runs over the same selection should look different.</p>
     */
    public static List<Float> stratified(int count, Random random)
    {
        List<Float> phases = new ArrayList<>();

        if (count <= 0)
        {
            return phases;
        }

        float slot = 1F / count;

        for (int i = 0; i < count; i++)
        {
            float margin = slot * (1F - JITTER) / 2F;

            phases.add(i * slot + margin + random.nextFloat() * slot * JITTER);
        }

        /* Fisher-Yates, so every ordering is as likely as every other. */
        for (int i = phases.size() - 1; i > 0; i--)
        {
            int j = random.nextInt(i + 1);
            Float swap = phases.get(i);

            phases.set(i, phases.get(j));
            phases.set(j, swap);
        }

        return phases;
    }
}
