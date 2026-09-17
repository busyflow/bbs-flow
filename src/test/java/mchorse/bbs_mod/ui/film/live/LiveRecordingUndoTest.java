package mchorse.bbs_mod.ui.film.live;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.ui.film.utils.undo.LiveRecordingUndo;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The undo half of live recording, which is where the exact behaviour was asked for: one ctrl+Z
 * takes the whole take back, the first recorded keyframe included, and anything that was on the
 * track beforehand stays.
 *
 * <p>Needs no game running - a keyframe channel is plain data. What this cannot speak for is
 * whether the take feels smooth while it is being performed.</p>
 */
public class LiveRecordingUndoTest
{
    /* The registry is filled by an explicit call now, not a static initialiser, and the undo
     * resolves a channel's factory through it. */
    @BeforeAll
    public static void fillInTheFactories()
    {
        KeyframeFactories.setup();
    }

    /** A film-shaped context: the undo resolves its channel by path from the root group. */
    private static ValueGroup filmWith(KeyframeChannel channel)
    {
        ValueGroup root = new ValueGroup("");

        root.add(channel);

        return root;
    }

    private static KeyframeChannel<Double> channel()
    {
        return new KeyframeChannel<>("y", KeyframeFactories.DOUBLE);
    }

    /** Perform a take from tick 10 to 10+count, as the recorder does one tick at a time. */
    private static void record(KeyframeChannel<Double> channel, int from, int count)
    {
        for (int i = 0; i < count; i++)
        {
            channel.insert(from + i, i * 0.5D);
        }
    }

    @Test
    public void takeOnAnEmptyTrackLeavesNothingBehind()
    {
        KeyframeChannel<Double> channel = channel();
        ValueGroup film = filmWith(channel);

        BaseType before = channel.toData();

        record(channel, 10, 25);

        assertEquals(25, channel.getKeyframes().size(), "the take should have written a keyframe a tick");

        LiveRecordingUndo undo = new LiveRecordingUndo(channel.getPath(), before, channel.toData(), 10, 35);

        undo.undo(film);

        /* "This is no keyframes, and I begin live recording. Remove all those keyframes." - the
         * first one recorded is not special and does not survive. */
        assertTrue(channel.isEmpty(), "undo left keyframes behind on a track that had none");
    }

    @Test
    public void takeStartedFromAnExistingKeyframeKeepsThatKeyframe()
    {
        KeyframeChannel<Double> channel = channel();
        ValueGroup film = filmWith(channel);

        channel.insert(10, 4.2D);

        BaseType before = channel.toData();

        record(channel, 11, 20);

        assertEquals(21, channel.getKeyframes().size());

        LiveRecordingUndo undo = new LiveRecordingUndo(channel.getPath(), before, channel.toData(), 11, 31);

        undo.undo(film);

        assertEquals(1, channel.getKeyframes().size(), "only the pre-existing keyframe should remain");
        assertEquals(10D, channel.getKeyframes().get(0).getTick(), 0.0001D);
        assertEquals(4.2D, channel.getKeyframes().get(0).getValue(), 0.0001D);
    }

    @Test
    public void aTakeThatOverwroteAnExistingKeyframeRestoresItsOldValue()
    {
        KeyframeChannel<Double> channel = channel();
        ValueGroup film = filmWith(channel);

        channel.insert(12, 99D);

        BaseType before = channel.toData();

        /* The take runs straight over tick 12, replacing what was there. */
        record(channel, 10, 6);

        assertEquals(6, channel.getKeyframes().size(), "the overwritten tick should not have been duplicated");

        new LiveRecordingUndo(channel.getPath(), before, channel.toData(), 10, 16).undo(film);

        assertEquals(1, channel.getKeyframes().size());
        assertEquals(99D, channel.getKeyframes().get(0).getValue(), 0.0001D,
            "the keyframe the take wrote over came back with the wrong value");
    }

    @Test
    public void redoPutsTheWholeTakeBack()
    {
        KeyframeChannel<Double> channel = channel();
        ValueGroup film = filmWith(channel);

        BaseType before = channel.toData();

        record(channel, 10, 12);

        LiveRecordingUndo undo = new LiveRecordingUndo(channel.getPath(), before, channel.toData(), 10, 22);

        undo.undo(film);
        assertTrue(channel.isEmpty());

        undo.redo(film);
        assertEquals(12, channel.getKeyframes().size(), "redo did not restore the take");
    }

    @Test
    public void theCursorReturnsToWhereTheTakeBegan()
    {
        KeyframeChannel<Double> channel = channel();

        LiveRecordingUndo undo = new LiveRecordingUndo(channel.getPath(), channel.toData(), channel.toData(), 7, 40);

        /* The handler reads these two to move the playhead; undoing has to land on the tick the
         * take was started from, not on the end of a performance that no longer exists. */
        assertEquals(7, undo.cursorBefore);
        assertEquals(40, undo.cursorAfter);
    }

    @Test
    public void takesAreNeverMergedIntoOneAnother()
    {
        KeyframeChannel<Double> channel = channel();
        BaseType data = channel.toData();

        LiveRecordingUndo first = new LiveRecordingUndo(channel.getPath(), data, data, 0, 10);
        LiveRecordingUndo second = new LiveRecordingUndo(channel.getPath(), data, data, 10, 20);

        /* Two takes back to back are two things to undo. Merging them would make the first
         * unreachable, so one ctrl+Z would throw away both performances. */
        assertEquals(false, first.isMergeable(second));
    }
}
