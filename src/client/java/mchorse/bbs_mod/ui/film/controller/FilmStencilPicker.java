package mchorse.bbs_mod.ui.film.controller;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.film.FilmEntityRenderer;
import mchorse.bbs_mod.film.FilmControllerContext;
import mchorse.bbs_mod.film.FilmTarget;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.profiler.BBSProfiler;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Hit-testing the film preview: the scene is drawn again into an off-screen buffer where every
 * pickable thing writes its id instead of its colour, and the pixel under the cursor says what
 * is hovered — a bone, a gizmo handle, or (with Alt) another replay.
 *
 * <p>The id space is shared with the gizmo, which owns the low ids ({@link Gizmo#STENCIL_MAX});
 * replays begin right after, so a pixel is never ambiguous about which of the two it is.
 */
public class FilmStencilPicker
{
    /** Where replay ids begin, past the ids the gizmo's own handles claim. */
    private static final int REPLAY_STENCIL_OFFSET = Gizmo.STENCIL_MAX + 1;

    private final UIFilmController controller;

    private final StencilFormFramebuffer stencil = new StencilFormFramebuffer();
    private final StencilMap stencilMap = new StencilMap();

    /** The replay under the cursor while Alt is held, or -1. */
    private int hoveredReplayIndex = -1;

    /* The pick is a pure function of these inputs; while none of them change, the previous
     * pass's buffer and pick result stand, and the whole scene re-render is skipped. The
     * heartbeat below bounds staleness from anything this key does not see (an undo from the
     * keyboard, physics settling) to a fraction of a second. */
    private final Matrix4f lastPickView = new Matrix4f();
    private final Matrix4f lastPickProjection = new Matrix4f();
    /* The view matrix is camera-relative: moving the camera (an orbit zoom) shows only here */
    private final Vector3d lastPickEye = new Vector3d();
    private int lastPickMouseX = Integer.MIN_VALUE;
    private int lastPickMouseY;
    private boolean lastPickAlt;
    private boolean lastPickTargetMode;
    private int lastPickCursor;
    private int lastPickReplayIndex;
    private FilmTarget lastPickTarget;
    private int lastPickReplayCount;
    private int lastPickPoseVersion;
    private boolean sceneValid;
    private int framesSincePick;

    /** Repick at least this often (in frames) even when no tracked input changed. */
    private static final int PICK_HEARTBEAT = 120;

    /** One-shot target selection, scoped to the visible control that armed it. */
    private UIElement pickOwner;
    private String excludedReplay;
    private BiConsumer<String, Pair<Form, String>> pickCallback;

    public FilmStencilPicker(UIFilmController controller)
    {
        this.controller = controller;
    }

    public StencilFormFramebuffer getStencil()
    {
        return this.stencil;
    }

    public void toggleTargetPick(UIElement owner, String excludedReplay, BiConsumer<String, Pair<Form, String>> callback)
    {
        boolean cancel = this.pickOwner == owner;

        this.cancelTargetPick();

        if (!cancel)
        {
            this.pickOwner = owner;
            this.excludedReplay = excludedReplay;
            this.pickCallback = callback;
        }
    }

    public void cancelTargetPick()
    {
        this.pickOwner = null;
        this.pickCallback = null;
        this.excludedReplay = null;
        this.hoveredReplayIndex = -1;
        this.stencil.clearPicking();
        this.lastPickMouseX = Integer.MIN_VALUE;
        this.sceneValid = false;
    }

    public boolean isPickingTarget()
    {
        if (this.pickOwner != null && !this.pickOwner.canBeSeen())
        {
            this.cancelTargetPick();
        }

        return this.pickCallback != null;
    }

    public boolean isPickingTarget(UIElement owner)
    {
        return this.isPickingTarget() && this.pickOwner == owner;
    }

    public boolean pickTarget(UIContext context)
    {
        if (!this.isPickingTarget() || !this.controller.panel.preview.getViewport().isInside(context))
        {
            return false;
        }

        if (context.mouseButton == 1)
        {
            this.cancelTargetPick();
        }
        else if (context.mouseButton == 0)
        {
            this.stencil.finishAsyncPick();
            Form spline = this.controller.pickSplineForm(context);
            Pair<Form, String> handle = this.controller.pickRigHandle(context);
            Pair<Form, String> pair = spline != null ? new Pair<>(spline, "") : handle != null ? handle : this.stencil.getPicked();

            if (pair != null && pair.a != null)
            {
                Form root = FormUtils.getRoot(pair.a);

                for (var entry : this.controller.getEntities().entrySet())
                {
                    if (entry.getValue().getForm() == root && !entry.getKey().equals(this.excludedReplay))
                    {
                        BiConsumer<String, Pair<Form, String>> callback = this.pickCallback;

                        this.cancelTargetPick();
                        callback.accept(entry.getKey(), pair);
                        break;
                    }
                }
            }
        }

        return true;
    }

    public int getHoveredReplayIndex()
    {
        return this.hoveredReplayIndex;
    }

    public int resolveHoveredReplayIndex()
    {
        this.stencil.finishAsyncPick();

        if (!this.stencil.hasPicked())
        {
            return -1;
        }

        int stencilIndex = this.stencil.getIndex() - REPLAY_STENCIL_OFFSET;

        if (stencilIndex >= 0 && stencilIndex < this.controller.panel.getData().replays.getList().size())
        {
            return stencilIndex;
        }

        return -1;
    }

    /**
     * The picking pass of a rendered frame: draw the scene into the pick buffer, read what is
     * under the cursor, then paint the hover highlight and its label back over the viewport.
     */
    public void renderPreview(UIContext context, Area area)
    {
        if (this.controller.panel.isFlying() || this.controller.worldRenderContext() == null)
        {
            return;
        }

        boolean altPressed = Window.isAltPressed() && !this.isPickingTarget();

        this.renderStencil(this.controller.worldRenderContext(), this.controller.getContext(), altPressed);

        this.hoveredReplayIndex = -1;

        if (this.controller.canShowGizmo())
        {
            this.controller.gizmo().update(context);
            this.controller.gizmo().renderSphereHighlight(context);
            this.controller.gizmo().renderReadout(context);
        }

        if (!this.stencil.hasPicked())
        {
            return;
        }

        int index = this.stencil.getIndex();
        Pair<Form, String> pair = this.stencil.getPicked();

        this.stencil.renderPreview(context, area);

        if (altPressed)
        {
            int selectedReplayIndex = this.controller.getCurrentReplayIndex();
            int stencilIndex = index - REPLAY_STENCIL_OFFSET;

            if (stencilIndex >= 0 && stencilIndex < this.controller.panel.getData().replays.getList().size())
            {
                this.hoveredReplayIndex = stencilIndex;

                String label = this.controller.panel.getData().replays.getList().get(stencilIndex).getName();

                context.batcher.textCard(label, context.mouseX + 12, context.mouseY + 8);
            }
            else if (pair != null && pair.a != null)
            {
                String label = pair.a.getFormIdOrName();

                if (!pair.b.isEmpty())
                {
                    label += " - " + pair.b;
                }

                context.batcher.textCard(label, context.mouseX + 12, context.mouseY + 8);
            }
        }
        else if (pair != null && pair.a != null)
        {
            String label = pair.a.getFormIdOrName();

            if (!pair.b.isEmpty())
            {
                label += " - " + pair.b;
            }

            context.batcher.textCard(label, context.mouseX + 12, context.mouseY + 8);
        }
    }

    private void renderStencil(WorldRenderContext renderContext, UIContext context, boolean altPressed)
    {
        Area viewport = this.controller.panel.preview.getViewport();

        /* Skip hover picking completely while actively orbiting/coasting the camera or dragging a gizmo:
         * neither needs a hover highlight and skipping avoids GPU readback stalls during drags. */
        if (this.controller.orbit.isMoving() || Gizmo.INSTANCE.getTrackedGesture() != null)
        {
            this.stencil.clearIndex();
            this.sceneValid = false;
            this.lastPickMouseX = Integer.MIN_VALUE;

            return;
        }

        if (!viewport.isInside(context) || this.controller.panel.preview.isOverIcons(context) || this.controller.getControlled() != null)
        {
            this.stencil.clearPicking();
            this.sceneValid = false;
            this.lastPickMouseX = Integer.MIN_VALUE;

            return;
        }

        IEntity entity = this.controller.getCurrentEntity();

        if ((entity == null || (this.controller.getPovMode() == UIFilmController.CAMERA_MODE_FIRST_PERSON && entity == this.controller.getCurrentEntity())) && !altPressed && !this.isPickingTarget())
        {
            this.sceneValid = false;
            this.lastPickMouseX = Integer.MIN_VALUE;

            return;
        }

        boolean sceneChanged = this.needsSceneRedraw(altPressed, entity);
        boolean mouseMoved = context.mouseX != this.lastPickMouseX || context.mouseY != this.lastPickMouseY;

        if (!sceneChanged)
        {
            /* Harvest any async PBO readback queued by the previous frame's stencil render. */
            this.stencil.finishAsyncPick();

            if (!mouseMoved)
            {
                return;
            }
        }

        this.lastPickMouseX = context.mouseX;
        this.lastPickMouseY = context.mouseY;

        if (!sceneChanged && this.sceneValid && this.stencil.getFramebuffer() != null)
        {
            /* Scene/camera/pose haven't moved — read the new cursor pixel straight from
             * the already-rendered stencil buffer without re-rendering any 3D geometry. */
            Texture mainTexture = this.stencil.getFramebuffer().getMainTexture();
            int x = (int) ((context.mouseX - viewport.x) / (float) viewport.w * mainTexture.width);
            int y = (int) ((1F - (context.mouseY - viewport.y) / (float) viewport.h) * mainTexture.height);
            int radius = Math.round(BBSSettings.gizmoHoverTolerance.get() * mainTexture.width / (float) viewport.w);

            this.stencil.repickFromBufferAsync(x, y, radius, Gizmo.STENCIL_MAX);

            return;
        }

        BBSProfiler.count(BBSProfiler.Section.STENCIL_PASS);
        BBSProfiler.begin(BBSProfiler.Timer.STENCIL_PASS);

        RenderSystem.depthFunc(GL11.GL_LESS);
        MatrixStackUtils.cacheMatrices();
        RenderSystem.setProjectionMatrix(this.controller.panel.lastProjection, VertexSorter.BY_Z);
        RenderSystem.setInverseViewRotationMatrix(new Matrix3f(this.controller.panel.lastView).invert());

        MatrixStack worldStack = renderContext.matrixStack();
        worldStack.push();
        worldStack.loadIdentity();
        MatrixStackUtils.multiply(worldStack, this.controller.panel.lastView);

        this.ensureFramebuffer(viewport);

        /* Match the visual gizmo's on-screen size compensation (see
         * Gizmo#setViewportHeight) so the pick handles line up with what is drawn. */
        Gizmo.INSTANCE.setViewportHeight(viewport.h);

        boolean isPlaying = this.controller.isPlaying();
        Texture mainTexture = this.stencil.getFramebuffer().getMainTexture();

        this.stencilMap.setup();
        this.stencil.apply();

        if (this.isPickingTarget())
        {
            this.stencilMap.setIncrement(true);

            for (Replay replay : this.controller.panel.getData().replays.getList())
            {
                IEntity replayEntity = this.controller.getEntities().get(replay.getId());

                if (replayEntity == null || replay.getId().equals(this.excludedReplay))
                {
                    continue;
                }

                FilmEntityRenderer.renderEntity(FilmControllerContext.instance
                    .setup(this.controller.getEntities(), replayEntity, replay, renderContext)
                    .transition(isPlaying ? renderContext.tickDelta() : 0)
                    .stencil(this.stencilMap)
                    .relative(replay.relative.get()));
            }
        }
        else if (altPressed)
        {
            List<Replay> replays = this.controller.panel.getData().replays.getList();
            int selectedReplayIndex = this.controller.getCurrentReplayIndex();
            FilmTarget target = this.controller.getEditTarget();

            /* Walked by list position, not by the entity map: the stencil object index IS the
             * replay's position in the film, which is what the pick reads back. */
            for (int i = 0; i < replays.size(); i++)
            {
                Replay replay = replays.get(i);
                IEntity replayEntity = this.controller.getEntities().get(replay.getId());

                if (replayEntity == null)
                {
                    continue;
                }

                FilmControllerContext filmContext = FilmControllerContext.instance
                    .setup(this.controller.getEntities(), replayEntity, replay, renderContext)
                    .transition(isPlaying ? renderContext.tickDelta() : 0)
                    .stencil(this.stencilMap)
                    .relative(replay.relative.get());

                if (i == selectedReplayIndex)
                {
                    this.stencilMap.objectIndex = replays.size() + REPLAY_STENCIL_OFFSET;
                    this.stencilMap.setIncrement(true);

                    filmContext
                        .gizmoTarget(target)
                        .gizmoMask(UIReplaysEditorUtils.getFilmGizmoMask(this.controller.panel, target))
                        .gizmoView(renderContext.matrixStack().peek().getPositionMatrix())
                        .replayTransform(this.controller.panel.replayEditor == null ? null : this.controller.panel.replayEditor.replayTransform);
                }
                else
                {
                    this.stencilMap.objectIndex = i + REPLAY_STENCIL_OFFSET;
                    this.stencilMap.setIncrement(false);
                }

                FilmEntityRenderer.renderEntity(filmContext);
            }
        }
        else
        {
            Replay replay = this.controller.panel.replayEditor.getReplay();

            this.stencilMap.setIncrement(true);

            FilmTarget target = this.controller.getEditTarget();

            FilmEntityRenderer.renderEntity(FilmControllerContext.instance
                .setup(this.controller.getEntities(), entity, replay, renderContext)
                .transition(isPlaying ? renderContext.tickDelta() : 0)
                .stencil(this.stencilMap)
                .relative(replay.relative.get())
                .gizmoTarget(target)
                .gizmoMask(UIReplaysEditorUtils.getFilmGizmoMask(this.controller.panel, target))
                .gizmoView(renderContext.matrixStack().peek().getPositionMatrix())
                .replayTransform(this.controller.panel.replayEditor == null ? null : this.controller.panel.replayEditor.replayTransform));
        }

        int x = (int) ((context.mouseX - viewport.x) / (float) viewport.w * mainTexture.width);
        int y = (int) ((1F - (context.mouseY - viewport.y) / (float) viewport.h) * mainTexture.height);
        int radius = Math.round(BBSSettings.gizmoHoverTolerance.get() * mainTexture.width / (float) viewport.w);

        this.stencil.pickAsync(x, y, radius, Gizmo.STENCIL_MAX);
        this.stencil.unbind(this.stencilMap);
        this.sceneValid = true;

        MinecraftClient.getInstance().getFramebuffer().beginWrite(true);

        worldStack.pop();
        MatrixStackUtils.restoreMatrices();
        RenderSystem.depthFunc(GL11.GL_ALWAYS);

        BBSProfiler.end(BBSProfiler.Timer.STENCIL_PASS);
    }

    /**
     * True when the 3D scene contents in the pick buffer may have changed — independent of
     * mouse position, so moving the cursor across a still scene reuses the rendered FBO.
     */
    private boolean needsSceneRedraw(boolean altPressed, IEntity entity)
    {
        this.framesSincePick += 1;

        int cursor = this.controller.panel.getCursor();
        int replayIndex = this.controller.getCurrentReplayIndex();
        FilmTarget target = this.controller.getEditTarget();
        int replayCount = this.controller.panel.getData().replays.getList().size();
        int poseVersion = entity != null && entity.getForm() != null ? entity.getForm().getPoseVersion() : 0;
        boolean pickingTarget = this.isPickingTarget();

        boolean unchanged = this.sceneValid
            && !this.controller.isPlaying()
            && this.framesSincePick < PICK_HEARTBEAT
            && altPressed == this.lastPickAlt
            && pickingTarget == this.lastPickTargetMode
            && cursor == this.lastPickCursor
            && replayIndex == this.lastPickReplayIndex
            && poseVersion == this.lastPickPoseVersion
            && target.equals(this.lastPickTarget)
            && replayCount == this.lastPickReplayCount
            && this.controller.panel.lastView.equals(this.lastPickView)
            && this.controller.panel.lastProjection.equals(this.lastPickProjection)
            && this.controller.panel.getDrawnCamera().position.equals(this.lastPickEye);

        if (unchanged)
        {
            return false;
        }

        this.framesSincePick = 0;
        this.lastPickAlt = altPressed;
        this.lastPickTargetMode = pickingTarget;
        this.lastPickCursor = cursor;
        this.lastPickReplayIndex = replayIndex;
        this.lastPickPoseVersion = poseVersion;
        this.lastPickTarget = target;
        this.lastPickReplayCount = replayCount;
        this.lastPickView.set(this.controller.panel.lastView);
        this.lastPickProjection.set(this.controller.panel.lastProjection);
        this.lastPickEye.set(this.controller.panel.getDrawnCamera().position);

        return true;
    }

    private void ensureFramebuffer(Area viewport)
    {
        this.stencil.setup(Link.bbs("stencil_film"));

        Texture mainTexture = this.stencil.getFramebuffer().getMainTexture();
        /* Match the actual viewport size (capped at 960x540) and compare against actual
         * texture pixels directly — resizeGUI previously multiplied by guiScale, causing a
         * mismatch and full 4K FBO reallocation on every single stencil pass. */
        int w = Math.max(2, Math.min(960, viewport.w > 0 ? viewport.w : BBSRendering.getVideoWidth()));
        int h = Math.max(2, Math.min(540, viewport.h > 0 ? viewport.h : BBSRendering.getVideoHeight()));

        if (mainTexture.width != w || mainTexture.height != h)
        {
            this.stencil.resize(w, h);
            this.sceneValid = false;
        }
    }
}
