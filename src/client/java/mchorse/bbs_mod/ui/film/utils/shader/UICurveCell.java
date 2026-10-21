package mchorse.bbs_mod.ui.film.utils.shader;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.RowStyle;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * One cell of the curve picker: a row the way every list in BBS draws one ({@link RowStyle}), set
 * out in the columns of the pack's menu. Every cell sits on a tile of its own, so neighbours don't
 * run into each other &mdash; packs like to spell a sentence across two cells.
 *
 * <ul>
 *     <li><b>curve</b> &mdash; something a curve can be made for. A click adds it, and the row
 *     then reads as picked: the accent wash with the bar down its edge. Another click takes it
 *     out again.</li>
 *     <li><b>option</b> &mdash; a shader option that can't be animated, shown so the pack's menu
 *     keeps its layout: grey, and deaf to the cursor.</li>
 *     <li><b>link</b> &mdash; a way into a sub-screen, with an arrow for a note.</li>
 *     <li><b>empty</b> &mdash; a gap the pack left in its layout.</li>
 * </ul>
 *
 * <p>The right side carries a grey note: the option's current value, a link's arrow, where a
 * search hit lives &mdash; or a colour swatch for a colour curve.</p>
 */
public class UICurveCell extends UIElement
{
    public static final int HEIGHT = 20;

    private static final int PADDING = 6;
    private static final int ICON_X = 3;
    private static final int ICON_TEXT_X = 21;
    private static final int SWATCH = 10;

    public enum Kind
    {
        CURVE, OPTION, LINK, EMPTY
    }

    public final Kind kind;

    private final Consumer<UICurveCell> callback;

    private String label = "";
    private Icon icon;
    private String note = "";
    private Integer swatch;
    private boolean muted;
    private boolean added;

    public static UICurveCell curve(String label, Consumer<UICurveCell> callback)
    {
        return new UICurveCell(Kind.CURVE, label, callback);
    }

    public static UICurveCell option(String label)
    {
        return new UICurveCell(Kind.OPTION, label, null).muted(true);
    }

    public static UICurveCell link(String label, Consumer<UICurveCell> callback)
    {
        return new UICurveCell(Kind.LINK, label, callback);
    }

    public static UICurveCell empty()
    {
        return new UICurveCell(Kind.EMPTY, "", null);
    }

    private UICurveCell(Kind kind, String label, Consumer<UICurveCell> callback)
    {
        this.kind = kind;
        this.label = label == null ? "" : label;
        this.callback = callback;

        this.h(HEIGHT);
    }

    public UICurveCell icon(Icon icon)
    {
        this.icon = icon;

        return this;
    }

    public UICurveCell note(String note)
    {
        this.note = note == null ? "" : note;

        return this;
    }

    public UICurveCell swatch(int color)
    {
        this.swatch = color;

        return this;
    }

    public UICurveCell muted(boolean muted)
    {
        this.muted = muted;

        return this;
    }

    public UICurveCell added(boolean added)
    {
        this.added = added;

        return this;
    }

    public boolean isAdded()
    {
        return this.added;
    }

    public boolean isActivatable()
    {
        return this.isEnabled() && (this.kind == Kind.LINK || this.kind == Kind.CURVE);
    }

    /** What a left click does: adds or removes the curve, or follows the link. */
    public void activate()
    {
        UIUtils.playClick();

        if (this.callback != null)
        {
            this.callback.accept(this);
        }
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (context.mouseButton == 0 && this.isActivatable() && this.area.isInside(context))
        {
            this.activate();

            return true;
        }

        return super.subMouseClicked(context);
    }

    @Override
    public void render(UIContext context)
    {
        boolean hover = this.isActivatable() && this.area.isInside(context);

        if (hover)
        {
            context.requestCursor(GLFW.GLFW_HAND_CURSOR);
        }

        this.renderSkin(context, hover);

        super.render(context);
    }

    private void renderSkin(UIContext context, boolean hover)
    {
        if (this.kind == Kind.EMPTY)
        {
            return;
        }

        Batcher2D batcher = context.batcher;
        FontRenderer font = batcher.getFont();
        Area a = this.area;
        boolean lit = hover || this.added;
        int textY = a.my(font.getHeight());
        int x = a.x + PADDING;
        int right = a.ex() - PADDING;

        a.render(batcher, BBSSettings.chromeSurface());

        RowStyle.row(batcher, a.x, a.y, a.w, a.h, 0, false, hover, this.added);

        if (this.icon != null)
        {
            batcher.icon(this.icon, this.muted ? Colors.GRAY : RowStyle.iconColor(lit), a.x + ICON_X, a.my(), 0F, 0.5F);
            x = a.x + ICON_TEXT_X;
        }

        if (this.swatch != null)
        {
            int sx = right - SWATCH;
            int sy = a.my() - SWATCH / 2;

            batcher.box(sx - 1, sy - 1, right + 1, sy + SWATCH + 1, Colors.A50);
            batcher.box(sx, sy, right, sy + SWATCH, Colors.A100 | this.swatch);

            right = sx - PADDING;
        }
        else if (!this.note.isEmpty())
        {
            int noteWidth = Math.min(font.getWidth(this.note), Math.max(0, (right - x) / 2));
            String note = font.limitToWidth(this.note, noteWidth + 1);
            int w = font.getWidth(note);

            batcher.text(note, right - w, textY, lit ? Colors.LIGHTER_GRAY : Colors.GRAY, true);

            right -= w + PADDING;
        }

        if (right > x)
        {
            batcher.text(font.limitToWidth(this.label, right - x), x, textY, this.muted ? Colors.GRAY : RowStyle.textColor(lit), true);
        }
    }
}
