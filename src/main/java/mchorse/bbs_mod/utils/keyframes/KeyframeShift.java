package mchorse.bbs_mod.utils.keyframes;

import java.util.List;

/**
 * Winding a take back so a chosen tick becomes zero.
 *
 * <p>Lives here rather than beside the film panel that asks for it: it is arithmetic on keyframes
 * and touches no UI, so it can be tested without one.</p>
 */
public class KeyframeShift
{
    /**
     * Copy {@code from} into {@code into} with {@code tick} as the new zero - the take wound back
     * like a typewriter carriage rather than resampled.
     *
     * <p>Everything from {@code tick} onwards survives at its original spacing, so the second half
     * of a performance lifts into its own scene unchanged rather than being flattened to the pose
     * it happened to be in.</p>
     *
     * <p>Nothing is dropped for being early. What came before {@code tick} is collapsed into the
     * keyframe at zero, which carries the interpolated value at the cut - the pose the take now
     * opens on, exactly as it looked a moment before. Keeping those keyframes at their own negative
     * ticks is the alternative, and a channel that starts before zero is not a thing the rest of
     * the editor knows how to read.</p>
     *
     * <p>Interpolation rides along per keyframe. Re-inserting values alone hands every one of them
     * the default easing, so a hand-eased take comes back linear and reads as a different
     * performance.</p>
     */
    public static void windBack(KeyframeChannel into, KeyframeChannel from, int tick)
    {
        if (into == null || from == null || from.isEmpty())
        {
            return;
        }

        into.insert(0, from.interpolate(tick));

        List<Keyframe> keyframes = from.getKeyframes();

        for (Keyframe keyframe : keyframes)
        {
            if (keyframe.getTick() < tick)
            {
                continue;
            }

            int index = into.insert(keyframe.getTick() - tick, from.getFactory().copy(keyframe.getValue()));
            List<Keyframe> inserted = into.getKeyframes();

            if (index >= 0 && index < inserted.size())
            {
                inserted.get(index).copyOverExtra(keyframe);
            }
        }
    }
}
