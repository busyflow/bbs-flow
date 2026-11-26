package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UITrackValue;
import mchorse.bbs_mod.ui.utils.UI;

public class UIBooleanKeyframeFactory extends UIKeyframeFactory<Boolean>
{
    private UIToggle toggle;
    private ValueFloat snowballSize;
    private UISliderTrackpad sizeSlider;

    public UIBooleanKeyframeFactory(UITrackValue<Boolean> track, UIKeyframes editor)
    {
        super(track, editor);

        this.toggle = new UIToggle(UIKeys.GENERIC_KEYFRAMES_BOOLEAN_TRUE, (b) -> this.setValue(b.getValue()));
        this.toggle.setValue(track.getValue());

        this.keys().register(Keys.TRANSFORMATIONS_TRANSLATE, () ->
        {
            this.setValue(!this.getDisplayValue());
            this.update();
        }).category(UIKeys.TRANSFORMS_KEYS_CATEGORY);

        this.scroll.add(this.toggle);

        BaseValue prop = track.sheet == null ? null : track.sheet.property;

        if (prop != null && prop.getParent() instanceof ModelForm modelForm && modelForm.isSnowballModel())
        {
            if (prop == modelForm.snowTrail)
            {
                this.snowballSize = modelForm.snowTrailSize;
            }
            else if (prop == modelForm.snowballImpact)
            {
                this.snowballSize = modelForm.snowballImpactSize;
            }
        }

        if (this.snowballSize != null)
        {
            this.sizeSlider = new UISliderTrackpad((v) ->
            {
                this.snowballSize.set(v.floatValue());
                this.editor.triggerChange();
            });
            this.sizeSlider.limit(0.1D, 5D).values(0.1F, 0.05F, 0.25F).increment(0.1F);
            this.sizeSlider.setValue(this.snowballSize.get());

            this.scroll.add(UI.labelRow(IKey.constant("Size"), this.sizeSlider));
        }
    }

    @Override
    public void update()
    {
        this.toggle.setValue(this.getDisplayValue());

        if (this.sizeSlider != null && this.snowballSize != null && !this.sizeSlider.isUserEditing())
        {
            this.sizeSlider.setValue(this.snowballSize.get());
        }
    }

}
