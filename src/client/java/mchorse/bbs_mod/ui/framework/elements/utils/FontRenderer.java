package mchorse.bbs_mod.ui.framework.elements.utils;

import mchorse.bbs_mod.fonts.NativeFontRenderer;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class FontRenderer
{
    /** What the interface has always stepped lines by with the default font. */
    public static final int DEFAULT_LINE_HEIGHT = 12;

    private TextRenderer renderer;
    private NativeFontRenderer nativeRenderer;

    /**
     * {@link TextRenderer#fontHeight} is a constant 9 whichever font is drawing, so
     * a custom font has to bring its own metrics along - see
     * {@link mchorse.bbs_mod.fonts.FontManager}.
     */
    private int height;
    private int lineHeight = DEFAULT_LINE_HEIGHT;

    /* Zero-allocation direct-mapped cache for limitToWidth() so truncated timeline/list labels
     * don't re-measure character-by-character every frame. */
    private static final int LIMIT_CACHE_SIZE = 128;
    private static final int LIMIT_CACHE_MASK = LIMIT_CACHE_SIZE - 1;
    private final String[] limitKeyStr = new String[LIMIT_CACHE_SIZE];
    private final String[] limitKeySuffix = new String[LIMIT_CACHE_SIZE];
    private final int[] limitKeyWidth = new int[LIMIT_CACHE_SIZE];
    private final String[] limitValue = new String[LIMIT_CACHE_SIZE];

    private void clearLimitCache()
    {
        for (int i = 0; i < LIMIT_CACHE_SIZE; i++)
        {
            this.limitKeyStr[i] = null;
        }
    }

    public static List<String> wrap(TextRenderer renderer, String string, int width)
    {
        return renderer.wrapLines(Text.literal(string), width).stream().map((ot) ->
        {
            StringBuilder builder = new StringBuilder();
            StyleHolder holder = new StyleHolder(Style.EMPTY);

            ot.accept((a, b, c) ->
            {
                if (!Objects.equals(b, holder.style))
                {
                    styleToString(builder, b);

                    holder.style = b;
                }

                builder.appendCodePoint(c);

                return true;
            });

            return builder.toString();
        }).collect(Collectors.toList());
    }

    private static void styleToString(StringBuilder b, Style style)
    {
        /* Ew... */
        if (!style.isEmpty())
        {
            b.append("\u00A7r");
        }

        if (style.getColor() != null)
        {
            switch (style.getColor().getName())
            {
                case "black": b.append("\u00A70"); break;
                case "dark_blue": b.append("\u00A71"); break;
                case "dark_green": b.append("\u00A72"); break;
                case "dark_aqua": b.append("\u00A73"); break;
                case "dark_red": b.append("\u00A74"); break;
                case "dark_purple": b.append("\u00A75"); break;
                case "gold": b.append("\u00A76"); break;
                case "gray": b.append("\u00A77"); break;
                case "dark_gray": b.append("\u00A78"); break;
                case "blue": b.append("\u00A79"); break;
                case "green": b.append("\u00A7a"); break;
                case "aqua": b.append("\u00A7b"); break;
                case "red": b.append("\u00A7c"); break;
                case "light_purple": b.append("\u00A7d"); break;
                case "yellow": b.append("\u00A7e"); break;
                case "white": b.append("\u00A7f"); break;
            }
        }

        if (style.isObfuscated()) b.append("\u00A7k");
        if (style.isBold()) b.append("\u00A7l");
        if (style.isStrikethrough()) b.append("\u00A7m");
        if (style.isUnderlined()) b.append("\u00A7n");
        if (style.isItalic()) b.append("\u00A7o");
    }

    public void setRenderer(TextRenderer renderer)
    {
        this.setRenderer(renderer, renderer.fontHeight - 2, DEFAULT_LINE_HEIGHT);
    }

    public void setRenderer(TextRenderer renderer, int height, int lineHeight)
    {
        if (this.renderer != renderer || this.nativeRenderer != null || this.height != height)
        {
            this.clearLimitCache();
        }

        this.renderer = renderer;
        this.nativeRenderer = null;
        this.height = height;
        this.lineHeight = lineHeight;
    }

    public void setNativeRenderer(NativeFontRenderer nativeRenderer, TextRenderer fallbackRenderer)
    {
        if (this.nativeRenderer != nativeRenderer)
        {
            this.clearLimitCache();
        }

        this.nativeRenderer = nativeRenderer;
        this.renderer = fallbackRenderer;
        this.height = nativeRenderer.getHeight();
        this.lineHeight = nativeRenderer.getLineHeight();
    }

    public boolean hasNativeRenderer()
    {
        return this.nativeRenderer != null;
    }

    public void drawNative(MatrixStack matrices, String text, float x, float y, int color, boolean shadow)
    {
        if (this.nativeRenderer != null)
        {
            this.nativeRenderer.draw(matrices, text, x, y, color, shadow);
        }
    }

    public void drawNativeBatched(MatrixStack matrices, String text, float x, float y, int color, boolean shadow, NativeFontRenderer.BatchSink sink)
    {
        if (this.nativeRenderer != null)
        {
            this.nativeRenderer.drawBatched(matrices, text, x, y, color, shadow, sink);
        }
    }

    public TextRenderer getRenderer()
    {
        return this.renderer;
    }

    public int getWidth(String string)
    {
        if (this.nativeRenderer != null)
        {
            return this.nativeRenderer.getWidth(string);
        }

        return this.renderer.getWidth(string);
    }

    public int getHeight()
    {
        return this.height;
    }

    /** How far apart the baselines of two lines of this font sit. */
    public int getLineHeight()
    {
        return this.lineHeight;
    }

    public List<String> wrap(String string, int width)
    {
        if (this.nativeRenderer != null)
        {
            return this.nativeRenderer.wrap(string, width);
        }

        return wrap(this.renderer, string, width);
    }

    public String limitToWidth(String str, int width)
    {
        return limitToWidth(str, "...", width);
    }

    public String limitToWidth(String str, String suffix, int width)
    {
        if (str.isEmpty())
        {
            return str;
        }

        int slot = ((str.hashCode() * 31 + suffix.hashCode()) * 31 + width) & LIMIT_CACHE_MASK;

        if (width == this.limitKeyWidth[slot]
            && str.equals(this.limitKeyStr[slot])
            && suffix.equals(this.limitKeySuffix[slot]))
        {
            return this.limitValue[slot];
        }

        int w = this.getWidth(str);
        String result;

        if (w < width)
        {
            result = str;
        }
        else
        {
            int sw = this.getWidth(suffix);
            int i = str.length() - 1;

            while (w + sw >= width && i > 0)
            {
                w -= this.getWidth(String.valueOf(str.charAt(i)));
                i -= 1;
            }

            String cut = str.substring(0, i);
            result = cut.isEmpty() ? cut : cut + suffix;
        }

        this.limitKeyStr[slot] = str;
        this.limitKeySuffix[slot] = suffix;
        this.limitKeyWidth[slot] = width;
        this.limitValue[slot] = result;

        return result;
    }

    private static class StyleHolder
    {
        public Style style;

        public StyleHolder(Style style)
        {
            this.style = style;
        }
    }
}