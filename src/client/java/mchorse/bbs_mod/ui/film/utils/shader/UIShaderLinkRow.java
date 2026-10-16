package mchorse.bbs_mod.ui.film.utils.shader;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.RowStyle;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.glfw.GLFW;

/**
 * A way into a sub-screen of the shader pack's menu: a row the way every list in BBS draws one
 * ({@link RowStyle}) on a tile of its own, with an arrow on the right. Its text sits close to the
 * tile's edge, so it starts almost where the names of the other rows do. A screen with no options
 * behind it is dimmed.
 */
public class UIShaderLinkRow extends UIElement
{
    public static final int HEIGHT = 20;

    private static final int TEXT_X = 2;
    private static final int PADDING = 4;
    private static final String ARROW = "›";

    private final String label;
    private final Runnable callback;
    private final boolean muted;

    public UIShaderLinkRow(String label, boolean muted, Runnable callback)
    {
        this.label = label == null ? "" : label;
        this.muted = muted;
        this.callback = callback;

        this.h(HEIGHT);
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (context.mouseButton == 0 && this.area.isInside(context))
        {
            UIUtils.playClick();
            this.callback.run();

            return true;
        }

        return super.subMouseClicked(context);
    }

    @Override
    public void render(UIContext context)
    {
        Batcher2D batcher = context.batcher;
        FontRenderer font = batcher.getFont();
        Area a = this.area;
        boolean hover = a.isInside(context);
        int textY = a.my(font.getHeight());
        int x = a.x + TEXT_X;
        int right = a.ex() - PADDING;

        if (hover)
        {
            context.requestCursor(GLFW.GLFW_HAND_CURSOR);
        }

        a.render(batcher, BBSSettings.chromeSurface());
        RowStyle.row(batcher, a.x, a.y, a.w, a.h, 0, false, hover, false);

        int arrow = font.getWidth(ARROW);

        batcher.text(ARROW, right - arrow, textY, hover ? Colors.LIGHTER_GRAY : Colors.GRAY, true);
        right -= arrow + PADDING;

        if (right > x)
        {
            batcher.text(font.limitToWidth(this.label, right - x), x, textY, this.muted ? Colors.GRAY : RowStyle.textColor(hover), true);
        }

        super.render(context);
    }
}
