package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** A value editor's binding. Reading never creates a key or exposes stored mutable data. */
public class UITrackValue<T>
{
    public final UIKeyframeSheet sheet;
    public final UIKeyframes editor;

    public UITrackValue(UIKeyframeSheet sheet, UIKeyframes editor)
    {
        this.sheet = sheet;
        this.editor = editor;
    }

    public IKeyframeFactory<T> getFactory()
    {
        return this.sheet.channel.getFactory();
    }

    /**
     * The selected key's value, which an edit is measured against. Auto-keyframing writes at the
     * playhead, so then the fields show what the track reads there instead.
     */
    public T getValue()
    {
        if (this.editor.getAutoKeyframeTick() != null)
        {
            return (T) this.sheet.sample(this.editor.getTick());
        }

        Keyframe<T> key = this.getKeyframe();

        return key != null ? this.getFactory().copy(key.getValue()) : this.getFactory().createEmpty();
    }

    /** The tick {@link #getValue} reads at: the selected key's, or the playhead's. */
    public float getValueTick()
    {
        Keyframe<T> key = this.editor.getAutoKeyframeTick() == null ? this.getKeyframe() : null;

        return key != null ? key.getTick() : this.editor.getTick();
    }

    /**
     * The picked key when it is on this track, otherwise the track's first selected key. With
     * nothing selected here the panel keeps showing the key it was opened for, never the track
     * at the playhead.
     */
    private Keyframe<T> getKeyframe()
    {
        Keyframe selected = this.editor.getSelectedKeyframe();

        if (selected != null && selected.getParent() == this.sheet.channel) return selected;

        Keyframe<T> first = (Keyframe<T>) this.sheet.selection.getFirst();
        if (first != null) return first;

        Keyframe picked = this.editor.getPickedKeyframe();

        return picked != null && picked.getParent() == this.sheet.channel ? picked : null;
    }

    /** Every track holding this kind of value that has selected keys, this one included. */
    private List<UIKeyframeSheet> targets()
    {
        List<UIKeyframeSheet> targets = new ArrayList<>();

        for (UIKeyframeSheet candidate : this.editor.getOperationSheets())
        {
            if (candidate.channel.getFactory() == this.getFactory() && candidate.selection.hasAny())
            {
                targets.add(candidate);
            }
        }

        if (!targets.contains(this.sheet) && (this.sheet.selection.hasAny() || this.editor.getAutoKeyframeTick() != null))
        {
            targets.add(this.sheet);
        }

        return targets;
    }

    public void edit(Consumer<T> edit)
    {
        this.write(edit, null, null);
    }

    public void setValue(T value)
    {
        this.setValue(value, this.getValue());
    }

    /** Numeric widgets supply their displayed starting value for the existing relative edit. */
    public void setValue(T value, T before)
    {
        this.write(null, value, before);
    }

    private void write(Consumer<T> edit, T value, T before)
    {
        List<UIKeyframeSheet> targets = this.targets();
        if (targets.isEmpty()) return;
        /* Auto-keyframing lands the edit on each track's key at the playhead. */
        boolean cursor = this.editor.getAutoKeyframeTick() != null;

        for (UIKeyframeSheet target : targets)
        {
            this.editor.applyValueChange(target, () ->
            {
                List<Keyframe<T>> keys = cursor ? List.of(target.ensureKeyframe(this.editor.getTick()))
                    : (List) target.selection.getSelected();

                for (Keyframe<T> key : keys)
                {
                    if (edit != null) edit.accept(key.getValue());
                    else if (cursor && target == this.sheet) key.setValue(this.getFactory().copy(value), false);
                    else target.setValueOn(key, value, before, false);
                }
            });
        }

        this.editor.triggerChange();
    }
}
