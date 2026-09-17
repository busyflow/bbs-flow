package mchorse.bbs_mod.ui.film.utils.undo;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.undo.IUndo;

/**
 * One live-recorded take, undone whole.
 *
 * <p>A take lays a keyframe on every tick it was playing for, so undoing it one keyframe at a time
 * would mean pressing ctrl+Z once per tick. What is stored instead is the state of the entire
 * channel either side of the take: undoing writes the whole channel back as it was, which removes
 * every keyframe the take made - the first one included - and leaves untouched anything that was
 * already there. There is nothing to walk and nothing to count, so it cannot half-undo.</p>
 *
 * <p>{@link #cursorBefore} rides along so the playhead returns to where the take was started from,
 * which is the only way ctrl+Z leaves the editor in a state you can immediately record from again.
 * The cursor is applied by {@code UIFilmUndoHandler}, not here, since this class has no view.</p>
 *
 * <p>Never mergeable. Two takes on one channel are two separate things to undo even when the second
 * starts the instant the first ends - merging them would make the earlier one unreachable.</p>
 */
public class LiveRecordingUndo extends FilmEditorUndo
{
    private final DataPath channel;
    private final BaseType before;
    private final BaseType after;

    /** Film tick the playhead sat on when the take began, restored on undo. */
    public final int cursorBefore;

    /** Film tick the playhead reached when the take ended, restored on redo. */
    public final int cursorAfter;

    private boolean invalid;

    public LiveRecordingUndo(DataPath channel, BaseType before, BaseType after, int cursorBefore, int cursorAfter)
    {
        this.channel = channel;
        this.before = before;
        this.after = after;
        this.cursorBefore = cursorBefore;
        this.cursorAfter = cursorAfter;
    }

    public DataPath getChannel()
    {
        return this.channel;
    }

    @Override
    public IUndo<ValueGroup> noMerging()
    {
        return this;
    }

    @Override
    public boolean isMergeable(IUndo<ValueGroup> undo)
    {
        return false;
    }

    @Override
    public void merge(IUndo<ValueGroup> undo)
    {}

    /**
     * The channel this take was recorded into, or null if the film no longer has it.
     *
     * <p>A replay deleted after the take was recorded takes its channels with it, and the undo
     * entry outlives them in the history. Resolving to null once marks this entry spent rather
     * than letting it throw every time the user walks back past it.</p>
     */
    private BaseValue resolve(ValueGroup context)
    {
        if (this.invalid)
        {
            return null;
        }

        BaseValue value = context.findRecursively(this.channel);

        if (value == null || !value.getPath().equals(this.channel))
        {
            this.invalid = true;

            return null;
        }

        return value;
    }

    @Override
    public void undo(ValueGroup context)
    {
        BaseValue value = this.resolve(context);

        if (value != null)
        {
            value.fromData(this.before);
        }
    }

    @Override
    public void redo(ValueGroup context)
    {
        BaseValue value = this.resolve(context);

        if (value != null)
        {
            value.fromData(this.after);
        }
    }
}
