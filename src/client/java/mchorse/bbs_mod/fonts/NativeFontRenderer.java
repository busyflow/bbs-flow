package mchorse.bbs_mod.fonts;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.resources.Link;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 1:1 physical-pixel OS-hinted TrueType font renderer for BBS UI.
 *
 * <p>Rasterizes glyphs via Java2D at the exact physical framebuffer pixel size with
 * integer-pixel hinting ({@code FRACTIONALMETRICS_OFF} + grayscale AA), uploads them
 * lazily to a 1024x1024 RGBA atlas, and draws them snapped to physical pixel centers
 * with the {@code native_text} contrast-tuned GPU shader.</p>
 */
public class NativeFontRenderer
{
    private static final int ATLAS_SIZE = 1024;
    private static final int MAX_CACHED_FACES = 24;

    private static final int[] MC_COLORS = {
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA,
        0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF,
        0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    private static final Map<String, Font> BASE_FONTS = new HashMap<>();
    private static final Map<FaceKey, Face> FACES = new LinkedHashMap<>(16, 0.75F, true);

    private static NativeFontRenderer cachedInstance;
    private static String cachedFontPath;
    private static int cachedLogicalSize = -1;
    private static float cachedPhysicalScale = Float.NaN;

    private static ShaderProgram cachedTextShader;
    private static GlUniform cachedGammaUniform;

    private final String fontKey;
    private final int physicalPx;
    private final float physicalScale;
    private Face plainFace;
    private final Map<String, Integer> widthCache = new HashMap<>(256);

    private NativeFontRenderer(String fontKey, int physicalPx, float physicalScale)
    {
        this.fontKey = fontKey;
        this.physicalPx = physicalPx;
        this.physicalScale = physicalScale;
    }

    public static NativeFontRenderer get(String fontPath, int logicalSize, float physicalScale)
    {
        if (fontPath == null || fontPath.isEmpty())
        {
            return null;
        }

        float safeScale = Math.max(0.5F, physicalScale);

        if (cachedInstance != null
            && logicalSize == cachedLogicalSize
            && Float.compare(safeScale, cachedPhysicalScale) == 0
            && fontPath.equals(cachedFontPath))
        {
            return cachedInstance;
        }

        Font base = getBaseFont(fontPath);

        if (base == null)
        {
            return null;
        }

        int physicalPx = Math.max(8, Math.min(96, Math.round(logicalSize * safeScale)));
        NativeFontRenderer instance = new NativeFontRenderer(fontPath, physicalPx, safeScale);

        cachedInstance = instance;
        cachedFontPath = fontPath;
        cachedLogicalSize = logicalSize;
        cachedPhysicalScale = safeScale;

        return instance;
    }

    public static void clearCache()
    {
        for (Face face : FACES.values())
        {
            face.delete();
        }

        FACES.clear();
        BASE_FONTS.clear();
        cachedInstance = null;
        cachedFontPath = null;
        cachedLogicalSize = -1;
        cachedPhysicalScale = Float.NaN;
        cachedTextShader = null;
        cachedGammaUniform = null;
    }

    private static Font getBaseFont(String fontPath)
    {
        if (BASE_FONTS.containsKey(fontPath))
        {
            return BASE_FONTS.get(fontPath);
        }

        Font loaded = null;

        try
        {
            Link link = Link.create(fontPath);

            try (InputStream stream = BBSMod.getProvider().getAsset(link))
            {
                loaded = Font.createFont(Font.TRUETYPE_FONT, stream);
            }
        }
        catch (Exception ignored)
        {
            /* Fall back to system font if the path names one directly (e.g. "Verdana"). */
            try
            {
                Font sys = new Font(fontPath, Font.PLAIN, 12);

                if (sys.getFamily() != null && !sys.getFamily().equalsIgnoreCase("Dialog"))
                {
                    loaded = sys;
                }
            }
            catch (Exception ignored2)
            {}
        }

        BASE_FONTS.put(fontPath, loaded);

        return loaded;
    }

    private Face getFace(int style)
    {
        if (style == Font.PLAIN && this.plainFace != null)
        {
            return this.plainFace;
        }

        FaceKey key = new FaceKey(this.fontKey, this.physicalPx, style);
        Face face = FACES.get(key);

        if (face != null)
        {
            if (style == Font.PLAIN)
            {
                this.plainFace = face;
            }

            return face;
        }

        Font base = getBaseFont(this.fontKey);

        if (base == null)
        {
            return null;
        }

        Font derived = base.deriveFont(style, (float) this.physicalPx);
        face = new Face(derived);
        FACES.put(key, face);

        if (style == Font.PLAIN)
        {
            this.plainFace = face;
        }

        while (FACES.size() > MAX_CACHED_FACES)
        {
            Iterator<Map.Entry<FaceKey, Face>> it = FACES.entrySet().iterator();

            if (it.hasNext())
            {
                Map.Entry<FaceKey, Face> oldest = it.next();
                Face removed = oldest.getValue();
                if (removed == this.plainFace)
                {
                    this.plainFace = null;
                }
                removed.delete();
                it.remove();
            }
        }

        return face;
    }

    public int getHeight()
    {
        Face face = this.getFace(Font.PLAIN);

        if (face == null)
        {
            return 7;
        }

        return Math.max(5, Math.round(face.capHeight / this.physicalScale));
    }

    public int getLineHeight()
    {
        Face face = this.getFace(Font.PLAIN);

        if (face == null)
        {
            return 12;
        }

        return Math.max(8, Math.round((face.ascent + face.descent + 2F) / this.physicalScale));
    }

    public int getWidth(String text)
    {
        if (text == null || text.isEmpty())
        {
            return 0;
        }

        Integer cached = this.widthCache.get(text);

        if (cached != null)
        {
            return cached;
        }

        int style = Font.PLAIN;
        Face face = this.getFace(style);

        if (face == null)
        {
            return 0;
        }

        float px = 0F;
        int len = text.length();

        for (int i = 0; i < len; i++)
        {
            char c = text.charAt(i);

            if (c == '\u00A7' && i + 1 < len)
            {
                char code = Character.toLowerCase(text.charAt(i + 1));
                i++;

                if (code == 'l') style |= Font.BOLD;
                else if (code == 'o') style |= Font.ITALIC;
                else if (code == 'r' || (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) style = Font.PLAIN;

                face = this.getFace(style);
                continue;
            }

            if (face != null)
            {
                px += face.getAdvance(c);
            }
        }

        int width = Math.round(px / this.physicalScale);

        if (this.widthCache.size() > 512)
        {
            this.widthCache.clear();
        }

        this.widthCache.put(text, width);

        return width;
    }

    public List<String> wrap(String text, int maxWidth)
    {
        List<String> lines = new ArrayList<>();

        if (text == null || text.isEmpty())
        {
            lines.add("");
            return lines;
        }

        int limit = Math.max(1, maxWidth);
        String[] paragraphs = text.split("\n", -1);

        for (String paragraph : paragraphs)
        {
            if (paragraph.isEmpty() || this.getWidth(paragraph) <= limit)
            {
                lines.add(paragraph);
                continue;
            }

            String activeFormat = "";
            StringBuilder current = new StringBuilder();
            String[] words = paragraph.split(" ", -1);

            for (int w = 0; w < words.length; w++)
            {
                String word = words[w];
                String candidate = current.length() == 0 ? word : current + " " + word;

                if (this.getWidth(candidate) <= limit)
                {
                    if (current.length() > 0)
                    {
                        current.append(' ');
                    }

                    current.append(word);
                }
                else
                {
                    if (current.length() > 0)
                    {
                        lines.add(current.toString());
                        activeFormat = extractTrailingFormat(current.toString());
                        current.setLength(0);
                    }

                    if (this.getWidth(activeFormat + word) <= limit)
                    {
                        current.append(activeFormat).append(word);
                    }
                    else
                    {
                        /* Break overly long single word by character */
                        StringBuilder chunk = new StringBuilder(activeFormat);

                        for (int i = 0; i < word.length(); i++)
                        {
                            char c = word.charAt(i);

                            if (c == '\u00A7' && i + 1 < word.length())
                            {
                                chunk.append(c).append(word.charAt(i + 1));
                                i++;
                                continue;
                            }

                            if (this.getWidth(chunk.toString() + c) > limit && chunk.length() > activeFormat.length())
                            {
                                lines.add(chunk.toString());
                                activeFormat = extractTrailingFormat(chunk.toString());
                                chunk.setLength(0);
                                chunk.append(activeFormat);
                            }

                            chunk.append(c);
                        }

                        current.append(chunk);
                    }
                }
            }

            if (current.length() > 0)
            {
                lines.add(current.toString());
            }
        }

        return lines;
    }

    private static String extractTrailingFormat(String line)
    {
        StringBuilder fmt = new StringBuilder();

        for (int i = 0; i < line.length() - 1; i++)
        {
            if (line.charAt(i) == '\u00A7')
            {
                char code = Character.toLowerCase(line.charAt(i + 1));

                if (code == 'r')
                {
                    fmt.setLength(0);
                }
                else if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f'))
                {
                    fmt.setLength(0);
                    fmt.append('\u00A7').append(code);
                }
                else if (code == 'l' || code == 'o' || code == 'n' || code == 'm' || code == 'k')
                {
                    fmt.append('\u00A7').append(code);
                }

                i++;
            }
        }

        return fmt.toString();
    }

    @FunctionalInterface
    public interface BatchSink
    {
        BufferBuilder acquire(int textureId, ShaderProgram shader, float contrastGamma);
    }

    public static void applyContrastGamma(ShaderProgram shader, float gamma)
    {
        if (shader == null)
        {
            return;
        }

        if (shader != cachedTextShader)
        {
            cachedTextShader = shader;
            cachedGammaUniform = shader.getUniform("ContrastGamma");
        }

        if (cachedGammaUniform != null)
        {
            cachedGammaUniform.set(gamma);
        }
    }

    public void drawBatched(MatrixStack matrices, String text, float x, float y, int color, boolean shadow, BatchSink sink)
    {
        if (text == null || text.isEmpty() || sink == null)
        {
            return;
        }

        int alpha = (color >>> 24) & 0xFF;

        if (alpha == 0)
        {
            alpha = 0xFF;
            color = (alpha << 24) | (color & 0x00FFFFFF);
        }

        if (shadow)
        {
            int sr = ((color >> 16) & 0xFF) / 4;
            int sg = ((color >> 8) & 0xFF) / 4;
            int sb = (color & 0xFF) / 4;
            int shadowColor = (alpha << 24) | (sr << 16) | (sg << 8) | sb;
            float offset = 1F / this.physicalScale;

            this.drawPassBatched(matrices, text, x + offset, y + offset, shadowColor, true, sink);
        }

        this.drawPassBatched(matrices, text, x, y, color, false, sink);
    }

    private void drawPassBatched(MatrixStack matrices, String text, float x, float y, int baseColor, boolean isShadow, BatchSink sink)
    {
        Face defaultFace = this.getFace(Font.PLAIN);

        if (defaultFace == null)
        {
            return;
        }

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float invScale = 1F / this.physicalScale;
        float penPx = Math.round(x * this.physicalScale);
        float topPx = Math.round(y * this.physicalScale);
        float baselinePx = topPx + defaultFace.capHeight;

        int style = Font.PLAIN;
        int currentColor = baseColor;
        int baseAlpha = (baseColor >>> 24) & 0xFF;

        Face currentFace = defaultFace;
        ShaderProgram shader = BBSShaders.getNativeTextProgram();
        float gamma = (isShadow || BBSSettings.lightSurfaces()) ? 1.0F : 0.84F;

        int len = text.length();

        for (int i = 0; i < len; i++)
        {
            char c = text.charAt(i);

            if (c == '\u00A7' && i + 1 < len)
            {
                char code = Character.toLowerCase(text.charAt(i + 1));
                i++;

                int colorIdx = "0123456789abcdef".indexOf(code);

                if (colorIdx >= 0)
                {
                    int rgb = MC_COLORS[colorIdx];

                    if (isShadow)
                    {
                        int sr = ((rgb >> 16) & 0xFF) / 4;
                        int sg = ((rgb >> 8) & 0xFF) / 4;
                        int sb = (rgb & 0xFF) / 4;
                        rgb = (sr << 16) | (sg << 8) | sb;
                    }

                    currentColor = (baseAlpha << 24) | rgb;
                    style = Font.PLAIN;
                    currentFace = this.getFace(style);
                }
                else if (code == 'l')
                {
                    style |= Font.BOLD;
                    currentFace = this.getFace(style);
                }
                else if (code == 'o')
                {
                    style |= Font.ITALIC;
                    currentFace = this.getFace(style);
                }
                else if (code == 'r')
                {
                    currentColor = baseColor;
                    style = Font.PLAIN;
                    currentFace = defaultFace;
                }

                continue;
            }

            if (currentFace == null)
            {
                continue;
            }

            Glyph glyph = currentFace.getOrRasterize(c);

            if (glyph == null)
            {
                continue;
            }

            if (glyph.width > 0 && glyph.height > 0)
            {
                int texId = currentFace.getTextureId();
                BufferBuilder builder = sink.acquire(texId, shader, gamma);

                float gx1 = (penPx + glyph.offsetX) * invScale;
                float gy1 = (baselinePx + glyph.offsetY) * invScale;
                float gx2 = (penPx + glyph.offsetX + glyph.width) * invScale;
                float gy2 = (baselinePx + glyph.offsetY + glyph.height) * invScale;

                builder.vertex(matrix, gx1, gy2, 0F).texture(glyph.u1, glyph.v2).color(currentColor).next();
                builder.vertex(matrix, gx2, gy2, 0F).texture(glyph.u2, glyph.v2).color(currentColor).next();
                builder.vertex(matrix, gx2, gy1, 0F).texture(glyph.u2, glyph.v1).color(currentColor).next();
                builder.vertex(matrix, gx1, gy1, 0F).texture(glyph.u1, glyph.v1).color(currentColor).next();
            }

            penPx += glyph.advance;
        }
    }

    public void draw(MatrixStack matrices, String text, float x, float y, int color, boolean shadow)
    {
        if (text == null || text.isEmpty())
        {
            return;
        }

        int alpha = (color >>> 24) & 0xFF;

        if (alpha == 0)
        {
            alpha = 0xFF;
            color = (alpha << 24) | (color & 0x00FFFFFF);
        }

        if (shadow)
        {
            int sr = ((color >> 16) & 0xFF) / 4;
            int sg = ((color >> 8) & 0xFF) / 4;
            int sb = (color & 0xFF) / 4;
            int shadowColor = (alpha << 24) | (sr << 16) | (sg << 8) | sb;
            float offset = 1F / this.physicalScale;

            this.drawPass(matrices, text, x + offset, y + offset, shadowColor, true);
        }

        this.drawPass(matrices, text, x, y, color, false);
    }

    private void drawPass(MatrixStack matrices, String text, float x, float y, int baseColor, boolean isShadow)
    {
        Face defaultFace = this.getFace(Font.PLAIN);

        if (defaultFace == null)
        {
            return;
        }

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float invScale = 1F / this.physicalScale;

        /* Snap origin to exact physical pixel centers so 1:1 atlas texels never blur */
        float penPx = Math.round(x * this.physicalScale);
        float topPx = Math.round(y * this.physicalScale);

        /* Align cap-height top with y so BBS's area.my(font.getHeight()) centers text visually */
        float baselinePx = topPx + defaultFace.capHeight;

        int style = Font.PLAIN;
        int currentColor = baseColor;
        int baseAlpha = (baseColor >>> 24) & 0xFF;

        Face currentFace = defaultFace;
        ShaderProgram shader = BBSShaders.getNativeTextProgram();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        if (shader != null)
        {
            if (shader != cachedTextShader)
            {
                cachedTextShader = shader;
                cachedGammaUniform = shader.getUniform("ContrastGamma");
            }

            if (cachedGammaUniform != null)
            {
                /* Slight stem darkening on dark backgrounds keeps small Ctrl+- text razor sharp */
                float g = (isShadow || BBSSettings.lightSurfaces()) ? 1.0F : 0.84F;
                cachedGammaUniform.set(g);
            }

            RenderSystem.setShader(() -> shader);
        }
        else
        {
            RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        }

        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        boolean building = false;
        int boundTexId = -1;

        int len = text.length();

        for (int i = 0; i < len; i++)
        {
            char c = text.charAt(i);

            if (c == '\u00A7' && i + 1 < len)
            {
                char code = Character.toLowerCase(text.charAt(i + 1));
                i++;

                int colorIdx = "0123456789abcdef".indexOf(code);

                if (colorIdx >= 0)
                {
                    int rgb = MC_COLORS[colorIdx];

                    if (isShadow)
                    {
                        int sr = ((rgb >> 16) & 0xFF) / 4;
                        int sg = ((rgb >> 8) & 0xFF) / 4;
                        int sb = (rgb & 0xFF) / 4;
                        rgb = (sr << 16) | (sg << 8) | sb;
                    }

                    currentColor = (baseAlpha << 24) | rgb;
                    style = Font.PLAIN;
                    currentFace = this.getFace(style);
                }
                else if (code == 'l')
                {
                    style |= Font.BOLD;
                    currentFace = this.getFace(style);
                }
                else if (code == 'o')
                {
                    style |= Font.ITALIC;
                    currentFace = this.getFace(style);
                }
                else if (code == 'r')
                {
                    currentColor = baseColor;
                    style = Font.PLAIN;
                    currentFace = defaultFace;
                }

                continue;
            }

            if (currentFace == null)
            {
                continue;
            }

            Glyph glyph = currentFace.getOrRasterize(c);

            if (glyph == null)
            {
                continue;
            }

            if (glyph.width > 0 && glyph.height > 0)
            {
                int texId = currentFace.getTextureId();

                if (texId != boundTexId)
                {
                    if (building)
                    {
                        BufferRenderer.drawWithGlobalProgram(builder.end());
                        building = false;
                    }

                    RenderSystem.setShaderTexture(0, texId);
                    boundTexId = texId;
                }

                if (!building)
                {
                    builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                    building = true;
                }

                float gx1 = (penPx + glyph.offsetX) * invScale;
                float gy1 = (baselinePx + glyph.offsetY) * invScale;
                float gx2 = (penPx + glyph.offsetX + glyph.width) * invScale;
                float gy2 = (baselinePx + glyph.offsetY + glyph.height) * invScale;

                builder.vertex(matrix, gx1, gy2, 0F).texture(glyph.u1, glyph.v2).color(currentColor).next();
                builder.vertex(matrix, gx2, gy2, 0F).texture(glyph.u2, glyph.v2).color(currentColor).next();
                builder.vertex(matrix, gx2, gy1, 0F).texture(glyph.u2, glyph.v1).color(currentColor).next();
                builder.vertex(matrix, gx1, gy1, 0F).texture(glyph.u1, glyph.v1).color(currentColor).next();
            }

            penPx += glyph.advance;
        }

        if (building)
        {
            BufferRenderer.drawWithGlobalProgram(builder.end());
        }
    }

    private record FaceKey(String fontKey, int physicalPx, int style) {}

    private static class Glyph
    {
        final float u1, v1, u2, v2;
        final int width, height;
        final int offsetX, offsetY;
        final int advance;

        Glyph(float u1, float v1, float u2, float v2, int width, int height, int offsetX, int offsetY, int advance)
        {
            this.u1 = u1;
            this.v1 = v1;
            this.u2 = u2;
            this.v2 = v2;
            this.width = width;
            this.height = height;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.advance = advance;
        }
    }

    private static class Face
    {
        final Font font;
        final int ascent;
        final int descent;
        final int capHeight;
        final FontMetrics metrics;

        private final Map<Character, Glyph> glyphs = new HashMap<>();
        private final Glyph[] asciiGlyphs = new Glyph[128];
        private final int[] asciiAdvances = new int[128];
        private int textureId = -1;
        private int cursorX = 1;
        private int cursorY = 1;
        private int rowHeight = 0;

        private final BufferedImage scratch;
        private final Graphics2D scratchG;

        Face(Font font)
        {
            this.font = font;
            this.scratch = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
            this.scratchG = this.scratch.createGraphics();
            this.scratchG.setFont(font);
            this.scratchG.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            this.scratchG.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
            this.scratchG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            this.scratchG.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            this.metrics = this.scratchG.getFontMetrics(font);
            this.ascent = this.metrics.getAscent();
            this.descent = this.metrics.getDescent();
            this.capHeight = measureCapHeight();

            for (int i = 0; i < 128; i++)
            {
                this.asciiAdvances[i] = Math.max(0, this.metrics.charWidth((char) i));
            }
        }

        private int measureCapHeight()
        {
            /* Measure actual rendered pixel height of 'H' so vertical centering is exact */
            int w = 64;
            int h = Math.max(32, this.ascent + this.descent + 8);
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setFont(this.font);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
            g.setColor(Color.WHITE);
            int base = this.ascent + 2;
            g.drawString("H", 4, base);
            g.dispose();

            int minY = base;
            int maxY = base;

            for (int y = 0; y < h; y++)
            {
                for (int x = 0; x < w; x++)
                {
                    if (((img.getRGB(x, y) >>> 24) & 0xFF) > 32)
                    {
                        if (y < minY) minY = y;
                        if (y > maxY) maxY = y;
                    }
                }
            }

            int measured = maxY - minY + 1;

            return measured > 3 ? measured : Math.max(6, Math.round(this.ascent * 0.72F));
        }

        int getAdvance(char c)
        {
            if (c < 128)
            {
                return this.asciiAdvances[c];
            }

            Glyph g = this.glyphs.get(c);

            if (g != null)
            {
                return g.advance;
            }

            return this.metrics.charWidth(c);
        }

        private static void resetUnpackState()
        {
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 1);
        }

        int getTextureId()
        {
            if (this.textureId == -1)
            {
                this.textureId = GL11.glGenTextures();
                GlStateManager._bindTexture(this.textureId);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

                resetUnpackState();
                ByteBuffer empty = BufferUtils.createByteBuffer(ATLAS_SIZE * ATLAS_SIZE * 4);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, ATLAS_SIZE, ATLAS_SIZE, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, empty);
            }

            return this.textureId;
        }

        Glyph getOrRasterize(char c)
        {
            if (c < 128)
            {
                Glyph fast = this.asciiGlyphs[c];

                if (fast != null)
                {
                    return fast;
                }
            }

            Glyph existing = this.glyphs.get(c);

            if (existing != null)
            {
                return existing;
            }

            int advance = c < 128 ? this.asciiAdvances[c] : Math.max(0, this.metrics.charWidth(c));

            if (c == ' ' || c == '\t' || Character.isISOControl(c))
            {
                Glyph space = new Glyph(0, 0, 0, 0, 0, 0, 0, 0, advance);
                this.glyphs.put(c, space);
                if (c < 128) this.asciiGlyphs[c] = space;
                return space;
            }

            int pad = 2;
            int cellW = Math.min(this.scratch.getWidth(), Math.max(16, advance + pad * 4));
            int cellH = Math.min(this.scratch.getHeight(), Math.max(16, this.ascent + this.descent + pad * 4));
            int drawX = pad * 2;
            int drawBaseY = pad + this.ascent;

            this.scratchG.setBackground(new Color(0, 0, 0, 0));
            this.scratchG.clearRect(0, 0, cellW, cellH);
            this.scratchG.setColor(Color.WHITE);
            this.scratchG.drawString(String.valueOf(c), drawX, drawBaseY);

            int minX = cellW, minY = cellH, maxX = -1, maxY = -1;

            for (int y = 0; y < cellH; y++)
            {
                for (int x = 0; x < cellW; x++)
                {
                    if (((this.scratch.getRGB(x, y) >>> 24) & 0xFF) > 0)
                    {
                        if (x < minX) minX = x;
                        if (x > maxX) maxX = x;
                        if (y < minY) minY = y;
                        if (y > maxY) maxY = y;
                    }
                }
            }

            if (maxX < minX || maxY < minY)
            {
                Glyph empty = new Glyph(0, 0, 0, 0, 0, 0, 0, 0, advance);
                this.glyphs.put(c, empty);
                return empty;
            }

            int gw = maxX - minX + 1;
            int gh = maxY - minY + 1;

            if (this.cursorX + gw + 1 >= ATLAS_SIZE)
            {
                this.cursorX = 1;
                this.cursorY += this.rowHeight + 1;
                this.rowHeight = 0;
            }

            if (this.cursorY + gh + 1 >= ATLAS_SIZE)
            {
                /* Atlas full: return non-rendered advance rather than overflowing */
                Glyph fallback = new Glyph(0, 0, 0, 0, 0, 0, 0, 0, advance);
                this.glyphs.put(c, fallback);
                return fallback;
            }

            int atlasX = this.cursorX;
            int atlasY = this.cursorY;
            this.cursorX += gw + 1;
            if (gh > this.rowHeight) this.rowHeight = gh;

            ByteBuffer pixels = BufferUtils.createByteBuffer(gw * gh * 4);

            for (int y = minY; y <= maxY; y++)
            {
                for (int x = minX; x <= maxX; x++)
                {
                    int argb = this.scratch.getRGB(x, y);
                    int a = (argb >>> 24) & 0xFF;

                    pixels.put((byte) 255);
                    pixels.put((byte) 255);
                    pixels.put((byte) 255);
                    pixels.put((byte) a);
                }
            }

            pixels.flip();

            int texId = this.getTextureId();
            org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
            GlStateManager._bindTexture(texId);
            resetUnpackState();
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, atlasX, atlasY, gw, gh, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);

            float invAtlas = 1F / ATLAS_SIZE;
            Glyph glyph = new Glyph(
                atlasX * invAtlas,
                atlasY * invAtlas,
                (atlasX + gw) * invAtlas,
                (atlasY + gh) * invAtlas,
                gw,
                gh,
                minX - drawX,
                minY - drawBaseY,
                advance
            );

            this.glyphs.put(c, glyph);
            if (c < 128) this.asciiGlyphs[c] = glyph;
            return glyph;
        }

        void delete()
        {
            if (this.textureId != -1)
            {
                GL11.glDeleteTextures(this.textureId);
                this.textureId = -1;
            }

            this.scratchG.dispose();
        }
    }
}
