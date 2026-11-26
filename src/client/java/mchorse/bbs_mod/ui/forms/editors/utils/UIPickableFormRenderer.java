package mchorse.bbs_mod.ui.forms.editors.utils;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.GizmoInteraction;
import mchorse.bbs_mod.ui.utils.GizmoViewport;
import mchorse.bbs_mod.ui.utils.OutlineMask;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.cubic.rig.RigMarkup;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.profiler.BBSProfiler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.lwjgl.opengl.GL11;

import java.util.function.Supplier;

public class UIPickableFormRenderer extends UIFormRenderer implements GizmoViewport
{
    public UIFormEditor formEditor;

    private boolean update;

    private StencilFormFramebuffer stencil = new StencilFormFramebuffer();
    private StencilMap stencilMap = new StencilMap();
    private RigMarkup.Capture rigMarkup = RigMarkup.Capture.EMPTY;

    private final GizmoInteraction gizmo = new GizmoInteraction(this);
    /** The edited bone's ring over the preview */
    private final OutlineMask outline = new OutlineMask();

    private IEntity target;
    private Supplier<Boolean> renderForm;

    /* Stencil pass cache: skip 3D scene re-render + readback when camera/pose/gizmo haven't moved. */
    private static final int CACHE_HEARTBEAT = 120;
    private final Matrix4f lastStencilView = new Matrix4f();
    private final Matrix4f lastStencilProj = new Matrix4f();
    private final Vector3d lastStencilEye = new Vector3d();
    private final Matrix4f lastStencilOrigin = new Matrix4f();
    private boolean lastStencilHasOrigin;
    private Form lastStencilForm;
    private int lastStencilPoseVersion;
    private int lastStencilEntityAge;
    private boolean lastStencilHideGizmo;
    private Gizmo.HandleMask lastStencilGizmoMask;
    private TransformSpace lastStencilGizmoSpace;
    private int lastStencilMouseX = Integer.MIN_VALUE;
    private int lastStencilMouseY;
    private boolean stencilSceneValid;
    private int framesSinceStencil;

    /* Outline mask cache: skip full-screen 3D form re-render while view & pose are unchanged. */
    private final Matrix4f lastOutlineView = new Matrix4f();
    private final Matrix4f lastOutlineProj = new Matrix4f();
    private final Vector3d lastOutlineEye = new Vector3d();
    private Form lastOutlineForm;
    private int lastOutlinePoseVersion;
    private int lastOutlineEntityAge;
    private int lastOutlineW;
    private int lastOutlineH;
    private int framesSinceOutline;

    public UIPickableFormRenderer(UIFormEditor formEditor)
    {
        this.formEditor = formEditor;
    }

    public void updatable()
    {
        this.update = true;
    }

    /** The rig's handles the last render of the form captured, for the markup. */
    public RigMarkup.Capture getRigMarkup()
    {
        return this.rigMarkup;
    }

    public StencilFormFramebuffer getStencil()
    {
        return this.stencil;
    }

    public void setRenderForm(Supplier<Boolean> renderForm)
    {
        this.renderForm = renderForm;
    }

    public IEntity getTargetEntity()
    {
        return this.target == null ? this.entity : this.target;
    }

    public void setTarget(IEntity target)
    {
        this.target = target;
    }

    private void ensureFramebuffer()
    {
        this.stencil.setup(Link.bbs("stencil_form"));

        Texture mainTexture = this.stencil.getFramebuffer().getMainTexture();
        int w = Math.max(2, Math.min(960, this.area.w));
        int h = Math.max(2, Math.min(540, this.area.h));

        if (mainTexture.width != w || mainTexture.height != h)
        {
            this.stencil.resize(w, h);
            this.stencilSceneValid = false;
        }
    }

    @Override
    public void resize()
    {
        super.resize();

        this.ensureFramebuffer();
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.formEditor.clickViewport(context, this.stencil))
        {
            return true;
        }

        return super.subMouseClicked(context);
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        if (this.formEditor.shapeControllerOverlay.release()) return true;
        if (this.gizmo.mouseReleased(context))
        {
            return true;
        }

        return super.subMouseReleased(context);
    }

    public GizmoInteraction getGizmoInteraction()
    {
        return this.gizmo;
    }

    @Override
    public StencilFormFramebuffer getGizmoStencil()
    {
        return this.stencil;
    }

    @Override
    public Matrix4f getGizmoProjection()
    {
        return this.camera.projection;
    }

    @Override
    public Area getGizmoArea()
    {
        return this.area;
    }

    @Override
    public boolean startGizmo(UIContext context, int stencilIndex)
    {
        return this.formEditor.startGizmo(context, stencilIndex);
    }

    @Override
    public void pickGizmoForm(UIContext context, Form form, String bone)
    {
        this.formEditor.pickFormFromRenderer(new Pair<>(form, bone));
    }

    @Override
    protected void renderUserModel(UIContext context)
    {
        if (this.form == null)
        {
            return;
        }

        this.formEditor.preFormRender(context, this.form);

        FormRenderingContext formContext = new FormRenderingContext()
            .set(FormRenderType.PREVIEW, this.target == null ? this.entity : this.target, context.batcher.getContext().getMatrices(), LightmapTextureManager.pack(15, 15), OverlayTexture.DEFAULT_UV, context.getTransition())
            .camera(this.camera)
            .modelRenderer(context.getTick());

        if (this.renderForm == null || this.renderForm.get())
        {
            /* The form editor viewport gets the same deferred translucency as the world: the
             * form's semi-transparent pixels draw after all its opaque ones, sorted, without
             * hiding bones behind them. The rig's handles are captured on the way for the markup. */
            RigMarkup.Capture previous = RigMarkup.open();

            try
            {
                FormTranslucentQueue.begin();
                FormUtilsClient.render(this.form, formContext);
                FormTranslucentQueue.flush();
            }
            finally
            {
                this.rigMarkup = RigMarkup.close(previous);
            }

            if (this.form.hitbox.get())
            {
                this.renderFormHitbox(context);
            }
        }

        /* Keep the gizmo the same on-screen size as in the film preview (see
         * Gizmo#setViewportHeight); set before both the visual (renderAxes) and the
         * stencil pass below so the drawn handles and their pick hitbox match. */
        Gizmo.INSTANCE.setViewportHeight(this.area.h);

        this.renderOutlineMask(formContext);
        this.renderAxes(context);

        if (this.area.isInside(context) && !this.isMoving() && Gizmo.INSTANCE.getTrackedGesture() == null)
        {
            this.ensureFramebuffer();

            Matrix4f originMatrix = this.formEditor.getOrigin(context.getTransition());
            boolean hideGizmo = UIBaseMenu.isHideGizmoHeld();
            Gizmo.HandleMask gizmoMask = this.formEditor.getGizmoHandleMask();
            TransformSpace gizmoSpace = this.formEditor.getGizmoSpace();
            boolean sceneChanged = this.needsStencilRedraw(originMatrix, hideGizmo, gizmoMask, gizmoSpace);
            boolean mouseMoved = context.mouseX != this.lastStencilMouseX || context.mouseY != this.lastStencilMouseY;

            if (!sceneChanged)
            {
                this.stencil.finishAsyncPick();

                if (mouseMoved && this.stencilSceneValid && this.stencil.getFramebuffer() != null)
                {
                    this.lastStencilMouseX = context.mouseX;
                    this.lastStencilMouseY = context.mouseY;

                    Texture mainTexture = this.stencil.getFramebuffer().getMainTexture();
                    int x = (int) ((context.mouseX - this.area.x) / (float) this.area.w * mainTexture.width);
                    int y = (int) ((1F - (context.mouseY - this.area.y) / (float) this.area.h) * mainTexture.height);
                    int radius = Math.round(BBSSettings.gizmoHoverTolerance.get() * mainTexture.width / (float) this.area.w);

                    this.stencil.repickFromBuffer(x, y, radius, Gizmo.STENCIL_MAX);
                }
            }
            else
            {
                this.lastStencilMouseX = context.mouseX;
                this.lastStencilMouseY = context.mouseY;

                BBSProfiler.count(BBSProfiler.Section.STENCIL_PASS);
                BBSProfiler.begin(BBSProfiler.Timer.STENCIL_PASS);

                GlStateManager._disableScissorTest();

                this.stencilMap.setup();
                this.stencil.apply();

                FormUtilsClient.render(this.form, formContext.stencilMap(this.stencilMap));
                formContext.stencilMap(null);

                MatrixStack stack = context.render.batcher.getContext().getMatrices();

                stack.push();

                if (originMatrix != null)
                {
                    MatrixStackUtils.multiply(stack, MatrixStackUtils.stripScale(originMatrix));
                }

                /* Reorient the pick stencil into the active space to match the visual
                 * (below), so hovering a ring lands where it's drawn. */
                Gizmo.INSTANCE.reorientForSpace(stack, gizmoSpace, this.camera.view, this.getSceneAxes());

                /* Skip the gizmo's pick stencil while the hide-gizmo key is held, so its handles can't be
                 * clicked when hidden. Form-part picking (the stencil rendered above) is left intact, and
                 * the F8 axes toggle is untouched here on purpose. */
                if (!hideGizmo)
                {
                    Gizmo.INSTANCE.renderStencil(context.batcher.getContext().getMatrices(), gizmoMask);
                }

                stack.pop();

                Texture mainTexture = this.stencil.getFramebuffer().getMainTexture();
                int x = (int) ((context.mouseX - this.area.x) / (float) this.area.w * mainTexture.width);
                int y = (int) ((1F - (context.mouseY - this.area.y) / (float) this.area.h) * mainTexture.height);
                int radius = Math.round(BBSSettings.gizmoHoverTolerance.get() * mainTexture.width / (float) this.area.w);

                this.stencil.pickAsync(x, y, radius, Gizmo.STENCIL_MAX);
                this.stencil.unbind(this.stencilMap);
                this.stencilSceneValid = true;

                MinecraftClient.getInstance().getFramebuffer().beginWrite(true);

                GlStateManager._enableScissorTest();

                BBSProfiler.end(BBSProfiler.Timer.STENCIL_PASS);
            }
        }
        else if (this.isMoving() || Gizmo.INSTANCE.getTrackedGesture() != null)
        {
            this.stencil.clearIndex();
            this.stencilSceneValid = false;
            this.lastStencilMouseX = Integer.MIN_VALUE;
        }
        else
        {
            this.stencil.clearPicking();
            this.stencilSceneValid = false;
            this.lastStencilMouseX = Integer.MIN_VALUE;
        }

        this.gizmo.update(context);
    }

    private boolean needsStencilRedraw(Matrix4f originMatrix, boolean hideGizmo, Gizmo.HandleMask gizmoMask, TransformSpace gizmoSpace)
    {
        this.framesSinceStencil += 1;

        int poseVersion = this.form.getPoseVersion();
        int entityAge = this.getTargetEntity().getAge();
        boolean hasOrigin = originMatrix != null;

        boolean unchanged = this.stencilSceneValid
            && this.framesSinceStencil < CACHE_HEARTBEAT
            && this.form == this.lastStencilForm
            && poseVersion == this.lastStencilPoseVersion
            && entityAge == this.lastStencilEntityAge
            && hideGizmo == this.lastStencilHideGizmo
            && java.util.Objects.equals(gizmoMask, this.lastStencilGizmoMask)
            && gizmoSpace == this.lastStencilGizmoSpace
            && hasOrigin == this.lastStencilHasOrigin
            && (!hasOrigin || originMatrix.equals(this.lastStencilOrigin))
            && this.camera.view.equals(this.lastStencilView)
            && this.camera.projection.equals(this.lastStencilProj)
            && this.camera.position.equals(this.lastStencilEye);

        if (unchanged)
        {
            return false;
        }

        this.framesSinceStencil = 0;
        this.lastStencilForm = this.form;
        this.lastStencilPoseVersion = poseVersion;
        this.lastStencilEntityAge = entityAge;
        this.lastStencilHideGizmo = hideGizmo;
        this.lastStencilGizmoMask = gizmoMask;
        this.lastStencilGizmoSpace = gizmoSpace;
        this.lastStencilHasOrigin = hasOrigin;

        if (hasOrigin)
        {
            this.lastStencilOrigin.set(originMatrix);
        }

        this.lastStencilView.set(this.camera.view);
        this.lastStencilProj.set(this.camera.projection);
        this.lastStencilEye.set(this.camera.position);

        return true;
    }

    /**
     * The form again into the selection outline's mask while a bone is edited, the way the pick
     * pass draws it, so the bone's part can be ringed over the preview ({@link #render}).
     */
    private void renderOutlineMask(FormRenderingContext formContext)
    {
        if (!BBSSettings.editorSelectionOutline.get() || !UIBaseMenu.renderAxes || !OutlineMask.isAvailable()
            || (this.renderForm != null && !this.renderForm.get()) || !this.formEditor.hasPickedBone())
        {
            this.outline.invalidate();

            return;
        }

        float scale = BBSModClient.getGUIScale();
        int w = Math.max(1, Math.round(this.area.w * scale));
        int h = Math.max(1, Math.round(this.area.h * scale));
        int poseVersion = this.form.getPoseVersion();
        int entityAge = this.getTargetEntity().getAge();

        this.framesSinceOutline += 1;

        if (this.outline.isDrawn()
            && this.framesSinceOutline < CACHE_HEARTBEAT
            && this.form == this.lastOutlineForm
            && poseVersion == this.lastOutlinePoseVersion
            && entityAge == this.lastOutlineEntityAge
            && w == this.lastOutlineW
            && h == this.lastOutlineH
            && this.camera.view.equals(this.lastOutlineView)
            && this.camera.projection.equals(this.lastOutlineProj)
            && this.camera.position.equals(this.lastOutlineEye))
        {
            return;
        }

        BBSProfiler.begin(BBSProfiler.Timer.OUTLINE);

        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);

        /* The interface's scissor is in window pixels, not the mask's */
        GlStateManager._disableScissorTest();

        if (this.outline.begin(w, h))
        {
            try
            {
                FormUtilsClient.render(this.form, formContext.stencilMap(this.outline.stencilMap));
            }
            finally
            {
                this.outline.end();
                formContext.stencilMap(null);
            }

            this.framesSinceOutline = 0;
            this.lastOutlineForm = this.form;
            this.lastOutlinePoseVersion = poseVersion;
            this.lastOutlineEntityAge = entityAge;
            this.lastOutlineW = w;
            this.lastOutlineH = h;
            this.lastOutlineView.set(this.camera.view);
            this.lastOutlineProj.set(this.camera.projection);
            this.lastOutlineEye.set(this.camera.position);
        }

        if (scissor)
        {
            GlStateManager._enableScissorTest();
        }

        BBSProfiler.end(BBSProfiler.Timer.OUTLINE);
    }

    private void renderAxes(UIContext context)
    {
        Matrix4f matrix = this.formEditor.getOrigin(context.getTransition());
        MatrixStack stack = context.render.batcher.getContext().getMatrices();

        stack.push();

        if (matrix != null)
        {
            MatrixStackUtils.multiply(stack, MatrixStackUtils.stripScale(matrix));
        }

        /* Reorient the drawn gizmo into the active space (the preview's own scene
         * axes for GLOBAL, screen axes for VIEW); LOCAL leaves it on the bone's
         * own axes. Kept in lockstep with the pick stencil above. The scene axes
         * are the renderer's transform ({@link UIModelRenderer#getSceneAxes}):
         * identity in a plain preview, the model block's own rotation when the
         * block is edited immersively — GLOBAL must follow the container the
         * form is drawn inside, or it points off the scene the user sees. */
        Gizmo.INSTANCE.reorientForSpace(stack, this.formEditor.getGizmoSpace(), this.camera.view, this.getSceneAxes());

        /* Draw axes */
        if (UIBaseMenu.shouldRenderAxes())
        {
            RenderSystem.disableDepthTest();
            Gizmo.INSTANCE.render(stack, this.formEditor.getGizmoHandleMask());
            RenderSystem.enableDepthTest();
        }

        stack.pop();
    }

    private void renderFormHitbox(UIContext context)
    {
        float hitboxW = this.form.hitboxWidth.get();
        float hitboxH = this.form.hitboxHeight.get();
        float eyeHeight = hitboxH * this.form.hitboxEyeHeight.get();

        /* Draw look vector */
        final float thickness = 0.01F;
        Draw.renderBox(context.batcher.getContext().getMatrices(), -thickness, -thickness + eyeHeight, -thickness, thickness, thickness, 2F, 1F, 0F, 0F);

        /* Draw hitbox */
        Draw.renderBox(context.batcher.getContext().getMatrices(), -hitboxW / 2, 0, -hitboxW / 2, hitboxW, hitboxH, hitboxW);
    }

    @Override
    protected void update()
    {
        super.update();

        if (this.update && this.form != null)
        {
            this.form.update(this.entity);
        }
    }

    @Override
    public void render(UIContext context)
    {
        if (this.form == null || (this.renderForm != null && !this.renderForm.get()))
        {
            this.rigMarkup = RigMarkup.Capture.EMPTY;
        }

        super.render(context);

        /* The edited bone ringed, under the markup and the gizmo; nothing while no bone is edited */
        if (this.form != null && this.outline.isDrawn())
        {
            int index = this.outline.indexOf(this.formEditor::isPickedPart);

            if (index > 0)
            {
                this.outline.draw(context, this.area, index, null);
            }
        }

        this.formEditor.renderMarkup(context);

        this.gizmo.renderSphereHighlight(context);
        this.gizmo.renderReadout(context);

        /* The markup's handle under the cursor names itself. */
        if (this.formEditor.markup.getHovered() != null)
        {
            return;
        }

        if (!this.stencil.hasPicked())
        {
            /* An armed eyedropper over empty space explains itself at the cursor;
             * over a bone the regular pick card below already names the catch. */
            if (this.formEditor.isBonePicking() && this.area.isInside(context))
            {
                context.batcher.textCard(UIKeys.BONE_PICKER_CLICK_BONE.get(), context.mouseX + 12, context.mouseY + 8);
            }

            return;
        }

        Pair<Form, String> pair = this.stencil.getPicked();

        this.stencil.renderPreview(context, this.area);

        if (pair != null && pair.a != null)
        {
            String label = pair.a.getFormIdOrName();

            if (!pair.b.isEmpty())
            {
                label += " - " + pair.b;
            }

            context.batcher.textCard(label, context.mouseX + 12, context.mouseY + 8);
        }
    }

    @Override
    protected void renderGrid(UIContext context)
    {
        if (this.renderForm == null || this.renderForm.get())
        {
            super.renderGrid(context);
        }
    }
}
