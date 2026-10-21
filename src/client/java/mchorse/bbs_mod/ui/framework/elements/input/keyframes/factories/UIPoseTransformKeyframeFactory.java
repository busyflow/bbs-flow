package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UITrackValue;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.pose.UIPoseEditor;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.film.replays.tracks.TrackKind;
import mchorse.bbs_mod.ui.film.replays.FilmBoneWorldProvider;
import mchorse.bbs_mod.ui.utils.WorldTransformClipboard;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix4f;

import java.util.function.Consumer;

public class UIPoseTransformKeyframeFactory extends UIKeyframeFactory<PoseTransform>
{
    @Override public Transform getGizmoTransform(PoseTransform value) { return value; }

    public UISliderTrackpad fix;
    public UIToggle boneVisible;
    public UIColor color;
    public UIColor overlay;
    public UISliderTrackpad lighting;
    public UIPropTransform transform;

    public UIPoseTransformKeyframeFactory(UITrackValue<PoseTransform> track, UIKeyframes editor)
    {
        super(track, editor);

        this.transform = new UIPoseTransforms(this);
        this.transform.enableHotkeys();
        this.transform.setTransform(track.getValue());

        this.keys().register(Keys.TRANSFORMATIONS_TOGGLE_FIX, this::toggleFix).category(UIKeys.TRANSFORMS_KEYS_CATEGORY);

        this.fix = new UISliderTrackpad((v) ->
        {
            if (this.transform.getTransform() instanceof PoseTransform)
            {
                UIPoseTransforms.apply(track, (poseT) -> poseT.fix = v.floatValue());
            }
        });
        this.fix.limit(0D, 1D).increment(0.1D).values(0.1, 0.05D, 0.2D);
        this.fix.tooltip(UIKeys.POSE_CONTEXT_FIX_TOOLTIP);
        this.fix.setValue(track.getValue().fix);

        this.boneVisible = new UIToggle(UIKeys.MODEL_EDITOR_BONE_VISIBLE, track.getValue().visible, (toggle) ->
        {
            boolean visible = toggle.getValue();

            UIPoseTransforms.apply(track, (poseT) -> poseT.visible = visible);
        });

        this.color = new UIColor((c) ->
        {
            if (this.transform.getTransform() instanceof PoseTransform)
            {
                UIPoseTransforms.apply(track, (poseT) -> poseT.color.set(c));
            }
        });
        this.color.withAlpha();
        this.color.setColor(track.getValue().color.getARGBColor());

        this.overlay = new UIColor((c) ->
        {
            if (this.transform.getTransform() instanceof PoseTransform)
            {
                UIPoseTransforms.apply(track, (poseT) -> poseT.overlay.set(c));
            }
        });
        this.overlay.withAlpha();
        this.overlay.tooltip(UIKeys.FORMS_EDITORS_MATERIAL_OVERLAY_TOOLTIP);
        this.overlay.setColor(track.getValue().overlay.getARGBColor());

        /* A 0..1 slider, like every other bone panel — this used to be a toggle writing 1F/0F
         * inverted, which was the only place where glow wasn't a value you could dial in. */
        this.lighting = new UISliderTrackpad((v) ->
        {
            if (this.transform.getTransform() instanceof PoseTransform)
            {
                UIPoseTransforms.apply(track, (poseT) -> poseT.lighting = v.floatValue());
            }
        });
        this.lighting.limit(0D, 1D);
        this.lighting.tooltip(UIKeys.FORMS_EDITORS_MATERIAL_GLOW_TOOLTIP);
        this.lighting.setValue(track.getValue().lighting);

        /* Same rows in the same order, and the material section built by UIPoseEditor itself —
         * this panel is that one without the bone list, so it has to read as the same panel. */
        this.scroll.add(
            this.boneVisible,
            UI.labelRow(UIKeys.POSE_CONTEXT_FIX, this.fix),
            this.transform,
            UIPoseEditor.materialSection(this.color, this.overlay, this.lighting)
        );
    }

    private void toggleFix()
    {
        if (!(this.transform.getTransform() instanceof PoseTransform))
        {
            return;
        }
        float next = this.fix.getValue() >= 0.5F ? 0F : 1F;
        this.fix.setValue(next);
        UIPoseTransforms.apply(this.track, (poseT) -> poseT.fix = next);
    }

    @Override
    public void update()
    {
        if (this.transform.isUserEditing()) return;
        PoseTransform value = this.getDisplayValue();
        this.transform.setTransform(value);
        if (!this.fix.isUserEditing()) this.fix.setValue(value.fix);
        if (!this.lighting.isUserEditing()) this.lighting.setValue(value.lighting);
        this.boneVisible.setValue(value.visible);
        this.color.setColor(value.color.getARGBColor());
        this.overlay.setColor(value.overlay.getARGBColor());
    }

    public static class UIPoseTransforms extends UIKeyframePropTransform
    {
        private UIPoseTransformKeyframeFactory editor;

        public UIPoseTransforms(UIPoseTransformKeyframeFactory editor)
        {
            this.editor = editor;
        }

        @Override
        protected UIKeyframes getKeyframes()
        {
            return this.editor.editor;
        }

        @Override
        protected void applyToSelection(Consumer<Transform> consumer)
        {
            apply(this.editor.track, (poseT) -> consumer.accept(poseT));
        }

        public static void apply(UITrackValue<PoseTransform> track, Consumer<PoseTransform> consumer)
        {
            track.edit((selected) ->
            {
                PoseTransform transform = (PoseTransform) selected;
                consumer.accept(transform);
            });
        }

        @Override
        public boolean canPasteWorldTransform()
        {
            return this.worldProvider != null && WorldTransformClipboard.has();
        }

        @Override
        protected void pasteWorldTransform()
        {
            if (this.worldProvider == null || !WorldTransformClipboard.has())
            {
                return;
            }

            TrackId poseBonePath = TrackId.parse(this.editor.track.sheet.id, TrackKind.BONE);
            String bone = poseBonePath != null ? poseBonePath.subjectPath() : null;

            Matrix4f targetWorld = null;

            if (bone != null && WorldTransformClipboard.hasBone(bone))
            {
                targetWorld = WorldTransformClipboard.getBone(bone);
            }
            else
            {
                targetWorld = WorldTransformClipboard.get();
            }

            if (targetWorld == null || bone == null)
            {
                super.pasteWorldTransform();

                return;
            }

            if (this.worldProvider instanceof FilmBoneWorldProvider filmProvider)
            {
                Pose tempPose = new Pose();
                PoseTransform pt = tempPose.getOrCreate(bone);
                pt.copy(this.editor.track.getValue());

                float tick = 0F;

                if (this.editor.editor != null && this.editor.editor.getSelectedKeyframe() != null)
                {
                    tick = this.editor.editor.getSelectedKeyframe().getTick();
                }

                boolean isOverlay = this.editor.track.sheet != null
                    && this.editor.track.sheet.id != null
                    && this.editor.track.sheet.id.contains("overlay");

                this.beginGesture();

                try
                {
                    if (filmProvider.solveBoneWorldTransform(tempPose, bone, targetWorld, tick, isOverlay))
                    {
                        PoseTransform solved = tempPose.get(bone);

                        apply(this.editor.track, (current) -> current.copy(solved));
                        this.editor.transform.setTransform(this.editor.track.getValue());
                    }
                }
                finally
                {
                    this.endGesture();
                }

                return;
            }

            super.pasteWorldTransform();
        }
    }
}
