package mchorse.bbs_mod.ui.dashboard.textures;

import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageBarOverlayPanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import org.joml.Vector2i;

import java.util.function.BiConsumer;

public class UIResizeTextureOverlayPanel extends UIMessageBarOverlayPanel
{
    /** Whether the picture gets stretched rather than the canvas cropped — kept for the session, so a series of resizes doesn't have to switch it every time. */
    private static boolean stretch;

    public UITrackpad x;
    public UITrackpad y;
    public UIIcon stretchIcon;
    public UIButton resize;

    /** Receives the new size and whether to stretch the picture to it (otherwise the canvas is cropped). */
    private final BiConsumer<Vector2i, Boolean> callback;

    public UIResizeTextureOverlayPanel(int w, int h, BiConsumer<Vector2i, Boolean> size)
    {
        super(UIKeys.TEXTURES_RESIZE_TITLE, UIKeys.TEXTURES_RESIZE_DESCRIPTION);

        this.callback = size;

        this.x = new UITrackpad();
        this.x.limit(1, 4096, true).setValue(w);
        this.x.tooltip(UIKeys.SNOWSTORM_APPEARANCE_WIDTH);
        this.y = new UITrackpad();
        this.y.limit(1, 4096, true).setValue(h);
        this.y.tooltip(UIKeys.SNOWSTORM_APPEARANCE_HEIGHT);
        this.stretchIcon = new UIIcon(Icons.SCALE, (b) -> stretch = !stretch);
        this.stretchIcon.highlight(() -> stretch, Direction.BOTTOM);
        this.stretchIcon.tooltip(UIKeys.TEXTURES_RESIZE_STRETCH);
        /* The icon's own size, centred against the 20 px fields beside it */
        this.stretchIcon.wh(16, 16).margin(0, 2);
        this.resize = new UIButton(UIKeys.TEXTURES_RESIZE, (b) -> this.confirm());
        this.resize.w(100);

        this.bar.remove(this.confirm);
        this.bar.add(this.x, this.y, this.stretchIcon, this.resize);
    }

    @Override
    public void confirm()
    {
        if (this.callback != null)
        {
            this.callback.accept(new Vector2i((int) this.x.getValue(), (int) this.y.getValue()), stretch);
        }

        super.confirm();
    }
}
