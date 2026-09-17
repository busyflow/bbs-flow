package mchorse.bbs_mod.utils.animation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The arithmetic behind the desync button.
 *
 * <p>What the button has to be worth is one thing: that afterwards no two selected replays are at
 * the same point of the same cycle, and that none of them has been moved to a point that is not on
 * the cycle at all. Everything else is the UI and the entity plumbing.</p>
 */
public class DesyncPhaseTest
{
    /** Fixed seeds: a spread that is only usually good is a spread that usually gets reported. */
    private static Random seeded()
    {
        return new Random(20260830L);
    }

    @Test
    public void aWholeCycleIsTheSamePoseAgain()
    {
        /* The premise: the offset is a fraction of this, so this number being right is what makes
         * a phase of 1 the same as a phase of 0. */
        assertEquals(
            Math.cos(0),
            Math.cos(DesyncPhase.LIMB_CYCLE * 0.6662F),
            0.0001F,
            "one LIMB_CYCLE of limb distance should land back on the same swing"
        );
    }

    @Test
    public void phasesWrapRatherThanRun()
    {
        assertEquals(0.25F, DesyncPhase.normalize(0.25F), 0.0001F);
        assertEquals(0.25F, DesyncPhase.normalize(3.25F), 0.0001F, "a phase past the end belongs back at the start");
        assertEquals(0.75F, DesyncPhase.normalize(-0.25F), 0.0001F, "a negative phase counts backwards from a full cycle");
        assertEquals(0F, DesyncPhase.normalize(Float.NaN), 0.0001F, "nonsense should be the default, not propagated");
    }

    @Test
    public void aLoopIsShiftedInsideItself()
    {
        /* Half of a twenty tick loop, from tick 4, is tick 14 - still a tick of that animation. */
        assertEquals(14F, DesyncPhase.offsetTicks(4F, 0.5F, 20F), 0.0001F);

        /* And from tick 16 it is tick 6, because past the end the loop has started again. */
        assertEquals(6F, DesyncPhase.offsetTicks(16F, 0.5F, 20F), 0.0001F);

        for (float tick = 0F; tick < 20F; tick += 0.37F)
        {
            float shifted = DesyncPhase.offsetTicks(tick, 0.8F, 20F);

            assertTrue(shifted >= 0F && shifted < 20F, "a shifted tick left the animation: " + shifted);
        }
    }

    @Test
    public void aZeroLengthLoopIsLeftAlone()
    {
        /* An animation with no length has no cycle to be anywhere in, and dividing by it is how
         * that would otherwise be discovered. */
        assertEquals(7F, DesyncPhase.offsetTicks(7F, 0.5F, 0F), 0.0001F);
    }

    @Test
    public void everyPhaseIsOnTheCycle()
    {
        for (int count = 1; count <= 64; count++)
        {
            for (Float phase : DesyncPhase.stratified(count, seeded()))
            {
                assertTrue(phase >= 0F && phase < 1F, "phase off the cycle: " + phase);
            }
        }
    }

    @Test
    public void asManyPhasesAsThereAreReplays()
    {
        assertEquals(0, DesyncPhase.stratified(0, seeded()).size());
        assertEquals(1, DesyncPhase.stratified(1, seeded()).size());
        assertEquals(13, DesyncPhase.stratified(13, seeded()).size());
    }

    @Test
    public void noTwoReplaysEndUpTogether()
    {
        /* The whole point. A pair closer than a fifth of their share of the cycle would still read
         * as marching together, which is the complaint the button exists to answer. */
        for (int count = 2; count <= 32; count++)
        {
            List<Float> phases = new ArrayList<>(DesyncPhase.stratified(count, seeded()));
            float slot = 1F / count;

            phases.sort(Float::compare);

            for (int i = 1; i < phases.size(); i++)
            {
                float gap = phases.get(i) - phases.get(i - 1);

                assertTrue(gap > slot * 0.2F,
                    "two of " + count + " replays landed " + gap + " apart, inside a slot of " + slot);
            }
        }
    }

    @Test
    public void theOrderIsNotAlwaysTheSame()
    {
        /* Handed out in list order, so an unshuffled spread would always leave the first replay in
         * the list near zero and the last near the end - a pattern rather than a desync. */
        boolean differed = false;

        for (int seed = 0; seed < 20 && !differed; seed++)
        {
            List<Float> phases = DesyncPhase.stratified(8, new Random(seed));

            for (int i = 1; i < phases.size(); i++)
            {
                if (phases.get(i) < phases.get(i - 1))
                {
                    differed = true;

                    break;
                }
            }
        }

        assertTrue(differed, "stratified always returned its phases in ascending order");
    }

    @Test
    public void twoRunsDisagree()
    {
        /* Pressing the button again is the offered remedy for a spread that reads badly, so it has
         * to actually produce a different one. */
        assertNotEquals(
            DesyncPhase.stratified(6, new Random(1L)),
            DesyncPhase.stratified(6, new Random(2L))
        );
    }
}
