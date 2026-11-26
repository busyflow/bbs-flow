package mchorse.bbs_mod.forms;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.FramebufferForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.VideoForm;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.graphics.Renderbuffer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.texture.TextureFormat;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Offscreen FBO thumbnail cache for 3D UI form previews (Replay List, Body Parts,
 * Preview HUD, Form Palettes). Renders each form once into an RGBA8 texture and
 * automatically re-bakes whenever the form, its model/skin, armor, or any body part changes.
 */
public final class FormThumbnailCache
{
    private static final int MAX_ENTRIES = 512;
    private static final int MIN_LOGICAL_SIZE = 8;
    private static final int MAX_LOGICAL_SIZE = 256;
    private static final int MAX_TEXTURE_SIZE = 256;

    /** True while baking a thumbnail into our offscreen FBO so models use the fixed 3/4 view angle. */
    public static boolean capturingThumbnail;

    private static final Map<Integer, Entry> CACHE = new LinkedHashMap<>(256, 0.75F, true);
    private static final int[] VIEWPORT_SAVE = new int[4];
    private static final int[] SCISSOR_SAVE = new int[4];
    private static final float[] CLEAR_COLOR_SAVE = new float[4];

    private static int fboId = -1;
    private static Renderbuffer depthBuffer;
    private static int depthW;
    private static int depthH;

    private FormThumbnailCache()
    {}

    public static boolean renderCached(Form form, FormRenderer<?> renderer, UIContext context, int x1, int y1, int x2, int y2)
    {
        return renderCachedClipped(form, renderer, context, x1, y1, x2, y2, y1, y2);
    }

    /**
     * Same as {@link #renderCached}, vertically clipped via texture UV sub-rectangle to {@code [clipY1, clipY2]}
     * so list rows can draw cropped thumbnails without GL scissor flushes.
     */
    public static boolean renderCachedClipped(Form form, FormRenderer<?> renderer, UIContext context, int x1, int y1, int x2, int y2, int clipY1, int clipY2)
    {
        if (capturingThumbnail || form == null || renderer == null)
        {
            return false;
        }

        if (form instanceof VideoForm || form instanceof FramebufferForm)
        {
            return false;
        }

        int w = x2 - x1;
        int h = y2 - y1;

        if (w < MIN_LOGICAL_SIZE || h < MIN_LOGICAL_SIZE || w > MAX_LOGICAL_SIZE || h > MAX_LOGICAL_SIZE)
        {
            return false;
        }

        /* Keep interactive hover-spin when the user actively hovers over a single thumbnail cell. */
        if (!BBSSettings.freezeModels.get()
            && context.mouseX >= x1 && context.mouseX < x2
            && context.mouseY >= Math.max(y1, clipY1) && context.mouseY < Math.min(y2, clipY2))
        {
            return false;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        float scale = mc != null && mc.getWindow() != null ? (float) mc.getWindow().getScaleFactor() : 1F;
        int texW = Math.max(16, Math.min(MAX_TEXTURE_SIZE, Math.round(w * scale)));
        int texH = Math.max(16, Math.min(MAX_TEXTURE_SIZE, Math.round(h * scale)));

        int key = System.identityHashCode(form);
        long signature = computeSignature(form, texW, texH);
        boolean ready = isAssetsReady(form);

        Entry entry = CACHE.get(key);

        if (entry != null && entry.form != form)
        {
            /* Rare identityHashCode collision: evict and rebake cleanly. */
            entry.delete();
            CACHE.remove(key);
            entry = null;
        }

        boolean needsBake = entry == null
            || entry.signature != signature
            || entry.texW != texW
            || entry.texH != texH
            || (!entry.ready && ready);

        if (needsBake)
        {
            if (entry == null)
            {
                entry = new Entry(form);
                CACHE.put(key, entry);
                evictOldest();
            }

            if (!bake(entry, renderer, context, w, h, texW, texH, signature, ready))
            {
                return false;
            }
        }

        if (entry.texture == null || !entry.texture.isValid())
        {
            return false;
        }

        int drawY1 = Math.max(y1, clipY1);
        int drawY2 = Math.min(y2, clipY2);

        if (drawY2 <= drawY1)
        {
            return true;
        }

        /* OpenGL FBO color attachments are bottom-up, so flip V when drawing into top-down UI. */
        float topFrac = (drawY1 - y1) / (float) h;
        float bottomFrac = (drawY2 - y1) / (float) h;
        float v1 = texH * (1F - topFrac);
        float v2 = texH * (1F - bottomFrac);

        context.batcher.texturedBox(entry.texture, Colors.WHITE, x1, drawY1, w, drawY2 - drawY1, 0F, v1, texW, v2, texW, texH);

        return true;
    }

    public static void clear()
    {
        for (Entry entry : CACHE.values())
        {
            entry.delete();
        }

        CACHE.clear();

        if (depthBuffer != null)
        {
            depthBuffer.delete();
            depthBuffer = null;
            depthW = 0;
            depthH = 0;
        }

        if (fboId > 0)
        {
            GL30.glDeleteFramebuffers(fboId);
            fboId = -1;
        }
    }

    private static void evictOldest()
    {
        while (CACHE.size() > MAX_ENTRIES)
        {
            Iterator<Map.Entry<Integer, Entry>> it = CACHE.entrySet().iterator();

            if (!it.hasNext())
            {
                break;
            }

            Map.Entry<Integer, Entry> oldest = it.next();
            oldest.getValue().delete();
            it.remove();
        }
    }

    private static boolean bake(Entry entry, FormRenderer<?> renderer, UIContext context, int w, int h, int texW, int texH, long signature, boolean ready)
    {
        context.batcher.flush();

        if (fboId <= 0)
        {
            fboId = GL30.glGenFramebuffers();
        }

        if (entry.texture == null || !entry.texture.isValid() || entry.texW != texW || entry.texH != texH)
        {
            if (entry.texture != null)
            {
                entry.texture.delete();
            }

            Texture tex = new Texture();
            tex.setFormat(TextureFormat.RGBA_U8);
            tex.setFilter(GL11.GL_LINEAR);
            tex.setWrap(GL12.GL_CLAMP_TO_EDGE);
            tex.setSize(texW, texH);
            tex.unbind();

            entry.texture = tex;
            entry.texW = texW;
            entry.texH = texH;
        }

        if (depthBuffer == null || depthW != texW || depthH != texH)
        {
            if (depthBuffer == null)
            {
                depthBuffer = new Renderbuffer();
            }

            depthBuffer.resize(texW, texH);
            depthBuffer.unbind();
            depthW = texW;
            depthH = texH;
        }

        int prevDrawFbo = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int prevReadFbo = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, VIEWPORT_SAVE);
        boolean prevScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, SCISSOR_SAVE);
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, CLEAR_COLOR_SAVE);
        boolean prevDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        int prevDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean prevDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

        /* Save global render matrices in local variables rather than MatrixStackUtils.cacheMatrices()
         * because MatrixStackUtils uses single static fields that any inner form renderer calling
         * cacheMatrices()/restoreMatrices() would overwrite with our small thumbnail ortho matrix. */
        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorter savedSorting = RenderSystem.getVertexSorting();
        Matrix4f savedMV = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix3f savedInverse = new Matrix3f(RenderSystem.getInverseViewRotationMatrix());

        MatrixStack mvStack = RenderSystem.getModelViewStack();
        mvStack.push();
        mvStack.loadIdentity();
        RenderSystem.applyModelViewMatrix();

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, entry.texture.id, 0);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, depthBuffer.target, GL30.GL_RENDERBUFFER, depthBuffer.id);

        boolean complete = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;

        if (!complete)
        {
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDrawFbo);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFbo);
            restoreRenderMatrices(savedProjection, savedSorting, savedMV, savedInverse);
            return false;
        }

        MatrixStack matrices = context.batcher.getContext().getMatrices();
        int savedMouseX = context.mouseX;
        int savedMouseY = context.mouseY;

        capturingThumbnail = true;

        try
        {
            RenderSystem.disableScissor();
            GL11.glViewport(0, 0, texW, texH);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.clearColor(0F, 0F, 0F, 0F);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);

            /* ModelView is at identity (z = 0), so cover [-1000, 1000] around z = 40 where UI form previews render. */
            Matrix4f ortho = new Matrix4f().setOrtho(0F, (float) w, (float) h, 0F, -1000F, 1000F);
            RenderSystem.setProjectionMatrix(ortho, VertexSorter.BY_Z);

            matrices.push();
            matrices.loadIdentity();

            context.mouseX = w / 2;
            context.mouseY = h / 2;

            renderer.renderPreview(context, 0, 0, w, h);
            context.batcher.flush();

            matrices.pop();

            entry.signature = signature;
            entry.ready = ready;

            return true;
        }
        finally
        {
            capturingThumbnail = false;
            context.mouseX = savedMouseX;
            context.mouseY = savedMouseY;

            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDrawFbo);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFbo);
            GL11.glViewport(VIEWPORT_SAVE[0], VIEWPORT_SAVE[1], VIEWPORT_SAVE[2], VIEWPORT_SAVE[3]);
            RenderSystem.clearColor(CLEAR_COLOR_SAVE[0], CLEAR_COLOR_SAVE[1], CLEAR_COLOR_SAVE[2], CLEAR_COLOR_SAVE[3]);

            if (prevScissor)
            {
                RenderSystem.enableScissor(SCISSOR_SAVE[0], SCISSOR_SAVE[1], SCISSOR_SAVE[2], SCISSOR_SAVE[3]);
            }
            else
            {
                RenderSystem.disableScissor();
            }

            RenderSystem.depthMask(prevDepthMask);
            RenderSystem.depthFunc(prevDepthFunc);

            if (prevDepthTest)
            {
                RenderSystem.enableDepthTest();
            }
            else
            {
                RenderSystem.disableDepthTest();
            }

            restoreRenderMatrices(savedProjection, savedSorting, savedMV, savedInverse);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    private static void restoreRenderMatrices(Matrix4f projection, VertexSorter sorting, Matrix4f mv, Matrix3f inverse)
    {
        RenderSystem.setProjectionMatrix(projection, sorting);
        RenderSystem.setInverseViewRotationMatrix(inverse);

        MatrixStack mvStack = RenderSystem.getModelViewStack();
        mvStack.pop();
        mvStack.push();
        mvStack.loadIdentity();
        MatrixStackUtils.multiply(mvStack, mv);
        RenderSystem.applyModelViewMatrix();
        mvStack.pop();
    }

    private static boolean isAssetsReady(Form form)
    {
        if (form instanceof ModelForm modelForm)
        {
            ModelInstance model = ModelFormRenderer.getModel(modelForm);

            if (model == null || model.model == null)
            {
                return false;
            }

            Link link = modelForm.texture.get();
            Link texture = link == null ? model.getTexture() : link;

            if (texture != null)
            {
                Texture tex = BBSModClient.getTextures().getTexture(texture);

                if (tex == null || !tex.isValid() || tex.width <= 0)
                {
                    return false;
                }
            }
        }

        for (BodyPart part : form.parts.getAllTyped())
        {
            Form child = part.getForm();

            if (child != null && !isAssetsReady(child))
            {
                return false;
            }
        }

        return true;
    }

    private static long computeSignature(Form form, int texW, int texH)
    {
        long h = 1469598103934665603L;
        h = mix(h, texW);
        h = mix(h, texH);
        h = mix(h, BBSSettings.lightSurfaces() ? 1 : 0);
        h = hashForm(h, form, 0);
        return h;
    }

    private static long hashForm(long h, Form form, int depth)
    {
        if (form == null || depth > 8)
        {
            return h;
        }

        h = mix(h, form.getPoseVersion());
        h = mix(h, form.visible.get() ? 1 : 0);

        if (form instanceof ModelForm modelForm)
        {
            ModelInstance model = ModelFormRenderer.getModel(modelForm);
            h = mix(h, System.identityHashCode(model));

            Link link = modelForm.texture.get();
            if (link != null)
            {
                h = mix(h, link.hashCode());
            }
        }

        for (BodyPart part : form.parts.getAllTyped())
        {
            h = mix(h, part.bone.get().hashCode());
            Transform t = part.transform.get();
            h = mix(h, Float.floatToIntBits(t.translate.x));
            h = mix(h, Float.floatToIntBits(t.translate.y));
            h = mix(h, Float.floatToIntBits(t.translate.z));
            h = mix(h, Float.floatToIntBits(t.scale.x));
            h = mix(h, Float.floatToIntBits(t.scale.y));
            h = mix(h, Float.floatToIntBits(t.scale.z));
            h = hashForm(h, part.getForm(), depth + 1);
        }

        return h;
    }

    private static long mix(long h, long v)
    {
        h ^= v;
        h *= 1099511628211L;
        return h;
    }

    private static final class Entry
    {
        final Form form;
        Texture texture;
        int texW;
        int texH;
        long signature;
        boolean ready;

        Entry(Form form)
        {
            this.form = form;
        }

        void delete()
        {
            if (this.texture != null)
            {
                this.texture.delete();
                this.texture = null;
            }
        }
    }
}
