package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.cubic.shake.ShakeControl;
import mchorse.bbs_mod.cubic.shake.ShakeControls;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UITrackValue;
import mchorse.bbs_mod.ui.utils.UIShakeControlFields;

import java.util.ArrayList;
import java.util.List;

/**
 * Editor for the {@code shake} track: shaking bones are keyed by their own name, and a keyframe
 * holds every number of the bone's shake.
 */
public class UIShakeKeyframeFactory extends UIChainKeyframeFactory<ShakeControl, ShakeControls>
{
    public UIShakeControlFields fields;

    public UIShakeKeyframeFactory(UITrackValue<ShakeControls> track, UIKeyframes editor)
    {
        super(track, editor);

        this.fields = new UIShakeControlFields(this::edit);

        for (UIElement field : this.fields.all())
        {
            this.input(field);
        }

        List<UIElement> rows = new ArrayList<>(List.of(this.fields.settings()));

        rows.addAll(List.of(this.fields.amplitudes()));
        this.setup(rows.toArray(new UIElement[0]));
    }

    @Override
    protected boolean hasChain(FormBone bone)
    {
        return bone.hasShake();
    }

    @Override
    protected void sync(ShakeControl control)
    {
        this.fields.fill(control);
    }

    @Override
    protected ShakeControl configControl(String bone)
    {
        ShakeControl control = this.form == null ? null : this.form.shake.getOriginalValue().controls.get(bone);

        return control == null ? new ShakeControl() : control.copy();
    }
}
