package mchorse.bbs_mod.utils.keyframes;

import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.interps.Interpolations;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Duplicating a scene from the playhead: tick 30 becomes tick 0 and the take is wound back, rather
 * than being flattened to whatever pose the playhead was sitting on.
 */
public class KeyframeShiftTest
{
    private static KeyframeChannel<Double> channel(double... ticksAndValues)
    {
        KeyframeChannel<Double> channel = new KeyframeChannel<>("y", KeyframeFactories.DOUBLE);

        for (int i = 0; i < ticksAndValues.length; i += 2)
        {
            channel.insert((float) ticksAndValues[i], ticksAndValues[i + 1]);
        }

        return channel;
    }

    private static KeyframeChannel<Double> empty()
    {
        return new KeyframeChannel<>("y", KeyframeFactories.DOUBLE);
    }

    @Test
    public void everythingAfterTheCutKeepsItsSpacing()
    {
        KeyframeChannel<Double> from = channel(30, 1D, 50, 2D, 90, 3D);
        KeyframeChannel<Double> into = empty();

        KeyframeShift.windBack(into, from, 30);

        List<Keyframe<Double>> out = into.getKeyframes();

        assertEquals(3, out.size(), "a keyframe went missing across the cut");
        assertEquals(0F, out.get(0).getTick(), 0.0001F);
        assertEquals(20F, out.get(1).getTick(), 0.0001F, "50 should land 20 ticks after the cut");
        assertEquals(60F, out.get(2).getTick(), 0.0001F, "90 should land 60 ticks after the cut");
    }

    @Test
    public void theSceneOpensOnThePoseThatWasUnderThePlayhead()
    {
        /* Halfway between 0 and 100 in value as well as in time. */
        KeyframeChannel<Double> from = channel(0, 0D, 100, 100D);
        KeyframeChannel<Double> into = empty();

        KeyframeShift.windBack(into, from, 40);

        assertEquals(40D, into.getKeyframes().get(0).getValue(), 0.5D,
            "tick 0 should hold the value the take actually had at the cut");
    }

    @Test
    public void keyframesBeforeTheCutAreCollapsedRatherThanCarriedNegative()
    {
        KeyframeChannel<Double> from = channel(0, 5D, 10, 6D, 20, 7D, 60, 9D);
        KeyframeChannel<Double> into = empty();

        KeyframeShift.windBack(into, from, 30);

        for (Keyframe<Double> keyframe : into.getKeyframes())
        {
            assertTrue(keyframe.getTick() >= 0F, "a keyframe was carried to a negative tick");
        }

        /* The three before the cut become the one at zero; the one after it survives. */
        assertEquals(2, into.getKeyframes().size());
        assertEquals(30F, into.getKeyframes().get(1).getTick(), 0.0001F);
    }

    @Test
    public void nothingAfterTheCutIsDroppedForBeingEarly()
    {
        KeyframeChannel<Double> from = channel(0, 0D, 5, 1D, 10, 2D, 15, 3D, 20, 4D);
        KeyframeChannel<Double> into = empty();

        KeyframeShift.windBack(into, from, 10);

        /* Cut at 10: the keyframes at 10, 15 and 20 all survive, at 0, 5 and 10. */
        assertEquals(3, into.getKeyframes().size(), "keyframes after the cut were trimmed off");
        assertEquals(4D, into.getKeyframes().get(2).getValue(), 0.0001D);
    }

    @Test
    public void handEasingSurvivesTheWindBack()
    {
        KeyframeChannel<Double> from = channel(30, 1D, 60, 2D);

        from.getKeyframes().get(1).getInterpolation().setInterp(Interpolations.SINE_INOUT);

        KeyframeChannel<Double> into = empty();

        KeyframeShift.windBack(into, from, 30);

        /* Re-inserting values alone would hand every keyframe the default easing, and a hand-eased
         * take would come back linear - the same poses, a different performance. */
        assertTrue(into.getKeyframes().get(1).getInterpolation().has(Interpolations.SINE_INOUT),
            "the eased keyframe came back with the default interpolation");
    }

    @Test
    public void cuttingAtZeroIsACleanCopy()
    {
        KeyframeChannel<Double> from = channel(0, 1D, 25, 2D, 80, 3D);
        KeyframeChannel<Double> into = empty();

        KeyframeShift.windBack(into, from, 0);

        assertEquals(3, into.getKeyframes().size());
        assertEquals(0F, into.getKeyframes().get(0).getTick(), 0.0001F);
        assertEquals(25F, into.getKeyframes().get(1).getTick(), 0.0001F);
        assertEquals(80F, into.getKeyframes().get(2).getTick(), 0.0001F);
    }

    @Test
    public void anEmptyChannelStaysEmpty()
    {
        KeyframeChannel<Double> into = empty();

        KeyframeShift.windBack(into, empty(), 30);

        assertTrue(into.isEmpty(), "an empty track should not gain a keyframe from a duplicate");
    }
}
