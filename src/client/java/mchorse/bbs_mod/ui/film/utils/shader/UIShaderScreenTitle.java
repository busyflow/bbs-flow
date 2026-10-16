package mchorse.bbs_mod.ui.film.utils.shader;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.glfw.GLFW;

/**
 * The line over the shader panel's options, in one line however narrow the panel: on a sub-screen
 * the way back and where it is ("‹ Lighting"), a click going one screen up; while searching, a grey
 * note of what was found.
 */
public class UIShaderScreenTitle extends UIElement
{
    public static final int HEIGHT = 16;

    private static final String BACK = "‹";
    private static final int GAP = 4;

    private String text = "";

    /** What a click does; null for a note */
    private Runnable back;

    public UIShaderScreenTitle()
    {
        this.h(HEIGHT);
    }

    /** A sub-screen's title, after the way back up */
    public void screen(String title, Runnable back)
    {
        this.text = title;
        this.back = back;
    }

    /** A grey note that leads nowhere */
    public void note(String text)
    {
        this.text = text;
        this.back = null;
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (context.mouseButton == 0 && this.back != null && this.area.isInside(context))
        {
            UIUtils.playClick();
            this.back.run();

            return true;
        }

        return super.subMouseClicked(context);
    }

    @Override
    public void render(UIContext context)
    {
        FontRenderer font = context.batcher.getFont();
        int x = this.area.x;
        int y = this.area.my(font.getHeight());

        if (this.back == null)
        {
            context.batcher.textShadow(font.limitToWidth(this.text, this.area.w), x, y, Colors.GRAY);
        }
        else
        {
            boolean hover = this.area.isInside(context);

            if (hover)
            {
                context.requestCursor(GLFW.GLFW_HAND_CURSOR);
            }

            context.batcher.textShadow(BACK, x, y, hover ? Colors.WHITE : Colors.GRAY);
            x += font.getWidth(BACK) + GAP;
            context.batcher.textShadow(font.limitToWidth(this.text, this.area.ex() - x), x, y, hover ? Colors.WHITE : Colors.LIGHTEST_GRAY);
        }

        super.render(context);
    }
}
