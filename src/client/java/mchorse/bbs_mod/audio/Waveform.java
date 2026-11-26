package mchorse.bbs_mod.audio;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.utils.resources.Pixels;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.GL11;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Waveform
{
    private static final int PREVIEW_TILE_WIDTH = 512;
    private static final int PREVIEW_TILE_LIMIT = 8;
    private static final int DETAILED_TILE_WIDTH = 512;
    private static final int DETAILED_TILE_LIMIT = 24;
    private WaveformEnvelope envelope;
    private List<ColorCode> colorCodes;
    private float[] cues;
    private int pixelsPerSecond;
    private int height;
    private float duration;
    private final Map<Integer, Texture> previewTiles = new LinkedHashMap<>(16, 0.75F, true);
    private final Map<DetailedTileKey, Texture> detailedTiles = new LinkedHashMap<>(32, 0.75F, true);

    private record DetailedTileKey(long ppsQuarter, int tileHeight, int tileIndex) {}

    public void generate(Wave data, List<ColorCode> colorCodes, int pixelsPerSecond, int height)
    {
        this.populate(data, pixelsPerSecond, height);
        this.render(colorCodes, data.getCues());
    }

    /** Update annotations without rebuilding the audio envelope. */
    public void render(List<ColorCode> colorCodes, float[] cues)
    {
        this.clearPreviewTiles();
        this.colorCodes = colorCodes;
        this.cues = cues;
    }

    public void populate(Wave data, int pixelsPerSecond, int height)
    {
        this.clearPreviewTiles();
        this.envelope = new WaveformEnvelope(data);
        this.pixelsPerSecond = Math.max(1, pixelsPerSecond);
        this.height = height;
        this.duration = data.getDuration();
    }

    public void delete()
    {
        this.clearPreviewTiles();
        this.envelope = null;
        this.colorCodes = null;
        this.cues = null;
    }

    public boolean isCreated()
    {
        return this.envelope != null;
    }

    /** Preview scale only; waveform detail is determined by the visible pixels. */
    public int getPixelsPerSecond()
    {
        return this.pixelsPerSecond;
    }

    public int getWidth()
    {
        return (int) Math.ceil(this.duration * this.pixelsPerSecond);
    }

    public int getHeight()
    {
        return this.height;
    }

    public float getDuration()
    {
        return this.duration;
    }

    /** The recording and film preview use a fixed scale, so reuse small texture tiles while playback scrolls. */
    public void renderPreview(Batcher2D batcher, int color, int x, int y, int w, int h, float startTime, float endTime)
    {
        if (this.envelope == null || w <= 0 || h <= 0 || !(endTime > startTime))
        {
            return;
        }

        double pixelsPerScreenSecond = w / (endTime - (double) startTime);
        int visibleFirst = (int) Math.max(0L, -(long) x);
        int visibleLast = (int) Math.min(w, (long) MinecraftClient.getInstance().getWindow().getWidth() - x);

        if (visibleLast <= visibleFirst)
        {
            return;
        }

        double visibleStartTime = startTime + visibleFirst / pixelsPerScreenSecond;
        double visibleEndTime = startTime + visibleLast / pixelsPerScreenSecond;
        int first = Math.max(0, (int) Math.floor(visibleStartTime * this.pixelsPerSecond / PREVIEW_TILE_WIDTH));
        int last = (int) Math.floor(Math.min(this.duration, visibleEndTime) * this.pixelsPerSecond / PREVIEW_TILE_WIDTH);

        batcher.clip(x, y, w, h, 0, 0);

        for (int tile = first; tile <= last; tile++)
        {
            double tileStart = tile * (double) PREVIEW_TILE_WIDTH / this.pixelsPerSecond;

            if (tileStart >= this.duration || tileStart >= endTime)
            {
                break;
            }

            Texture texture = this.getPreviewTile(tile);
            float drawX = (float) (x + (tileStart - startTime) * pixelsPerScreenSecond);
            float drawW = (float) (PREVIEW_TILE_WIDTH * pixelsPerScreenSecond / this.pixelsPerSecond);

            batcher.texturedBox(texture, color, drawX, y, drawW, h, 0, 0, PREVIEW_TILE_WIDTH, this.height);
        }

        batcher.unclip(0, 0);
    }

    private Texture getPreviewTile(int index)
    {
        Texture cached = this.previewTiles.get(index);

        if (cached != null)
        {
            return cached;
        }

        Pixels pixels = Pixels.fromSize(PREVIEW_TILE_WIDTH, this.height);
        WaveformEnvelope.Range range = new WaveformEnvelope.Range();
        Color background = new Color();
        int firstColumn = index * PREVIEW_TILE_WIDTH;

        for (int x = 0; x < PREVIEW_TILE_WIDTH; x++)
        {
            double from = (firstColumn + x) / (double) this.pixelsPerSecond;

            if (from >= this.duration)
            {
                break;
            }

            this.envelope.sample(from, from + 1D / this.pixelsPerSecond, range);
            int columnColor = Colors.WHITE;

            if (this.colorCodes != null)
            {
                for (ColorCode code : this.colorCodes)
                {
                    if (code.isInside((float) from))
                    {
                        columnColor = code.color | 0xff000000;
                        background.set(columnColor);

                        for (int row = 0; row < this.height; row++)
                        {
                            background.a = 0.125F + 0.25F * row / this.height;
                            pixels.setColor(x, row, background);
                        }

                        break;
                    }
                }
            }

            if (this.cues != null)
            {
                for (float cue : this.cues)
                {
                    if (cue >= from && cue < from + 1D / this.pixelsPerSecond)
                    {
                        pixels.drawRect(x, 0, 1, this.height, Colors.ACTIVE | Colors.A75);
                        break;
                    }
                }
            }

            int peak = range.maximum > 0 ? Math.max(1, Math.round(range.maximum * this.height)) : 0;
            int average = range.average > 0 ? Math.max(1, Math.round(range.average * this.height)) : 0;

            if (peak > 0) pixels.drawRect(x, (this.height - peak) / 2, 1, peak, columnColor);
            if (average > 0) pixels.drawRect(x, (this.height - average) / 2, 1, average, Colors.mulRGB(columnColor, 0.8F));
        }

        pixels.rewindBuffer();
        Texture texture = Texture.textureFromPixels(pixels, GL11.GL_NEAREST);
        pixels.delete();
        this.previewTiles.put(index, texture);

        if (this.previewTiles.size() > PREVIEW_TILE_LIMIT)
        {
            Integer oldest = this.previewTiles.keySet().iterator().next();
            this.previewTiles.remove(oldest).delete();
        }

        return texture;
    }

    private void clearPreviewTiles()
    {
        for (Texture texture : this.previewTiles.values())
        {
            texture.delete();
        }

        this.previewTiles.clear();

        for (Texture texture : this.detailedTiles.values())
        {
            texture.delete();
        }

        this.detailedTiles.clear();
    }

    public void render(Batcher2D batcher, int color, int x, int y, int w, int h, float startTime, float endTime)
    {
        if (!BBSSettings.audioWaveformDetailed.get())
        {
            this.renderPreview(batcher, color, x, y, w, h, startTime, endTime);

            return;
        }

        if (this.envelope == null || w <= 0 || h <= 0 || !(endTime > startTime))
        {
            return;
        }

        double pixelsPerScreenSecond = w / (endTime - (double) startTime);
        int visibleFirst = (int) Math.max(0L, -(long) x);
        int visibleLast = (int) Math.min(w, (long) MinecraftClient.getInstance().getWindow().getWidth() - x);

        if (visibleLast <= visibleFirst)
        {
            return;
        }

        long ppsQuarter = Math.max(1L, Math.round(pixelsPerScreenSecond * 4D));
        double tilePps = ppsQuarter / 4D;
        int tileHeight = Math.max(1, Math.min(256, h));

        double visibleStartTime = Math.max(0D, startTime + visibleFirst / pixelsPerScreenSecond);
        double visibleEndTime = Math.min(this.duration, startTime + visibleLast / pixelsPerScreenSecond);

        if (visibleEndTime <= visibleStartTime)
        {
            return;
        }

        double firstD = Math.floor(visibleStartTime * tilePps / DETAILED_TILE_WIDTH);
        double lastD = Math.floor(visibleEndTime * tilePps / DETAILED_TILE_WIDTH);

        if (firstD >= 0D && lastD - firstD <= 64D && lastD <= Integer.MAX_VALUE)
        {
            int first = (int) firstD;
            int last = (int) lastD;

            batcher.clip(x, y, w, h, 0, 0);

            for (int tile = first; tile <= last; tile++)
            {
                double tileStart = tile * (double) DETAILED_TILE_WIDTH / tilePps;

                if (tileStart >= this.duration || tileStart >= endTime)
                {
                    break;
                }

                Texture texture = this.getDetailedTile(ppsQuarter, tileHeight, tile);
                float drawX = (float) (x + (tileStart - startTime) * pixelsPerScreenSecond);
                float drawW = (float) (DETAILED_TILE_WIDTH * pixelsPerScreenSecond / tilePps);

                batcher.texturedBox(texture, color, drawX, y, drawW, h, 0, 0, DETAILED_TILE_WIDTH, tileHeight);
            }

            batcher.unclip(0, 0);

            return;
        }

        /* Fallback direct pass (also preserves Batcher2D.box targets for addon mixins such as RefreshedUI). */
        double secondsPerPixel = (endTime - (double) startTime) / w;
        int first = (int) Math.max(0L, -(long) x);
        int last = (int) Math.min(w, (long) MinecraftClient.getInstance().getWindow().getWidth() - x);
        float center = y + h / 2F;
        WaveformEnvelope.Range range = new WaveformEnvelope.Range();
        boolean wasBatching = batcher.isBatching();

        batcher.clip(x, y, w, h, 0, 0);
        batcher.beginBatch();

        for (int i = first; i < last; i++)
        {
            double from = startTime + i * secondsPerPixel;
            double to = from + secondsPerPixel;

            if (to <= 0 || from >= this.duration)
            {
                continue;
            }

            this.envelope.sample(from, to, range);
            int columnColor = color;

            if (this.colorCodes != null)
            {
                for (ColorCode code : this.colorCodes)
                {
                    if (code.isInside((float) Math.max(0, from)))
                    {
                        columnColor = tint(code.color | 0xff000000, color);
                        batcher.gradientVBox(x + i, y, x + i + 1, y + h,
                            Colors.mulA(columnColor, 0.125F), Colors.mulA(columnColor, 0.375F));
                        break;
                    }
                }
            }

            if (this.cues != null)
            {
                for (float cue : this.cues)
                {
                    if (cue >= from && cue < to)
                    {
                        batcher.box(x + i, y, x + i + 1, y + h, tint(Colors.ACTIVE | Colors.A75, color));
                        break;
                    }
                }
            }

            float peak = range.maximum * h / 2F;
            float average = range.average * h / 2F;

            batcher.box(x + i, center - peak, x + i + 1, center + peak, columnColor);
            batcher.box(x + i, center - average, x + i + 1, center + average, Colors.mulRGB(columnColor, 0.8F));
        }

        batcher.unclip(0, 0);

        if (!wasBatching)
        {
            batcher.endBatch();
        }
    }

    private Texture getDetailedTile(long ppsQuarter, int tileHeight, int index)
    {
        DetailedTileKey key = new DetailedTileKey(ppsQuarter, tileHeight, index);
        Texture cached = this.detailedTiles.get(key);

        if (cached != null)
        {
            return cached;
        }

        double tilePps = ppsQuarter / 4D;
        double secondsPerPixel = 1D / tilePps;
        Pixels pixels = Pixels.fromSize(DETAILED_TILE_WIDTH, tileHeight);
        WaveformEnvelope.Range range = new WaveformEnvelope.Range();
        Color background = new Color();
        long firstColumn = (long) index * DETAILED_TILE_WIDTH;

        for (int px = 0; px < DETAILED_TILE_WIDTH; px++)
        {
            double from = (firstColumn + px) * secondsPerPixel;

            if (from >= this.duration)
            {
                break;
            }

            this.envelope.sample(from, from + secondsPerPixel, range);
            int columnColor = Colors.WHITE;

            if (this.colorCodes != null)
            {
                for (ColorCode code : this.colorCodes)
                {
                    if (code.isInside((float) from))
                    {
                        columnColor = code.color | 0xff000000;
                        background.set(columnColor);

                        for (int row = 0; row < tileHeight; row++)
                        {
                            background.a = 0.125F + 0.25F * row / tileHeight;
                            pixels.setColor(px, row, background);
                        }

                        break;
                    }
                }
            }

            if (this.cues != null)
            {
                for (float cue : this.cues)
                {
                    if (cue >= from && cue < from + secondsPerPixel)
                    {
                        pixels.drawRect(px, 0, 1, tileHeight, Colors.ACTIVE | Colors.A75);
                        break;
                    }
                }
            }

            int peak = range.maximum > 0 ? Math.max(1, Math.round(range.maximum * tileHeight)) : 0;
            int average = range.average > 0 ? Math.max(1, Math.round(range.average * tileHeight)) : 0;

            if (peak > 0) pixels.drawRect(px, (tileHeight - peak) / 2, 1, peak, columnColor);
            if (average > 0) pixels.drawRect(px, (tileHeight - average) / 2, 1, average, Colors.mulRGB(columnColor, 0.8F));
        }

        pixels.rewindBuffer();
        Texture texture = Texture.textureFromPixels(pixels, GL11.GL_NEAREST);
        pixels.delete();
        this.detailedTiles.put(key, texture);

        if (this.detailedTiles.size() > DETAILED_TILE_LIMIT)
        {
            DetailedTileKey oldest = this.detailedTiles.keySet().iterator().next();
            this.detailedTiles.remove(oldest).delete();
        }

        return texture;
    }

    private static int tint(int a, int b)
    {
        int result = 0;

        for (int shift = 0; shift <= 24; shift += 8)
        {
            result |= (((a >>> shift) & 255) * ((b >>> shift) & 255) / 255) << shift;
        }

        return result;
    }
}
