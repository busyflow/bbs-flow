package mchorse.bbs_mod.ui.film.live;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.math.Operation;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.film.utils.undo.LiveRecordingUndo;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Records a gizmo drag onto the timeline as it happens.
 *
 * <p>Drag a pose bone or a transform while the film is playing and the pose is written to the
 * timeline at the tick it was made on, every tick, for as long as the drag lasts. That is the whole
 * feature: performing the motion once rather than posing, stepping, posing, stepping.</p>
 *
 * <h3>Why it samples rather than listens</h3>
 *
 * <p>The obvious shape is to hook the transform's change callback and write a keyframe whenever the
 * value moves. It gives the wrong curve. A drag reports changes when the <em>mouse</em> moves, so
 * holding the bone still for half a second writes nothing at all, and the timeline then interpolates
 * straight through the pause as though the motion never stopped - the one thing a performance is
 * usually trying to say. Sampling on the tick instead records what was true at that tick whether or
 * not it changed, so a held pose is held keyframes and reads back exactly as it was performed.</p>
 *
 * <h3>Why it writes at the playhead</h3>
 *
 * <p>The transform is bound to whichever keyframe the editor has selected, so a drag ordinarily
 * edits <em>that</em> keyframe wherever it sits. While recording, the value is read from the same
 * transform but written to a fresh keyframe at the playhead. The keyframe the editor is pointing at
 * therefore behaves as the pose the take starts from, which is what makes starting a take on an
 * existing keyframe continue from it rather than jump.</p>
 */
public class LiveKeyframeRecorder
{
    /**
     * The channel a take is writing into right now, or {@code null} between takes.
     *
     * <p>Published so the pose editor can tell a live take from a hand-authored edit: the two want
     * opposite things out of an overlay bone (see {@link #keepAdditive}). One recorder per film
     * panel, one drag at a time, so a single slot is the whole state — the same shape as the other
     * editor-wide singletons this UI already reads ({@code Gizmo.INSTANCE}, {@code AreaBrush}).</p>
     */
    private static KeyframeChannel recordingChannel;

    /** Whether a live take is writing into {@code channel} right now. */
    public static boolean isRecording(KeyframeChannel channel)
    {
        return channel != null && channel == recordingChannel;
    }

    /** Null unless a take is in progress. */
    private KeyframeChannel channel;
    private UIKeyframeFactory factory;

    /**
     * Whether this take's channel is one of a form's pose overlays ({@code pose_overlay},
     * {@code pose_overlay0}...). Those are the tracks a performance is layered onto, so their bones
     * have to stay additive — see {@link #keepAdditive}.
     */
    private boolean overlay;

    /**
     * The bones already pinned (Fix on) when the take began, so {@link #keepAdditive} un-pins only
     * what the take itself would pin and leaves an authored correction alone.
     */
    private final Set<String> pinnedBefore = new HashSet<>();

    /** The channel exactly as it was before the take, and where the playhead was. */
    private BaseType before;
    private int cursorBefore;

    /** Last tick sampled, so one tick is never sampled twice while frames outrun ticks. */
    private int lastTick;

    /**
     * The take, held back until the drag ends.
     *
     * <p>Writing each sample into the channel as it was taken fed the recorder's own output back
     * into the gesture it was recording. A drag solves as a snapshot of the transform taken when
     * the gesture began, plus a delta measured against the gizmo's origin <em>as rendered this
     * frame</em>. Writing a keyframe at the playhead moved the actor, which moved that origin,
     * so the next frame measured its delta from a moved origin against a snapshot that had not
     * moved - counting the same motion twice, again every frame. A translation ran away and a
     * trackball rotation span.</p>
     *
     * <p>Held here instead, the channel is untouched for the length of the drag, the origin stays
     * where the gesture anchored it, and the samples are exactly the motion performed.</p>
     */
    private final List<Sample> take = new ArrayList<>();

    private record Sample(int tick, Object value) {}

    public boolean isRecording()
    {
        return this.channel != null;
    }

    /**
     * Drive the recorder from the film panel's frame.
     *
     * <p>Polled rather than driven by events because a take is bounded by three separate things -
     * the drag ending, playback stopping, and the editor's selection changing out from under it -
     * and only one of them is a mouse event. Asking every frame is a handful of field reads and
     * cannot miss a boundary the way three listeners can.</p>
     */
    public void update(UIFilmPanel panel)
    {
        if (panel == null || panel.getData() == null)
        {
            this.abandon();

            return;
        }

        UIKeyframeEditor editor = panel.replayEditor == null ? null : panel.replayEditor.keyframeEditor;
        UIPropTransform transform = UIReplaysEditorUtils.getEditableTransform(editor);
        boolean live = panel.isRunning() && transform != null && transform.isEditing();

        if (!live)
        {
            this.finish(panel);

            return;
        }

        if (!this.isRecording())
        {
            this.begin(panel, editor);
        }

        /* Still null when the selected track is one whose keyframes cannot be resolved to a
         * channel — nothing to record into, so the drag stays an ordinary edit. */
        if (this.isRecording())
        {
            this.sample(panel.getCursor());
        }
    }

    private void begin(UIFilmPanel panel, UIKeyframeEditor editor)
    {
        UIKeyframeFactory factory = editor.editor;
        Keyframe keyframe = factory == null ? null : factory.getKeyframe();
        UIKeyframeSheet sheet = keyframe == null ? null : editor.getSheet(keyframe);

        if (sheet == null || sheet.channel == null)
        {
            return;
        }

        this.channel = sheet.channel;
        this.factory = factory;
        this.overlay = sheet.channel.getFactory() == KeyframeFactories.POSE && sheet.id.contains("pose_overlay");
        this.before = this.channel.toData();
        this.cursorBefore = panel.getCursor();

        this.snapshotPins();

        recordingChannel = this.channel;

        /* -1 rather than the cursor: the tick the take starts on has not been written yet, and
         * seeding with the cursor would skip it. */
        this.lastTick = -1;
    }

    /**
     * Note every bone the track already pins, before the take writes a single sample.
     *
     * <p>Walked over the whole channel rather than the keyframe the editor points at: a take is
     * seeded from what the track interpolates at the playhead, so a pin authored on ANY earlier
     * keyframe is already part of the pose being performed on, and un-pinning it would silently
     * undo somebody's hand-made correction.</p>
     */
    private void snapshotPins()
    {
        this.pinnedBefore.clear();

        if (!this.overlay)
        {
            return;
        }

        for (Object o : this.channel.getKeyframes())
        {
            if (o instanceof Keyframe keyframe && keyframe.getValue() instanceof Pose pose)
            {
                for (Map.Entry<String, PoseTransform> entry : pose.transforms.entrySet())
                {
                    if (!Operation.equals(entry.getValue().fix, 0))
                    {
                        this.pinnedBefore.add(entry.getKey());
                    }
                }
            }
        }
    }

    /**
     * Take the transform's current value for {@code tick}.
     *
     * <p>Copied through the channel's own factory rather than kept by reference: the gizmo goes on
     * mutating that same instance for the rest of the drag, so every sample in the take would end
     * up being the final pose and the whole thing would read back flat.</p>
     */
    private void sample(int tick)
    {
        if (tick == this.lastTick || this.factory == null)
        {
            return;
        }

        Keyframe keyframe = this.factory.getKeyframe();
        Object value = keyframe == null ? null : keyframe.getValue();

        if (value == null)
        {
            return;
        }

        this.take.add(new Sample(tick, this.keepAdditive(this.channel.getFactory().copy(value))));
        this.lastTick = tick;
    }

    /**
     * Un-pin every bone of a sampled overlay pose, so the take LAYERS OVER what is already there
     * instead of replacing it.
     *
     * <p>An overlay bone carries a {@code fix} weight, and the renderer reads it as a choice between
     * two compositions: at zero the overlay's transform is ADDED onto the accumulated pose, above
     * zero it is LERPED over it — and at one, which is what touching a bone in an overlay editor
     * writes, it replaces it outright. That is right for a hand-authored correction, whose whole job
     * is to hold a bone still against the walk cycle underneath.</p>
     *
     * <p>It is wrong for a take. A take is a performance given on top of whatever is already visible
     * — the form's pose, its animation, and every overlay recorded before this one — so a pinned
     * bone throws all of that away and keeps only the motion just performed. Two takes on the same
     * head, one bobbing it and one turning it, came out as the second one alone; un-pinned they sum,
     * which is what layering a performance means.</p>
     *
     * <p>Only the pins this take would otherwise carry are touched: a bone that was already pinned
     * when the take began is left exactly as authored, so a hand-made correction survives a
     * performance recorded over it.</p>
     */
    private Object keepAdditive(Object value)
    {
        if (!this.overlay || !(value instanceof Pose pose))
        {
            return value;
        }

        for (Map.Entry<String, PoseTransform> entry : pose.transforms.entrySet())
        {
            if (!this.pinnedBefore.contains(entry.getKey()))
            {
                entry.getValue().fix = 0F;
            }
        }

        return pose;
    }

    /**
     * End the take and hand it to the undo history as one entry.
     *
     * <p>A take that wrote nothing - the drag began and ended inside a single tick - is dropped
     * rather than pushed, so a stray click during playback does not put an empty step in the
     * history for the user to walk back through.</p>
     */
    private void finish(UIFilmPanel panel)
    {
        if (!this.isRecording())
        {
            return;
        }

        KeyframeChannel channel = this.channel;
        BaseType before = this.before;
        int cursorBefore = this.cursorBefore;
        List<Sample> take = new ArrayList<>(this.take);

        this.abandon();

        if (take.isEmpty())
        {
            return;
        }

        /* The whole take at once, now the gesture is over and there is no drag left to disturb. */
        for (Sample sample : take)
        {
            channel.insert(sample.tick(), sample.value());
        }

        BaseType after = channel.toData();

        if (after.equals(before))
        {
            return;
        }

        panel.getUndoHandler().getUndoManager().pushUndo(new LiveRecordingUndo(
            channel.getPath(), before, after, cursorBefore, panel.getCursor()
        ));
    }

    private void abandon()
    {
        this.channel = null;
        this.factory = null;
        this.before = null;
        this.lastTick = -1;
        this.overlay = false;
        this.pinnedBefore.clear();
        this.take.clear();

        recordingChannel = null;
    }
}
