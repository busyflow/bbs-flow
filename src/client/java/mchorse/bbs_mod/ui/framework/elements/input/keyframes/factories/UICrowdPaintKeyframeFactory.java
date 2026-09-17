package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.actions.crowd.CrowdPaint;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.function.Consumer;

/**
 * Keyframe editor for a painted crowd formation.
 */
public class UICrowdPaintKeyframeFactory extends UIKeyframeFactory<CrowdPaint>
{
    private final UILabel blockCount;
    private final UITrackpad stagger;
    private final UITrackpad spread;
    private final UIToggle terrain;
    private final UIToggle run;
    private final UIButton clear;

    public UICrowdPaintKeyframeFactory(Keyframe<CrowdPaint> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        if (keyframe.getValue() == null)
        {
            keyframe.setValue(new CrowdPaint());
        }

        this.blockCount = UI.label(IKey.constant("0 blocks painted"));
        this.stagger = new UITrackpad((value) -> this.edit((p) -> p.stagger = value.floatValue()));
        this.stagger.limit(0D, 1D).increment(0.05D).values(0.05D, 0.01D, 0.2D);
        this.stagger.tooltip(IKey.constant("How ragged the crowd is about moving into this formation."));

        this.spread = new UITrackpad((value) -> this.edit((p) -> p.spread = value.floatValue()));
        this.spread.limit(0D, 1D).increment(0.05D).values(0.05D, 0.01D, 0.2D);
        this.spread.tooltip(IKey.constant("How much the formation loosens or breathes while traveling."));

        this.terrain = new UIToggle(IKey.constant("Follow terrain"), (b) -> this.edit((p) -> p.terrainFollow = b.getValue()));
        this.run = new UIToggle(IKey.constant("Run"), (b) -> this.edit((p) -> p.run = b.getValue()));

        this.clear = new UIButton(IKey.constant("Clear painted blocks"), (b) ->
        {
            this.edit((p) -> p.getCells().clear());
        });

        UIElement content = UI.column(
            UI.label(IKey.constant("Crowd Painted Formation")),
            this.blockCount.marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(IKey.constant("Stagger"), this.stagger).marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(IKey.constant("Spread"), this.spread),
            UI.row(1, this.terrain, this.run).marginTop(UIConstants.SECTION_GAP),
            this.clear.marginTop(UIConstants.SECTION_GAP)
        );

        this.scroll.add(content);
        this.display();
    }

    private void edit(Consumer<CrowdPaint> consumer)
    {
        CrowdPaint paint = this.keyframe.getValue();

        if (paint == null)
        {
            paint = new CrowdPaint();
            this.keyframe.setValue(paint);
        }

        this.keyframe.preNotify();
        consumer.accept(paint);
        this.keyframe.postNotify();

        this.display();
    }

    private void display()
    {
        CrowdPaint paint = this.keyframe.getValue();

        if (paint != null)
        {
            this.blockCount.label = IKey.constant(paint.size() + " blocks painted");
            this.stagger.setValue(paint.stagger);
            this.spread.setValue(paint.spread);
            this.terrain.setValue(paint.terrainFollow);
            this.run.setValue(paint.run);
        }
    }
}
