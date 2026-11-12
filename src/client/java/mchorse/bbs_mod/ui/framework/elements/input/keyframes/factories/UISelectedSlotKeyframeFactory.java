package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.film.replays.Hotbar;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIItemHotbar;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UITrackValue;
import mchorse.bbs_mod.ui.utils.UIUtils;

/**
 * A selected slot keyframe factory laid out as the hotbar: nine cells, with the currently selected
 * slot framed. Clicking on any slot sets the selected slot keyframe value to that slot index (0..8).
 */
public class UISelectedSlotKeyframeFactory extends UIKeyframeFactory<Integer>
{
    private final UIItemHotbar slots;
    private Hotbar hotbar = new Hotbar();
    private int selected = 0;

    public UISelectedSlotKeyframeFactory(UITrackValue<Integer> track, UIKeyframes editor)
    {
        super(track, editor, false);

        this.slots = new UIItemHotbar((slot) -> this.hotbar.get(slot), () -> this.selected, this::selectSlot);
        this.slots.fillWidth();

        this.scroll.add(this.slots);
        this.update();
    }

    private void selectSlot(int slot)
    {
        this.selected = slot;
        this.setValue(slot);
        UIUtils.playClick();
    }

    @Override
    public void update()
    {
        Integer val = this.getDisplayValue();
        this.selected = val != null ? val : 0;

        float tick = this.track.getValueTick();
        if (this.track.sheet.channel.getParent() instanceof ReplayKeyframes keyframes)
        {
            this.hotbar = keyframes.getHotbar(tick);
        }
        else
        {
            this.hotbar = new Hotbar();
        }
    }
}
