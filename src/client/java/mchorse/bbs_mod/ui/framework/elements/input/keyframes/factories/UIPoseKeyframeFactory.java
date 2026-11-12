package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.film.replays.tracks.TrackKind;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.renderers.MobFormRenderer;
import mchorse.bbs_mod.forms.renderers.mob.MobRig;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.utils.IWorldTransformProvider;
import mchorse.bbs_mod.ui.utils.pose.UIPoseEditor;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UITrackValue;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseFixInPlace;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;

import mchorse.bbs_mod.ui.film.replays.FilmBoneWorldProvider;
import mchorse.bbs_mod.ui.utils.WorldTransformClipboard;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class UIPoseKeyframeFactory extends UIKeyframeFactory<Pose>
{
    @Override
    public Transform getGizmoTransform(Pose value)
    {
        String bone = this.poseEditor.getGroup();
        return bone == null ? null : value.getOrCreate(bone);
    }

    public UIPoseFactoryEditor poseEditor;

    /* Which arrangement the fields are in (null until the first layout), so a resize
     * that stays on the same side of the threshold doesn't rebuild the subtree */
    private Boolean wide;

    public UIPoseKeyframeFactory(UITrackValue<Pose> track, UIKeyframes editor)
    {
        super(track, editor);

        this.poseEditor = this.createPoseEditor(editor, track);

        UIKeyframeSheet sheet = track.sheet;

        if (FormUtils.getForm(sheet.property) instanceof ModelForm modelForm)
        {
            ModelInstance model = ((ModelFormRenderer) FormUtilsClient.getRenderer(modelForm)).getModel();

            if (model != null)
            {
                this.poseEditor.setPose(track.getValue(), model.getPoseGroup());
                this.poseEditor.fillGroups(model.model, model.getFlippedParts(), false, model.getDisabledBones());
            }
        }
        else if (FormUtils.getForm(sheet.property) instanceof MobForm mobForm)
        {
            MobRig rig = MobFormRenderer.getRig(mobForm);

            this.poseEditor.setPose(track.getValue(), mobForm.mobID.get());

            if (rig == null)
            {
                this.poseEditor.fillGroups(FormUtilsClient.getRenderer(mobForm).getBones(), false);
            }
            else
            {
                /* No flipped-parts table: vanilla part names are already left_/right_, which is
                 * exactly what Pose's own mirror rule matches. */
                this.poseEditor.fillGroups(rig, null, false, null);
            }
        }

        this.scroll.add(this.poseEditor);

        mchorse.bbs_mod.ui.film.UIFilmPanel filmPanel = editor.getParent(mchorse.bbs_mod.ui.film.UIFilmPanel.class);

        if (filmPanel != null)
        {
            this.poseEditor.transform.worldTransform(new FilmBoneWorldProvider(filmPanel));
        }
    }

    /** Called from the constructor, before a subclass's own fields are set: take everything from the arguments. */
    protected UIPoseFactoryEditor createPoseEditor(UIKeyframes editor, UITrackValue<Pose> track)
    {
        return new UIPoseFactoryEditor(editor, track);
    }

    /**
     * Only the choice of arrangement lives here — this popup is the one that knows its own width,
     * since the user resizes it. Building the arrangement is {@link UIPoseEditor}'s own job, so this
     * editor stays identical to the form editor's pose panel instead of drifting from it.
     */
    @Override
    public void resize()
    {
        boolean wide = this.getFlex().getW() > UIPoseEditor.WIDE_WIDTH;

        if (this.wide == null || this.wide != wide)
        {
            this.wide = wide;
            this.poseEditor.buildLayout(wide);

            /* Ew... */
            for (UIElement child : this.scroll.getChildren(UIElement.class))
            {
                child.noCulling();
            }
        }

        super.resize();
    }

    @Override
    public void update() { this.poseEditor.refreshValue(); }

    public static class UIPoseFactoryEditor extends UIPoseEditor
    {
        private UIKeyframes editor;
        private UITrackValue<Pose> track;

        public static void apply(UITrackValue<Pose> track, Consumer<Pose> consumer)
        {
            track.edit(consumer);
        }

        public static void apply(UITrackValue<Pose> track, String group, Consumer<PoseTransform> consumer)
        {
            apply(track, (pose) -> consumer.accept(pose.getOrCreate(group)));
        }

        /**
         * Applies the consumer to each named bone on every selected keyframe pose (one notify round per track).
         */
        public static void apply(UITrackValue<Pose> track, List<String> boneNames, Consumer<PoseTransform> consumer)
        {
            if (boneNames == null || boneNames.isEmpty())
            {
                return;
            }

            apply(track, (pose) ->
            {
                for (String bone : boneNames)
                {
                    consumer.accept(pose.getOrCreate(bone));
                }
            });
        }

        /**
         * Like {@link #apply(UITrackValue, List, Consumer)} but hands the bone
         * name alongside its {@link PoseTransform}, so callers can decide per bone (e.g.
         * mirror editing via {@link UIPoseEditor#applyToBone}).
         */
        public static void applyBones(UITrackValue<Pose> track, List<String> boneNames, BiConsumer<String, PoseTransform> consumer)
        {
            if (boneNames == null || boneNames.isEmpty())
            {
                return;
            }

            apply(track, (pose) ->
            {
                for (String bone : boneNames)
                {
                    consumer.accept(bone, pose.getOrCreate(bone));
                }
            });
        }

        public UIPoseFactoryEditor(UIKeyframes editor, UITrackValue<Pose> track)
        {
            super();

            this.editor = editor;
            this.track = track;

            /* This popup is short and the user resizes it, so the list asks for less than the form
             * editor's does — it expands into the leftover anyway, and this is the floor it hits
             * when the fields alone already fill the popup. */
            this.groups.list.h(UIStringList.DEFAULT_HEIGHT * 4);

            ((UIPoseTransforms) this.transform).setEditor(this);
        }

        /**
         * This editor is shown in a popup, which is not under the film editor in the widget tree —
         * the timeline that spawned it is, so the bone selection is looked up from there.
         */
        @Override
        protected UIElement selectionAnchor()
        {
            return this.editor;
        }

        public void refreshValue()
        {
            if (this.transform.isUserEditing() || this.fix.isUserEditing() || this.lighting.isUserEditing()) return;

            Pose pose = this.track.getValue();

            if (!PINNED_LIMBS.isEmpty() && pose != null)
            {
                this.solveAllPinnedLimbs(pose);
            }

            this.refreshPose(pose);
        }

        public void solveAllPinnedLimbs(Pose pose)
        {
            if (PINNED_LIMBS.isEmpty() || pose == null)
            {
                return;
            }

            IWorldTransformProvider provider = this.getEffectiveWorldProvider();

            if (provider instanceof FilmBoneWorldProvider filmProvider)
            {
                float tick = this.track.getValueTick();
                boolean isOverlay = this.track.sheet != null
                    && this.track.sheet.id != null
                    && this.track.sheet.id.contains("pose_overlay");

                List<String> orderedPinned = this.getHierarchyOrderedBones(PINNED_LIMBS.keySet());

                for (String bone : orderedPinned)
                {
                    Matrix4f targetWorld = PINNED_LIMBS.get(bone);

                    if (targetWorld != null)
                    {
                        filmProvider.solveBoneWorldTransform(pose, bone, targetWorld, tick, isOverlay);
                    }
                }
            }
        }

        public void solveAllPinnedLimbs()
        {
            if (PINNED_LIMBS.isEmpty() || this.track == null)
            {
                return;
            }

            IWorldTransformProvider provider = this.getEffectiveWorldProvider();

            if (provider instanceof FilmBoneWorldProvider filmProvider)
            {
                float tick = this.track.getValueTick();
                boolean isOverlay = this.track.sheet != null
                    && this.track.sheet.id != null
                    && this.track.sheet.id.contains("pose_overlay");

                List<String> orderedPinned = this.getHierarchyOrderedBones(PINNED_LIMBS.keySet());

                this.track.edit((pose) ->
                {
                    for (String bone : orderedPinned)
                    {
                        Matrix4f targetWorld = PINNED_LIMBS.get(bone);

                        filmProvider.solveBoneWorldTransform(pose, bone, targetWorld, tick, isOverlay);
                    }
                });

                this.refreshPose(this.track.getValue());
            }
        }

        private String getGroup(PoseTransform transform)
        {
            return CollectionUtils.getKey(this.getPose().transforms, transform);
        }

        @Override
        protected UIPropTransform createTransformEditor()
        {
            return new UIPoseTransforms().enableHotkeys();
        }

        @Override
        protected void pastePose(MapType data)
        {
            List<String> current = new ArrayList<>(this.groups.list.getCurrent());

            apply(this.track, (pose) -> pose.fromData(data));
            this.groups.list.setCurrent(current);
            this.pickBones(this.groups.list.getCurrent());
        }

        @Override
        protected void flipPose()
        {
            List<String> current = new ArrayList<>(this.groups.list.getCurrent());

            apply(this.track, (pose) -> pose.flip(this.flippedParts));
            this.groups.list.setCurrent(current);
            this.pickBones(this.groups.list.getCurrent());
        }

        /** The bone within the form the pose track belongs to: {@code formPath/bone}, a root form's bones by name. */
        @Override
        public String bonePath(String bone)
        {
            String id = this.track.sheet.id;
            TrackId path = TrackId.parse(id, TrackKind.BONE);

            if (path != null)
            {
                return path.formPath().isEmpty() ? bone : path.formPath() + "/" + bone;
            }

            int i = id.lastIndexOf('/');

            return i >= 0 ? id.substring(0, i + 1) + bone : bone;
        }

        @Override
        protected BaseValue getPoseProperty()
        {
            return this.track.sheet.property;
        }

        /** Onto every selected key, like the other fields: the corrections were measured at the playhead. */
        @Override
        protected void applyFixInPlace(Map<String, PoseFixInPlace.Correction> corrections)
        {
            applyBones(this.track, new ArrayList<>(corrections.keySet()), (bone, poseT) -> corrections.get(bone).apply(poseT));
            this.refreshPose(this.track.getValue());
        }

        @Override
        protected void setFix(PoseTransform transform, float value)
        {
            apply(this.track, this.getGroup(transform), (poseT) -> poseT.fix = value);
        }

        @Override
        protected void setBoneVisible(PoseTransform transform, boolean value)
        {
            apply(this.track, this.getGroup(transform), (poseT) -> poseT.visible = value);
        }

        @Override
        protected void setColor(PoseTransform transform, int value)
        {
            apply(this.track, this.getGroup(transform), (poseT) -> poseT.color.set(value));
        }

        @Override
        protected void setLighting(PoseTransform poseTransform, float value)
        {
            apply(this.track, this.getGroup(poseTransform), (poseT) -> poseT.lighting = value);
        }

        @Override
        protected void setOverlay(PoseTransform poseTransform, int value)
        {
            apply(this.track, this.getGroup(poseTransform), (poseT) -> poseT.overlay.set(value));
        }
    }

    public static class UIPoseTransforms extends UIKeyframePropTransform
    {
        private UIPoseFactoryEditor editor;

        public void setEditor(UIPoseFactoryEditor editor)
        {
            this.editor = editor;
        }

        @Override
        protected boolean supportsMirror()
        {
            return true;
        }

        @Override
        protected void applyToSelection(Consumer<Transform> consumer)
        {
            Map<String, UIPoseEditor.BoneEdit> targets = this.editor.resolveBoneEdits(this.isMirrorEdit(), this.isAlternateInvert());
            Map<String, Matrix4f> pinned = UIPoseEditor.PINNED_LIMBS;

            this.editor.track.edit((pose) ->
            {
                for (Map.Entry<String, UIPoseEditor.BoneEdit> entry : targets.entrySet())
                {
                    this.editor.applyToBone(entry.getValue(), pose.getOrCreate(entry.getKey()), consumer);
                }

                if (!pinned.isEmpty())
                {
                    IWorldTransformProvider provider = this.editor.getEffectiveWorldProvider();

                    if (provider instanceof FilmBoneWorldProvider filmProvider)
                    {
                        float tick = this.editor.track.getValueTick();
                        boolean isOverlay = this.editor.track.sheet != null
                            && this.editor.track.sheet.id != null
                            && this.editor.track.sheet.id.contains("pose_overlay");

                        List<String> orderedPinned = this.editor.getHierarchyOrderedBones(pinned.keySet());

                        for (String pinnedBone : orderedPinned)
                        {
                            if (!targets.containsKey(pinnedBone))
                            {
                                Matrix4f targetWorld = pinned.get(pinnedBone);

                                filmProvider.solveBoneWorldTransform(pose, pinnedBone, targetWorld, tick, isOverlay);
                            }
                        }
                    }
                }
            });

            if (!pinned.isEmpty())
            {
                IWorldTransformProvider provider = this.editor.getEffectiveWorldProvider();

                if (provider != null)
                {
                    for (String editedBone : targets.keySet())
                    {
                        if (pinned.containsKey(editedBone))
                        {
                            Matrix4f newMat = new Matrix4f();

                            if (provider.getWorldMatrix(editedBone, newMat))
                            {
                                pinned.put(editedBone, newMat);
                            }
                        }
                    }
                }
            }
        }

        @Override
        protected UIKeyframes getKeyframes()
        {
            return this.editor.editor;
        }

        @Override
        protected void reset()
        {
            this.applyToTarget((poseT) ->
            {
                poseT.translate.set(0F, 0F, 0F);
                poseT.scale.set(1F, 1F, 1F);
                poseT.resetRotation();
            });
            this.refillTransform();
        }

        @Override
        public boolean canCopyWorldTransform()
        {
            return this.worldProvider != null && this.editor != null && this.editor.groups.hasBones();
        }

        @Override
        public boolean canPasteWorldTransform()
        {
            return this.worldProvider != null && WorldTransformClipboard.has();
        }

        @Override
        protected void copyWorldTransform()
        {
            if (this.worldProvider == null || this.editor == null)
            {
                return;
            }

            List<String> selected = this.editor.groups.list.getCurrent();

            if (selected == null || selected.isEmpty())
            {
                String first = this.editor.groups.list.getCurrentFirst();

                if (first != null && !first.isEmpty())
                {
                    selected = List.of(first);
                }
            }

            if (selected != null && !selected.isEmpty())
            {
                Map<String, Matrix4f> captured = new LinkedHashMap<>();

                for (String bone : selected)
                {
                    Matrix4f matrix = new Matrix4f();

                    if (this.worldProvider.getWorldMatrix(bone, matrix))
                    {
                        captured.put(bone, matrix);
                    }
                }

                if (!captured.isEmpty())
                {
                    WorldTransformClipboard.setBones(captured);

                    return;
                }
            }

            super.copyWorldTransform();
        }

        @Override
        protected void pasteWorldTransform()
        {
            if (this.worldProvider == null || !WorldTransformClipboard.has())
            {
                return;
            }

            if (WorldTransformClipboard.hasBones())
            {
                this.pasteBonesWorldTransform(WorldTransformClipboard.getBones());

                return;
            }

            String currentBone = this.editor.groups.list.getCurrentFirst();

            if (currentBone != null && !currentBone.isEmpty())
            {
                this.pasteBonesWorldTransform(Map.of(currentBone, WorldTransformClipboard.get()));

                return;
            }

            super.pasteWorldTransform();
        }

        private void pasteBonesWorldTransform(Map<String, Matrix4f> bones)
        {
            if (bones == null || bones.isEmpty() || this.editor == null || this.editor.track == null)
            {
                return;
            }

            boolean isOverlay = this.editor.track.sheet != null
                && this.editor.track.sheet.id != null
                && this.editor.track.sheet.id.contains("pose_overlay");

            float tick = this.editor.track.getValueTick();

            this.beginGesture();

            try
            {
                List<String> orderedBones = new ArrayList<>();

                for (String bone : this.editor.groups.list.getList())
                {
                    if (bones.containsKey(bone))
                    {
                        orderedBones.add(bone);
                    }
                }

                for (String bone : bones.keySet())
                {
                    if (!orderedBones.contains(bone))
                    {
                        orderedBones.add(bone);
                    }
                }

                List<String> pastedBones = new ArrayList<>();

                this.editor.track.edit((pose) ->
                {
                    for (String boneName : orderedBones)
                    {
                        Matrix4f targetWorld = bones.get(boneName);

                        if (this.worldProvider instanceof FilmBoneWorldProvider filmProvider)
                        {
                            if (filmProvider.solveBoneWorldTransform(pose, boneName, targetWorld, tick, isOverlay))
                            {
                                pastedBones.add(boneName);
                            }
                        }
                    }
                });

                if (!pastedBones.isEmpty())
                {
                    this.editor.groups.list.setCurrent(pastedBones);
                    this.editor.groups.list.setCurrentScroll(pastedBones.get(0));
                    this.editor.pickBones(pastedBones);
                    this.editor.refreshPose(this.editor.track.getValue());
                }
            }
            finally
            {
                this.endGesture();
            }
        }
    }
}
