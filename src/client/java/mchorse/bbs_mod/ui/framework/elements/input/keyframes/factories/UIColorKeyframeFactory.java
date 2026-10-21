package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UITrackValue;

public class UIColorKeyframeFactory extends UIKeyframeFactory<Color>
{
    private UIColor color;

    public UIColorKeyframeFactory(UITrackValue<Color> track, UIKeyframes editor)
    {
        super(track, editor);

        this.color = new UIColor((c) -> this.setValue(Color.rgba(c)));
        this.color.setColor(track.getValue().getARGBColor());
        this.color.withAlpha();

        this.scroll.add(this.color);
    }
    @Override
    public void update()
    {
        this.color.setColor(this.getDisplayValue().getARGBColor());
    }

}