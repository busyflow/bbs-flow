package mchorse.bbs_mod.ui.film.utils;

import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.ui.film.utils.undo.LiveRecordingUndo;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.utils.undo.IUndo;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.forms.editors.UIFormUndoHandler;
import mchorse.bbs_mod.utils.Timer;

import java.util.HashSet;
import java.util.Set;

public class UIFilmUndoHandler extends UIFormUndoHandler
{
    private Timer actionsTimer = new Timer(100);
    private Set<BaseValue> syncData = new HashSet<>();

    public UIFilmUndoHandler(UIFilmPanel panel)
    {
        super(panel);
    }

    /**
     * Put the playhead back where a live take was started from.
     *
     * <p>Undoing a take removes its keyframes wherever the cursor happens to be, which leaves the
     * playhead parked at the end of a performance that no longer exists. Moving it back is what
     * makes ctrl+Z mean "as though I never recorded that" rather than only "delete those".</p>
     */
    @Override
    protected void handleUndos(IUndo<ValueGroup> undo, boolean redo)
    {
        super.handleUndos(undo, redo);

        if (undo instanceof LiveRecordingUndo take)
        {
            ((UIFilmPanel) this.uiElement).setCursor(redo ? take.cursorAfter : take.cursorBefore);
        }
    }

    @Override
    public void handlePreValues(BaseValue baseValue, int flag)
    {
        /* time_spent_active is a passive counter updated every second; it should not
         * pollute undo history with dozens of entries per minute */
        if (baseValue.getPath().getLast().equals("time_spent_active"))
        {
            return;
        }

        /* Opening and closing a category of the replay list is a way of looking at the film, not a
         * change to it; it is saved with the film all the same, but Ctrl+Z has nothing to say to it. */
        if (baseValue.getPath().getLast().equals("expanded") && baseValue.getPath().strings.contains("replay_categories"))
        {
            return;
        }

        super.handlePreValues(baseValue, flag);
    }

    @Override
    protected void handleValue(BaseValue value)
    {
        super.handleValue(value);

        if (this.isCrowd(value))
        {
            /* Sync the whole crowds group rather than the field that changed. The server spawns
             * from its own copy of the film, and an edit that adds or removes a crowd is a
             * change of shape, not of one value - a path like "crowds/1/count" cannot be
             * resolved against a server that has never heard of crowd 1. Sending the group
             * carries the structure with it.
             *
             * Crowds are why this had to be said at all: spawning used to be an action clip, so
             * it reached the server through the clips branch below, and moving crowds onto the
             * film quietly took them off every path this method recognises. The crowd was
             * therefore only ever edited client-side, and the server had none to spawn. */
            Film film = ((UIFilmPanel) this.uiElement).getData();

            if (film != null)
            {
                this.syncData.add(film.crowds);
                this.actionsTimer.mark();
            }
        }

        BaseValue crowdChannel = this.crowdChannel(value);

        if (crowdChannel != null)
        {
            /* The whole channel, not the keyframe that changed. An indexed path like
             * "keyframes/crowd_motion_path/2" cannot be resolved against a server whose copy of
             * the channel has two keyframes in it, which is exactly the case the moment a third
             * waypoint is added - so the third one, and every one after it, never arrived and the
             * crowd walked the route it knew about. */
            this.syncData.add(crowdChannel);
            this.actionsTimer.mark();
        }
        /* The value itself declares whether the server's copy needs it (Film marks the replays
         * subtree) — this used to be a hand-written list of path endings that kept falling
         * behind the data model, silently keeping new channels off the server. */
        if (value.isSynced())
        {
            /* TODO: Variant A for the lazy-channel desync — if 'value' is a keyframe
             * inside a channel, promote it to its parent KeyframeChannel here so the
             * sync sends the whole channel ('properties/<key>') instead of an indexed
             * keyframe path ('properties/<key>/0'). A channel created client-side by
             * FormProperties.getOrCreate during UI building (UIReplaysEditor
             * .collectFormPropertySheets) is never synced, so the server lacks it and
             * the indexed path can't be resolved. Currently handled reactively by the
             * full-film resync request (ServerNetwork.requestFilmResync). */
            this.syncData.add(value);
            this.actionsTimer.mark();
        }
    }

    @Override
    protected void handleTimers()
    {
        super.handleTimers();

        if (this.actionsTimer.checkReset())
        {
            for (BaseValue syncData : this.syncData)
            {
                ClientNetwork.sendSyncData(((UIFilmPanel) this.uiElement).getData().getId(), syncData);
            }

            this.syncData.clear();
        }
    }

    /**
     * The crowd keyframe channel an edit belongs to, or null when it belongs to none.
     *
     * <p>Climbs to whichever value sits directly under a replay's keyframes, since that is the
     * channel, and an edit is usually reported against a keyframe well inside one.</p>
     */
    private BaseValue crowdChannel(BaseValue value)
    {
        BaseValue current = value;

        while (current != null)
        {
            BaseValue parent = current.getParent();

            if (parent != null && "keyframes".equals(parent.getId()))
            {
                return current.getId() != null && current.getId().startsWith("crowd_") ? current : null;
            }

            current = parent;
        }

        return null;
    }

    /** Anything under the film's crowds, including the group itself. */
    private boolean isCrowd(BaseValue value)
    {
        String path = value.getPath().toString();

        return path.equals("crowds") || path.startsWith("crowds/") || path.contains("/crowds/") || path.endsWith("/crowds");
    }
}