package mchorse.bbs_mod.ui.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.Framebuffer;
import mchorse.bbs_mod.graphics.Renderbuffer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class StencilFormFramebuffer
{
    private Framebuffer framebuffer;

    private int index;
    private Map<Integer, Pair<Form, String>> indexMap = new HashMap<>();

    /** Reused readback buffer for the tolerance region pick (grows as needed). */
    private ByteBuffer pickBuffer;

    /** Async PBO readback for freshly-rendered stencil passes (avoids synchronous glReadPixels stalls). */
    private int asyncPbo = -1;
    private int asyncPboCapacity;
    private boolean asyncPending;
    private int asyncX0, asyncY0, asyncW, asyncH;
    private int asyncX, asyncY, asyncRadius, asyncHandleMax;

    public Framebuffer getFramebuffer()
    {
        return this.framebuffer;
    }

    /**
     * Draw the stencil buffer over the area with the picked form lit up: the preview shader is
     * told which index it is looking for and what colour to paint it, and the buffer is drawn
     * flipped, the way it was rendered.
     */
    public void renderPreview(UIContext context, Area area)
    {
        this.renderPreview(context, area, this.getIndex());
    }

    private static ShaderProgram cachedPreviewShader;
    private static GlUniform cachedPreviewTarget;
    private static GlUniform cachedPreviewHighlight;

    /** Same, lighting up {@code index} instead of what is under the cursor — a host pointing at a bone from a list. */
    public void renderPreview(UIContext context, Area area, int index)
    {
        Texture texture = this.getFramebuffer().getMainTexture();
        ShaderProgram program = BBSShaders.getPickerPreviewProgram();

        if (program != cachedPreviewShader)
        {
            cachedPreviewShader = program;
            cachedPreviewTarget = program == null ? null : program.getUniform("Target");
            cachedPreviewHighlight = program == null ? null : program.getUniform("HighlightColor");
        }

        if (cachedPreviewTarget != null)
        {
            cachedPreviewTarget.set(index);
        }

        if (cachedPreviewHighlight != null)
        {
            int color = BBSSettings.stencilHighlightColor.get();

            cachedPreviewHighlight.set(Colors.getR(color), Colors.getG(color), Colors.getB(color), Colors.getA(color));
        }

        RenderSystem.enableBlend();
        context.batcher.texturedBox(BBSShaders::getPickerPreviewProgram, texture.id, Colors.WHITE,
            area.x, area.y, area.w, area.h,
            0, texture.height, texture.width, 0, texture.width, texture.height);
    }

    public int getIndex()
    {
        return this.index;
    }

    public Pair<Form, String> getPicked()
    {
        return this.indexMap.get(this.index);
    }

    /** The id the last pass drew {@code bone} of {@code form} with, or 0 when it wasn't drawn. */
    public int indexOf(Form form, String bone)
    {
        for (Map.Entry<Integer, Pair<Form, String>> entry : this.indexMap.entrySet())
        {
            Pair<Form, String> pair = entry.getValue();

            if (pair.a == form && pair.b.equals(bone))
            {
                return entry.getKey();
            }
        }

        return 0;
    }

    public void setup(Link id)
    {
        if (this.framebuffer != null)
        {
            return;
        }

        this.framebuffer = BBSModClient.getFramebuffers().getFramebuffer(id, (framebuffer) ->
        {
            Texture texture = new Texture();

            texture.setSize(2, 2);
            texture.setFilter(GL11.GL_NEAREST);
            texture.setWrap(GL13.GL_CLAMP_TO_EDGE);

            Renderbuffer renderbuffer = new Renderbuffer();

            renderbuffer.resize(2, 2);

            framebuffer.deleteTextures().attach(texture, GL30.GL_COLOR_ATTACHMENT0);
            framebuffer.attach(renderbuffer);
            framebuffer.unbind();
        });
    }

    public void resizeGUI(int w, int h)
    {
        float scale = BBSModClient.getGUIScale();

        this.resize(Math.round(w * scale), Math.round(h * scale));
    }

    public void resize(int w, int h)
    {
        if (this.framebuffer != null)
        {
            this.framebuffer.resize(w, h);
        }
    }

    public void apply()
    {
        this.framebuffer.applyClear();
    }

    public void pickGUI(UIContext context, Area area)
    {
        this.pickGUI(context.mouseX - area.x, area.h - context.mouseY + area.y);
    }

    /** {@link #pickGUI(UIContext, Area)} with a gizmo-handle hover tolerance
     *  ({@code radius} in GUI pixels; ids in {@code [1, handleMax]} grab from nearby). */
    public void pickGUI(UIContext context, Area area, int radius, int handleMax)
    {
        float scale = BBSModClient.getGUIScale();
        int x = Math.round((context.mouseX - area.x) * scale);
        int y = Math.round((area.h - context.mouseY + area.y) * scale);

        this.pick(x, y, Math.round(radius * scale), handleMax);
    }

    public void pickGUI(int x, int y)
    {
        float scale = BBSModClient.getGUIScale();

        this.pick(Math.round(x * scale), Math.round(y * scale));
    }

    public void pick(int x, int y)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            ByteBuffer bytes = stack.malloc(4);

            GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, bytes);

            int r = bytes.get(0) & 0xFF;
            int g = bytes.get(1) & 0xFF;
            int b = bytes.get(2) & 0xFF;
            int a = bytes.get(3) & 0xFF;

            this.index = a < 1 ? 0 : r | (g << 8) | (b << 16);
        }
    }

    /**
     * Pick directly from the existing framebuffer without re-rendering the scene.
     */
    public void repickFromBuffer(int x, int y, int radius, int handleMax)
    {
        if (this.framebuffer == null)
        {
            return;
        }

        int prevRead = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, this.framebuffer.id);

        try
        {
            this.pick(x, y, radius, handleMax);
        }
        finally
        {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
        }
    }

    /**
     * Non-blocking variant of {@link #repickFromBuffer} for hover preview: queues an async PBO
     * readback from the cached stencil FBO without stalling the GPU pipeline with synchronous glReadPixels.
     */
    public void repickFromBufferAsync(int x, int y, int radius, int handleMax)
    {
        if (this.framebuffer == null)
        {
            return;
        }

        int mainFbo = net.minecraft.client.MinecraftClient.getInstance().getFramebuffer().fbo;
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, this.framebuffer.id);

        try
        {
            this.pickAsync(x, y, radius, handleMax);
        }
        finally
        {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mainFbo);
        }
    }

    /**
     * Pick, but let ids in {@code [1, handleMax]} (the gizmo's handles) grab from
     * nearby: search a {@code radius}-pixel disc around the cursor and take the
     * <em>nearest</em> such id, so a thin line captures when the cursor is beside
     * it — the way a typical 3D gizmo hovers. Anything outside that id range (form
     * parts / bones) still resolves at the exact pixel under the cursor, so only
     * the handles get the tolerance. {@code radius} is in framebuffer pixels;
     * {@code radius <= 0} falls back to the plain single-pixel {@link #pick}.
     */
    public void pick(int x, int y, int radius, int handleMax)
    {
        if (radius <= 0 || this.framebuffer == null)
        {
            this.pick(x, y);

            return;
        }

        Texture texture = this.framebuffer.getMainTexture();
        int x0 = Math.max(0, x - radius);
        int y0 = Math.max(0, y - radius);
        int x1 = Math.min(texture.width - 1, x + radius);
        int y1 = Math.min(texture.height - 1, y + radius);
        int w = x1 - x0 + 1;
        int h = y1 - y0 + 1;

        if (w <= 0 || h <= 0)
        {
            this.index = 0;

            return;
        }

        int needed = w * h * 4;

        /* UNSIGNED_BYTE readback uses 4x less bandwidth and avoids slow GPU RGBA8->float conversion. */
        if (this.pickBuffer == null || this.pickBuffer.capacity() < needed)
        {
            this.pickBuffer = BufferUtils.createByteBuffer(needed);
        }

        ByteBuffer bytes = this.pickBuffer;

        bytes.clear();
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadPixels(x0, y0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, bytes);

        this.asyncPending = false;
        this.resolvePickFromBuffer(bytes, x0, y0, w, h, x, y, radius, handleMax);
    }

    /**
     * Queue a non-blocking PBO DMA readback right after rendering the stencil pass so the GPU
     * never stalls the CPU waiting for the render to finish. Harvested on the next frame via
     * {@link #finishAsyncPick()}.
     */
    public void pickAsync(int x, int y, int radius, int handleMax)
    {
        if (this.framebuffer == null)
        {
            return;
        }

        /* Harvest any completed previous pass before reusing the PBO. */
        if (this.asyncPending)
        {
            this.finishAsyncPick();
        }

        Texture texture = this.framebuffer.getMainTexture();
        int r = Math.max(0, radius);
        int x0 = Math.max(0, x - r);
        int y0 = Math.max(0, y - r);
        int x1 = Math.min(texture.width - 1, x + r);
        int y1 = Math.min(texture.height - 1, y + r);
        int w = x1 - x0 + 1;
        int h = y1 - y0 + 1;

        if (w <= 0 || h <= 0)
        {
            this.index = 0;
            this.asyncPending = false;

            return;
        }

        int needed = w * h * 4;

        if (this.asyncPbo <= 0)
        {
            this.asyncPbo = GL15.glGenBuffers();
        }

        try
        {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, this.asyncPbo);

            if (this.asyncPboCapacity < needed)
            {
                GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, needed, GL15.GL_STREAM_READ);
                this.asyncPboCapacity = needed;
            }

            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glReadPixels(x0, y0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);

            this.asyncX0 = x0;
            this.asyncY0 = y0;
            this.asyncW = w;
            this.asyncH = h;
            this.asyncX = x;
            this.asyncY = y;
            this.asyncRadius = r;
            this.asyncHandleMax = handleMax;
            this.asyncPending = true;
        }
        finally
        {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        }
    }

    /**
     * Harvest the result of the previous frame's {@link #pickAsync} call (already finished on the GPU).
     */
    public boolean finishAsyncPick()
    {
        if (!this.asyncPending || this.asyncPbo <= 0)
        {
            return false;
        }

        this.asyncPending = false;

        int needed = this.asyncW * this.asyncH * 4;

        if (this.pickBuffer == null || this.pickBuffer.capacity() < needed)
        {
            this.pickBuffer = BufferUtils.createByteBuffer(needed);
        }

        ByteBuffer bytes = this.pickBuffer;
        bytes.clear();
        bytes.limit(needed);

        try
        {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, this.asyncPbo);
            GL15.glGetBufferSubData(GL21.GL_PIXEL_PACK_BUFFER, 0L, bytes);
        }
        finally
        {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        }

        this.resolvePickFromBuffer(bytes, this.asyncX0, this.asyncY0, this.asyncW, this.asyncH, this.asyncX, this.asyncY, this.asyncRadius, this.asyncHandleMax);

        return true;
    }

    private void resolvePickFromBuffer(ByteBuffer bytes, int x0, int y0, int w, int h, int x, int y, int radius, int handleMax)
    {
        int centerId = 0;
        int nearestHandle = 0;
        long nearestDist = Long.MAX_VALUE;
        long radiusSq = (long) radius * radius;

        for (int py = 0; py < h; py++)
        {
            for (int px = 0; px < w; px++)
            {
                int base = (py * w + px) * 4;

                if ((bytes.get(base + 3) & 0xFF) < 1)
                {
                    continue;
                }

                int id = (bytes.get(base) & 0xFF)
                    | ((bytes.get(base + 1) & 0xFF) << 8)
                    | ((bytes.get(base + 2) & 0xFF) << 16);
                int fx = x0 + px;
                int fy = y0 + py;

                if (fx == x && fy == y)
                {
                    centerId = id;
                }

                if (radius > 0 && id >= 1 && id <= handleMax)
                {
                    long dx = fx - x;
                    long dy = fy - y;
                    long dist = dx * dx + dy * dy;

                    if (dist <= radiusSq && dist < nearestDist)
                    {
                        nearestDist = dist;
                        nearestHandle = id;
                    }
                }
            }
        }

        this.index = nearestHandle != 0 ? nearestHandle : centerId;
    }

    public void unbind(StencilMap map)
    {
        this.unbind();

        this.indexMap.clear();
        this.indexMap.putAll(map.indexMap);
    }

    public void unbind()
    {
        this.framebuffer.unbind();
    }

    public void clearPicking()
    {
        this.index = 0;
        this.asyncPending = false;
        this.indexMap.clear();
    }

    /** Nothing under the cursor, while what the pass drew stays known (for {@link #indexOf}). */
    public void clearIndex()
    {
        this.index = 0;
        this.asyncPending = false;
    }

    public boolean hasPicked()
    {
        return this.index > 0;
    }
}