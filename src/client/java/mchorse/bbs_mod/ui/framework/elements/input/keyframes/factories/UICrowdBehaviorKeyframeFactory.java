package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.actions.crowd.CrowdBehavior;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.function.Consumer;

/** Which behaviour the crowd runs from this keyframe on, and how fast / jumpy. */
public class UICrowdBehaviorKeyframeFactory extends UIKeyframeFactory<CrowdBehavior>
{
    private final UIButton mode;
    private final UITrackpad speed;
    private final UITrackpad jumpRate;

    public UICrowdBehaviorKeyframeFactory(Keyframe<CrowdBehavior> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        if (keyframe.getValue() == null)
        {
            keyframe.setValue(new CrowdBehavior());
        }

        this.mode = new UIButton(IKey.EMPTY, (b) -> this.openModeMenu());
        this.mode.tooltip(IKey.constant("What the crowd does from here until the next behaviour keyframe.\n\nStand still, Walk (Motion Path), Wander (normal mob roaming), Run around, or Freak out (sprint and jump off in new directions)."));

        this.speed = new UITrackpad((value) -> this.edit((behavior) -> behavior.speed = value.floatValue()));
        this.speed.limit(0D, 20D).increment(0.1D).values(0.1D, 0.05D, 1D);
        this.speed.tooltip(IKey.constant("Blocks per second. 0 uses the mode's own default (walking ~4.3, sprinting ~5.6)."));

        this.jumpRate = new UITrackpad((value) -> this.edit((behavior) -> behavior.jumpRate = value.floatValue()));
        this.jumpRate.limit(0D, 5D).increment(0.1D).values(0.1D, 0.05D, 0.5D);
        this.jumpRate.tooltip(IKey.constant("Jumps per member per second while moving. 0 is never. Freak out jumps on its own if left at 0."));

        UIElement content = UI.column(
            UI.label(IKey.constant("Crowd behaviour")),
            UI.labelRow(IKey.constant("Mode"), this.mode).marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(IKey.constant("Speed"), this.speed),
            UI.labelRow(IKey.constant("Jump rate"), this.jumpRate)
        );

        this.scroll.add(content);
        this.display();
    }

    private void openModeMenu()
    {
        this.getContext().replaceContextMenu((menu) ->
        {
            for (CrowdBehavior.Kind kind : CrowdBehavior.Kind.values())
            {
                menu.action(Icons.SHAPES, IKey.constant(kind.title), () ->
                {
                    this.edit((behavior) -> behavior.kind = kind.ordinal());
                    this.display();
                });
            }
        });
    }

    private void edit(Consumer<CrowdBehavior> consumer)
    {
        CrowdBehavior behavior = this.keyframe.getValue();

        if (behavior == null)
        {
            behavior = new CrowdBehavior();

            this.keyframe.setValue(behavior);
        }

        this.keyframe.preNotify();
        consumer.accept(behavior);
        this.keyframe.postNotify();
    }

    private void display()
    {
        CrowdBehavior behavior = this.keyframe.getValue();

        if (behavior == null)
        {
            return;
        }

        this.mode.label = IKey.constant(behavior.getKind().title);
        this.speed.setValue(behavior.speed);
        this.jumpRate.setValue(behavior.jumpRate);
    }
}
