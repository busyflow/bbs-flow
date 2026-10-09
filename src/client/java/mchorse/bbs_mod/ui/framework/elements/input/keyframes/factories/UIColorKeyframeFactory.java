package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.ui.framework.elements.input.color.UIColorPicker;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UITrackValue;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;

public class UIColorKeyframeFactory extends UIKeyframeFactory<Color>
{
    private UIColorPicker color;

    public UIColorKeyframeFactory(UITrackValue<Color> track, UIKeyframes editor)
    {
        super(track, editor);

        this.color = new UIColorPicker((c) -> this.setValue(Color.rgba(c))).embedded().editAlpha();
        this.color.setColor(track.getValue().getARGBColor());
        this.color.w(160);

        UIElement content = UI.column(
            UIConstants.MARGIN,
            UI.label(IKey.constant("Keyframe Color")),
            this.color
        );

        this.scroll.add(content);
    }
    @Override
    public void update()
    {
        this.color.setColor(this.getDisplayValue().getARGBColor());
    }
}
