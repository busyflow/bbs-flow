package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.film.FilmMatrices;
import mchorse.bbs_mod.forms.forms.IPosedForm;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.ui.film.replays.FilmBoneWorldProvider;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.FilmEntityRenderer;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.FilmControllerContext;
import mchorse.bbs_mod.film.FilmTarget;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.entities.ReplayEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ParticleForm;
import mchorse.bbs_mod.forms.forms.VanillaParticleForm;
import mchorse.bbs_mod.forms.renderers.utils.RenderFrame;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.ui.ValueOnionSkin;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.utils.pose.UIPoseEditor;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.profiler.BBSProfiler;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.network.ClientPlayerEntity;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.util.List;
import java.util.Map;

public class FilmEditorController extends BaseFilmController
{
    public UIFilmController controller;

    private int lastTick = Integer.MIN_VALUE;
    private boolean fractionalPreview;

    public FilmEditorController(Film film, UIFilmController controller)
    {
        super(film);

        this.controller = controller;
    }

    @Override
    public Map<String, Integer> getActors()
    {
        return this.controller.getActors();
    }

    @Override
    public int getTick()
    {
        return this.controller.panel.getCursor();
    }

    @Override
    protected void updateEntities(int ticks)
    {
        ticks = this.getTick() + (this.controller.panel.getRunner().isRunning() ? 1 : 0);

        int step = (this.lastTick == Integer.MIN_VALUE) ? 9999 : (ticks - this.lastTick);
        /* While playing, the cursor follows real time (and audio sync), so it regularly
         * skips a tick or two on a hiccup. Treating that as a seek wiped every particle
         * in flight mid-playback - the "particles sometimes don't show" bug. Only a
         * backward move or a real seek counts as a jump while playing. */
        boolean isJump = this.controller.isPlaying()
            ? (step < 0 || step > 10)
            : (step != 0 && step != 1);

        if (isJump)
        {
            for (IEntity entity : this.entities.values())
            {
                if (entity.getForm() != null)
                {
                    entity.getForm().restart();
                }
            }
        }

        super.updateEntities(ticks);

        this.lastTick = ticks;
    }

    @Override
    protected void updateEntityAndForm(IEntity entity, int tick)
    {
        boolean isPlaying = this.controller.isPlaying();
        boolean isActor = !(entity instanceof MCEntity);

        if (isPlaying && isActor)
        {
            Replay replay = entity instanceof ReplayEntity re ? re.replay : null;
            if (replay != null && entity.getForm() != null)
            {
                this.applyTracks(replay, entity.getForm(), tick, 0F);
            }
            super.updateEntityAndForm(entity, tick);
        }
    }

    @Override
    protected void applyReplay(Replay replay, int ticks, IEntity entity)
    {
        List<String> groups = this.controller.getRecordingGroups();
        boolean isPlaying = this.controller.isPlaying();
        boolean isActor = !(entity instanceof MCEntity);

        if (entity != this.controller.getControlled() || (this.controller.isRecording() && this.controller.getRecordingCountdown() <= 0 && groups != null))
        {
            replay.keyframes.apply(ticks, entity, entity == this.controller.getControlled() ? groups : null);
            replay.applyClientActions(ticks, entity, this.film);
        }

        if (entity == this.controller.getControlled())
        {
            Vector3d anchoredPos = FilmMatrices.getAnchoredPosition(this.entities, entity, replay, 0F);

            if (anchoredPos != null)
            {
                entity.setPosition(anchoredPos.x, anchoredPos.y, anchoredPos.z);
                entity.setPrevX(anchoredPos.x);
                entity.setPrevY(anchoredPos.y);
                entity.setPrevZ(anchoredPos.z);

                if (entity instanceof MCEntity mcEntity && mcEntity.getMcEntity() instanceof ClientPlayerEntity clientPlayer)
                {
                    clientPlayer.setPos(anchoredPos.x, anchoredPos.y, anchoredPos.z);
                    clientPlayer.prevX = anchoredPos.x;
                    clientPlayer.prevY = anchoredPos.y;
                    clientPlayer.prevZ = anchoredPos.z;
                    clientPlayer.lastRenderX = anchoredPos.x;
                    clientPlayer.lastRenderY = anchoredPos.y;
                    clientPlayer.lastRenderZ = anchoredPos.z;
                    clientPlayer.setVelocity(0, 0, 0);
                }
            }
        }

        if (entity == this.controller.getControlled() && this.controller.isRecording() && this.controller.panel.getRunner().isRunning())
        {
            Float anchorYaw = FilmMatrices.getAnchorYaw(this.entities, entity, replay, 0F);
            float yawOffset = anchorYaw != null ? -anchorYaw : 0F;

            replay.keyframes.record(this.controller.panel.getCursor(), entity, groups, yawOffset);
        }

        ticks = this.getTick() + (this.controller.panel.getRunner().isRunning() ? 1 : 0);

        /* Special pausing logic */
        if (!isPlaying && isActor)
        {
            entity.setPrevX(entity.getX());
            entity.setPrevY(entity.getY());
            entity.setPrevZ(entity.getZ());
            entity.setPrevYaw(entity.getYaw());
            entity.setPrevHeadYaw(entity.getHeadYaw());
            entity.setPrevBodyYaw(entity.getBodyYaw());
            entity.setPrevPitch(entity.getPitch());

            int diff = (this.lastTick == Integer.MIN_VALUE) ? 9999 : (ticks - this.lastTick);
            boolean isJump = (diff != 0 && diff != 1);

            if (diff == 1 && !isJump)
            {
                entity.update();

                if (entity.getForm() != null)
                {
                    this.applyTracks(replay, entity.getForm(), ticks, 0F);
                    entity.getForm().update(entity);
                }
            }
        }
    }

    private boolean isParticleForm(Form form)
    {
        if (form == null)
        {
            return false;
        }

        if (form instanceof VanillaParticleForm || form instanceof ParticleForm)
        {
            return true;
        }

        if (form.getRenderer() instanceof ITickable)
        {
            return true;
        }

        for (mchorse.bbs_mod.forms.forms.BodyPart part : form.parts.getList())
        {
            if (this.isParticleForm(part.getForm()))
            {
                return true;
            }
        }

        return false;
    }

    private void warmupParticleForm(Replay replay, IEntity entity, int currentTick)
    {
        Form form = entity.getForm();

        if (form == null)
        {
            return;
        }

        form.restart();

        int startTick = Math.max(0, currentTick - 25);

        for (int t = startTick; t < currentTick; t++)
        {
            replay.keyframes.apply(t, entity);
            this.applyTracks(replay, form, t, 0F);
            form.update(entity);
        }

        replay.keyframes.apply(currentTick, entity);
        this.applyTracks(replay, form, currentTick, 0F);
        form.update(entity);

        Vector3d anchoredPos = FilmMatrices.getAnchoredPosition(this.entities, entity, replay, 0F);

        if (anchoredPos != null)
        {
            entity.setPosition(anchoredPos.x, anchoredPos.y, anchoredPos.z);
        }
    }

    @Override
    protected float getTransition(IEntity entity, float transition)
    {
        boolean current = this.isCurrent(entity) && this.controller.isControlling();

        if (current || this.controller.isPlaying())
        {
            return this.controller.panel.isRunning() ? this.controller.panel.getRunner().getTransition(transition) : transition;
        }

        return this.controller.panel.getRunner().getTransition(0F);
    }

    @Override
    public void startRenderFrame(float transition)
    {
        boolean fractional = this.controller.panel.getRunner().getTransition(0F) != 0F;

        if (this.film != null && !this.controller.isPlaying() && (fractional || this.fractionalPreview))
        {
            /* Seeking inside one tick does not advance the simulation. Sample the actor's
             * movement as well as its form on every frame, without replaying actions. */
            List<Replay> replays = this.film.replays.getList();

            for (int i = 0; i < replays.size(); i++)
            {
                Replay replay = replays.get(i);
                IEntity entity = this.entities.get(replay.getId());

                if (entity != null && entity != this.controller.getControlled() && this.canUpdate(i, replay, entity, UpdateMode.PROPERTIES))
                {
                    float tick = replay.getTick(this.getTick()) + this.getTransition(entity, transition);

                    replay.keyframes.apply(tick, entity);
                    entity.setPrevX(entity.getX());
                    entity.setPrevY(entity.getY());
                    entity.setPrevZ(entity.getZ());
                    entity.setPrevYaw(entity.getYaw());
                    entity.setPrevHeadYaw(entity.getHeadYaw());
                    entity.setPrevBodyYaw(entity.getBodyYaw());
                    entity.setPrevPitch(entity.getPitch());
                }
            }
        }

        this.fractionalPreview = fractional;
        super.startRenderFrame(transition);
    }

    @Override
    protected boolean canUpdate(int i, Replay replay, IEntity entity, UpdateMode updateMode)
    {
        return super.canUpdate(i, replay, entity, updateMode)
            || this.controller.getPovMode() != UIFilmController.CAMERA_MODE_FIRST_PERSON
            || !this.isCurrent(entity)
            || !this.controller.orbit.enabled;
    }

    private boolean isEnforcingPinned;

    private boolean isCurrent(Replay replay)
    {
        return replay != null && replay == this.controller.getReplay();
    }

    public void enforcePinnedLimbs(Replay replay, IEntity entity, float tick)
    {
        if (this.isEnforcingPinned || replay == null || entity == null)
        {
            return;
        }

        Map<String, Matrix4f> pinned = UIPoseEditor.PINNED_LIMBS;

        if (pinned.isEmpty())
        {
            return;
        }

        Form form = entity.getForm();

        if (!(form instanceof IPosedForm posedForm))
        {
            return;
        }

        UIKeyframeEditor keyframeEditor = this.controller.panel.replayEditor.getBoneKeyframeEditor();

        if (keyframeEditor == null || keyframeEditor.editor == null)
        {
            keyframeEditor = this.controller.panel.replayEditor.keyframeEditor;
        }

        UIKeyframeSheet sheet = keyframeEditor != null && keyframeEditor.editor != null ? keyframeEditor.editor.getSheet() : null;
        boolean isOverlay = sheet != null && sheet.id != null && sheet.id.contains("pose_overlay");

        ValuePose poseProp = isOverlay ? posedForm.getPoseOverlay() : posedForm.getPose();

        if (poseProp == null || poseProp.get() == null)
        {
            return;
        }

        Pose pose = poseProp.get();

        this.isEnforcingPinned = true;

        try
        {
            FilmBoneWorldProvider provider = new FilmBoneWorldProvider(this.controller.panel);
            List<String> orderedBones = UIPoseEditor.getHierarchyOrderedBonesStatic(pinned.keySet());
            boolean modified = false;

            for (String bone : orderedBones)
            {
                Matrix4f targetWorld = pinned.get(bone);

                if (targetWorld != null)
                {
                    if (provider.solveBoneWorldTransform(pose, bone, targetWorld, tick, isOverlay))
                    {
                        modified = true;
                    }
                }
            }

            if (modified)
            {
                poseProp.set(pose);
                RenderFrame.invalidate();
            }
        }
        finally
        {
            this.isEnforcingPinned = false;
        }
    }

    @Override
    protected void applyTracks(Replay replay, Form root, float tick, float transition)
    {
        super.applyTracks(replay, root, tick, transition);

        if (this.isCurrent(replay) && !UIPoseEditor.PINNED_LIMBS.isEmpty() && !this.isEnforcingPinned)
        {
            IEntity entity = this.entities.get(replay.getId());

            if (entity != null)
            {
                this.enforcePinnedLimbs(replay, entity, tick);
            }
        }
    }

    @Override
    protected void renderEntity(WorldRenderContext context, Replay replay, IEntity entity)
    {
        boolean current = this.isCurrent(entity);

        if (!(this.controller.getPovMode() == UIFilmController.CAMERA_MODE_FIRST_PERSON && current))
        {
            if (current && !UIPoseEditor.PINNED_LIMBS.isEmpty() && !this.isEnforcingPinned)
            {
                float tick = replay.getTick(this.getTick()) + this.getTransition(entity, context.tickDelta());

                this.enforcePinnedLimbs(replay, entity, tick);
            }

            super.renderEntity(context, replay, entity);
        }

        boolean isPlaying = this.controller.isPlaying();
        int ticks = replay.getTick(this.getTick());
        ValueOnionSkin onionSkin = this.controller.getOnionSkin();
        BaseValue value = replay.properties.get(onionSkin.group.get());

        if (value == null)
        {
            value = replay.properties.get("pose");
        }

        if (value instanceof KeyframeChannel<?> pose && entity instanceof StubEntity)
        {
            boolean canRender = onionSkin.enabled.get();

            if (!onionSkin.all.get())
            {
                canRender = canRender && current;
            }

            if (canRender)
            {
                KeyframeSegment<?> segment = pose.findSegment(ticks);

                if (segment != null)
                {
                    BBSProfiler.begin(BBSProfiler.Timer.ONION);
                    this.renderOnion(replay, pose.getKeyframes().indexOf(segment.a), -1, pose, onionSkin.preColor.get(), onionSkin.preFrames.get(), context, isPlaying, entity);
                    this.renderOnion(replay, pose.getKeyframes().indexOf(segment.b), 1, pose, onionSkin.postColor.get(), onionSkin.postFrames.get(), context, isPlaying, entity);
                    BBSProfiler.end(BBSProfiler.Timer.ONION);

                    float tick = ticks + this.getTransition(entity, context.tickDelta());
                    replay.keyframes.apply(isPlaying ? ticks : tick, entity);
                    Form form = entity.getForm();
                    replay.properties.applyProperties(form, tick);

                    if (current && !UIPoseEditor.PINNED_LIMBS.isEmpty() && !this.isEnforcingPinned)
                    {
                        this.enforcePinnedLimbs(replay, entity, tick);
                    }

                    /* Back on the current tick after the onion excursion. */
                    RenderFrame.invalidate();

                    if (!isPlaying)
                    {
                        entity.setPrevX(entity.getX());
                        entity.setPrevY(entity.getY());
                        entity.setPrevZ(entity.getZ());
                        entity.setPrevYaw(entity.getYaw());
                        entity.setPrevHeadYaw(entity.getHeadYaw());
                        entity.setPrevBodyYaw(entity.getBodyYaw());
                        entity.setPrevPitch(entity.getPitch());
                    }
                }
            }
        }
    }

    private void renderOnion(Replay replay, int index, int direction, KeyframeChannel<?> pose, int color, int frames, WorldRenderContext context, boolean isPlaying, IEntity entity)
    {
        List<? extends Keyframe<?>> keyframes = pose.getKeyframes();
        float alpha = Colors.getA(color);

        for (; CollectionUtils.inRange(keyframes, index) && frames > 0; index += direction)
        {
            Keyframe<?> keyframe = keyframes.get(index);

            if (keyframe.getTick() == this.getTick())
            {
                continue;
            }

            int tick1 = (int) keyframe.getTick();
            replay.keyframes.apply(tick1, entity);
            float tick = (int) keyframe.getTick();
            Form form = entity.getForm();
            replay.properties.applyProperties(form, tick);

            /* This pass re-poses the live entity for another tick; whatever the frame cache
             * holds for it no longer describes what is about to render. */
            RenderFrame.invalidate();

            FilmEntityRenderer.renderEntity(FilmControllerContext.instance
                .setup(this.getEntities(), entity, replay, context)
                .color(Colors.setA(color, alpha))
                .transition(0F));

            frames -= 1;
            alpha *= alpha;
        }
    }

    @Override
    protected FilmControllerContext getFilmControllerContext(WorldRenderContext context, Replay replay, IEntity entity)
    {
        boolean recording = this.controller.panel.recorder.isRecording();

        /* One question, asked once. The gizmo only ever belongs to the replay being edited,
         * so every other actor in the film gets NONE. */
        FilmTarget target = this.isCurrent(entity) && !recording
            ? this.controller.getEditTarget()
            : FilmTarget.NONE;

        String aBone2 = replay.axesPreview.get() && !recording ? replay.axesPreviewBone.get() : null;

        return super.getFilmControllerContext(context, replay, entity)
            .transition(this.getTransition(entity, context.tickDelta()))
            .gizmoTarget(target)
            .gizmoMask(UIReplaysEditorUtils.getFilmGizmoMask(this.controller.panel, target))
            /* The UI camera is copied later in the frame. Use the view that is
             * actually drawing this pass, before any actor transforms go on it. */
            .gizmoView(context.matrixStack().peek().getPositionMatrix())
            .gizmoViewportHeight(this.controller.panel.preview.getViewport().h)
            .replayTransform(this.controller.panel.replayEditor == null ? null : this.controller.panel.replayEditor.replayTransform)
            .bone2(aBone2, TransformSpace.LOCAL);
    }

    private boolean isCurrent(IEntity entity)
    {
        return entity == this.controller.getCurrentEntity();
    }
}
