package mchorse.bbs_mod.ui.film.utils.shader;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The name on the left of a row of the shader panel, in the colour the row's state calls for. A
 * search hit adds the screen it lives on in grey.
 */
public class UIShaderRowName extends UIElement
{
    private final Supplier<String> text;
    private final IntSupplier color;
    private final String where;

    /**
     * @param where for a search hit, the screen the row's option lives on; null otherwise
     */
    public UIShaderRowName(Supplier<String> text, IntSupplier color, String where)
    {
        this.text = text;
        this.color = color;
        this.where = where;
    }

    @Override
    public void render(UIContext context)
    {
        FontRenderer font = context.batcher.getFont();
        int x = this.area.x;
        int right = this.area.ex();
        int y = this.area.my(font.getHeight());
        String label = font.limitToWidth(this.text.get(), right - x);

        context.batcher.text(label, x, y, this.color.getAsInt(), true);

        if (this.where != null)
        {
            x += font.getWidth(label) + 6;

            if (right - x > 12)
            {
                context.batcher.text(font.limitToWidth(this.where, right - x), x, y, Colors.GRAY, true);
            }
        }

        super.render(context);
    }
}
