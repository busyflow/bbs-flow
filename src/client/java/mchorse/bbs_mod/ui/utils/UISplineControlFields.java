package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.cubic.spline.SplineControl;
import mchorse.bbs_mod.ui.forms.editors.panels.rig.UIRigSplineSection;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import java.util.function.Consumer;

public class UISplineControlFields
{
    public final UISliderTrackpad influence;
    public final UITrackpad progress;
    public final UITrackpad twist;

    public UISplineControlFields(Consumer<Consumer<SplineControl>> edit)
    {
        this.influence = new UISliderTrackpad(v -> edit.accept(c -> c.influence = v.floatValue()));
        this.influence.normalized();
        this.progress = new UITrackpad(v -> edit.accept(c -> c.progress = v.floatValue()));
        this.progress.tooltip(UIRigSplineSection.key("progress_tooltip"));
        this.twist = new UITrackpad(v -> edit.accept(c -> c.twist = v.floatValue()));
    }
}
