package mchorse.bbs_mod.ui.film.replays;


import mchorse.bbs_mod.film.FilmMatrices;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.film.replays.tracks.TrackKind;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.utils.RenderFrame;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.forms.forms.IPosedForm;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.ui.framework.elements.input.UITransform;
import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.ui.utils.IWorldTransformProvider;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.AxisAngle4f;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.function.Supplier;

/**
 * World-transform source for the film's pose editor: the edited bone's absolute world matrix,
 * so the world paste can drive the pose toward a captured matrix.
 *
 * <p>🔴 Sampled with the camera at the ORIGIN, so the matrix is absolute and stable across ticks
 * — the whole point of a world-space paste.
 */
public class FilmBoneWorldProvider implements IWorldTransformProvider
{
    private final UIFilmPanel panel;

    public FilmBoneWorldProvider(UIFilmPanel panel)
    {
        this.panel = panel;
    }

    public float getSamplingTick()
    {
        UIKeyframeEditor keyframeEditor = this.panel.replayEditor.keyframeEditor;

        if (keyframeEditor != null && keyframeEditor.editor != null && keyframeEditor.editor.getKeyframe() != null)
        {
            return keyframeEditor.editor.getKeyframe().getTick();
        }

        float transition = this.panel.getRunner().isRunning() && this.panel.replayEditor.getContext() != null
            ? this.panel.replayEditor.getContext().getTransition()
            : 0F;

        return this.panel.getCursor() + transition;
    }

    public String getBonePath(String boneName)
    {
        if (boneName == null)
        {
            return null;
        }

        UIKeyframeEditor keyframeEditor = this.panel.replayEditor.keyframeEditor;
        UIKeyframeSheet sheet = keyframeEditor != null && keyframeEditor.editor != null ? keyframeEditor.editor.getSheet() : null;

        if (sheet != null)
        {
            TrackId path = TrackId.parse(sheet.id, TrackKind.BONE);

            if (path != null && !path.formPath().isEmpty())
            {
                String prefix = path.formPath() + "/";

                return boneName.startsWith(prefix) ? boneName : prefix + boneName;
            }

            int i = sheet.id.lastIndexOf('/');

            if (i >= 0)
            {
                String prefix = sheet.id.substring(0, i + 1);

                return boneName.startsWith(prefix) ? boneName : prefix + boneName;
            }
        }

        return boneName;
    }

    @Override
    public boolean getWorldMatrix(Matrix4f out)
    {
        UIKeyframeEditor keyframeEditor = this.panel.replayEditor.keyframeEditor;
        Pair<String, TransformSpace> bone = keyframeEditor == null ? null : keyframeEditor.getBone();

        return bone != null && this.getWorldMatrix(bone.a, out);
    }

    @Override
    public boolean getWorldMatrix(String bone, Matrix4f out)
    {
        return this.getWorldMatrix(bone, out, true, this.getSamplingTick());
    }

    public boolean getWorldMatrix(String bone, Matrix4f out, boolean useBoneMatrix)
    {
        return this.getWorldMatrix(bone, out, useBoneMatrix, this.getSamplingTick());
    }

    public boolean getWorldMatrix(String bone, Matrix4f out, boolean useBoneMatrix, float tick)
    {
        UIReplaysEditor replayEditor = this.panel.replayEditor;
        Replay replay = replayEditor.getReplay();
        IEntity entity = this.panel.getController().getCurrentEntity();

        if (bone == null || replay == null || entity == null)
        {
            return false;
        }

        float transition = this.panel.getRunner().isRunning() && replayEditor.getContext() != null
            ? replayEditor.getContext().getTransition()
            : 0F;

        replay.anchor.setRuntimeValue(replay.evaluateAnchor(tick));
        Form form = entity.getForm();

        /* Flush matrix cache so perturbed scratch transforms are measured freshly */
        RenderFrame.invalidate();

        /* Push the pose into the model so the matrix cache reflects it. */
        if (form != null)
        {
            replay.properties.applyProperties(form, tick);
        }

        Matrix4f matrix = FilmMatrices.getBoneCompositeMatrix(
            this.panel.getController().getEntities(), entity, replay, 0D, 0D, 0D, transition, this.getBonePath(bone), useBoneMatrix
        );

        if (matrix == null)
        {
            return false;
        }

        out.set(matrix);

        return true;
    }

    /**
     * Solves the local transform of {@code bone} in {@code pose} so that its world transform
     * matches {@code targetWorld} at {@code tick}. Idempotent and exact.
     */
    public boolean solveBoneWorldTransform(Pose pose, String bone, Matrix4f targetWorld, float tick, boolean isOverlay)
    {
        if (pose == null || bone == null || targetWorld == null)
        {
            return false;
        }

        UIReplaysEditor replayEditor = this.panel.replayEditor;
        Replay replay = replayEditor.getReplay();
        IEntity entity = this.panel.getController().getCurrentEntity();

        if (replay == null || entity == null)
        {
            return false;
        }

        Form form = entity.getForm();

        if (form == null)
        {
            return false;
        }

        UIKeyframeEditor keyframeEditor = this.panel.replayEditor.keyframeEditor;
        UIKeyframeSheet sheet = keyframeEditor != null && keyframeEditor.editor != null ? keyframeEditor.editor.getSheet() : null;
        ValuePose property = (sheet != null && sheet.property instanceof ValuePose vp) ? vp : null;

        if (property == null && form instanceof IPosedForm posedForm)
        {
            String id = sheet != null ? sheet.id : "";

            if (id.contains("pose_overlay"))
            {
                property = posedForm.getPoseOverlay();

                for (ValuePose vp : form.additionalOverlays)
                {
                    if (id.endsWith(vp.getId()))
                    {
                        property = vp;
                        break;
                    }
                }
            }
            else
            {
                property = posedForm.getPose();
            }
        }

        PoseTransform pt = pose.getOrCreate(bone);

        if (isOverlay)
        {
            pt.fix = 1F;
        }

        Vector3f savedT = new Vector3f(pt.translate);
        Vector3f savedR = new Vector3f(pt.rotate);
        Vector3f savedS = new Vector3f(pt.scale);
        Quaternionf savedQ = new Quaternionf(pt.quat);
        Transform.RotationMode savedMode = pt.rotationMode;
        boolean wasQuaternion = savedMode == Transform.RotationMode.QUATERNION;

        final ValuePose finalProp = property;

        try
        {
            if (wasQuaternion)
            {
                pt.setModeEuler();
            }

            Supplier<Matrix4f> sampler = GizmoDrag.freshSampler(() ->
            {
                replay.anchor.setRuntimeValue(replay.evaluateAnchor(tick));
                float transition = this.panel.getRunner().isRunning() && replayEditor.getContext() != null
                    ? replayEditor.getContext().getTransition()
                    : 0F;

                replay.properties.applyProperties(form, tick);

                if (finalProp != null)
                {
                    finalProp.set(pose);
                }

                RenderFrame.invalidate();

                Matrix4f mat = FilmMatrices.getBoneCompositeMatrix(
                    this.panel.getController().getEntities(), entity, replay, 0D, 0D, 0D, transition, this.getBonePath(bone), true
                );

                return mat == null ? new Matrix4f() : mat;
            });

            Vector3f targetScale = new Vector3f();
            Matrix3f targetRotation = orthonormalize(targetWorld.get3x3(new Matrix3f()), targetScale);
            Vector3f targetPosition = targetWorld.getTranslation(new Vector3f());

            Matrix3f jacobian = GizmoDrag.computeTranslateJacobian(pt, () -> sampler.get().getTranslation(new Vector3f()));

            if (Math.abs(jacobian.determinant()) > 1.0E-9F)
            {
                Vector3f error = targetPosition.sub(sampler.get().getTranslation(new Vector3f()), new Vector3f());

                jacobian.invert().transform(error);
                pt.translate.add(error);
            }

            for (int i = 0; i < 4; i++)
            {
                Matrix3f axes = GizmoDrag.computeRotateAxes(pt, sampler);

                if (Math.abs(axes.determinant()) > 1.0E-9F)
                {
                    Matrix3f current = orthonormalize(sampler.get().get3x3(new Matrix3f()), new Vector3f());
                    Matrix3f delta = new Matrix3f(targetRotation).mul(current.invert());
                    AxisAngle4f axisAngle = new AxisAngle4f().set(delta);
                    Vector3f error = new Vector3f(axisAngle.x, axisAngle.y, axisAngle.z).mul(axisAngle.angle);

                    axes.invert().transform(error);
                    pt.rotate.add(error);
                }

                solveScale(pt, sampler, targetScale);
            }

            if (wasQuaternion)
            {
                pt.quat.set(Matrices.toQuaternionZYXRadians(pt.rotate.x, pt.rotate.y, pt.rotate.z));
                pt.rotationMode = Transform.RotationMode.QUATERNION;
            }
            else
            {
                pt.rotationMode = Transform.RotationMode.EULER;
            }

            RenderFrame.invalidate();

            return true;
        }
        catch (Exception e)
        {
            pt.translate.set(savedT);
            pt.rotate.set(savedR);
            pt.scale.set(savedS);
            pt.quat.set(savedQ);
            pt.rotationMode = savedMode;

            return false;
        }
    }

    private static Matrix3f orthonormalize(Matrix3f basis, Vector3f scaleOut)
    {
        Vector3f column = new Vector3f();

        float sx = basis.getColumn(0, column).length();
        float sy = basis.getColumn(1, column).length();
        float sz = basis.getColumn(2, column).length();

        scaleOut.set(sx, sy, sz);

        if (sx > 1.0E-6F) basis.setColumn(0, basis.getColumn(0, column).div(sx));
        if (sy > 1.0E-6F) basis.setColumn(1, basis.getColumn(1, column).div(sy));
        if (sz > 1.0E-6F) basis.setColumn(2, basis.getColumn(2, column).div(sz));

        return basis;
    }

    private static void solveScale(Transform transform, Supplier<Matrix4f> sampler, Vector3f targetScale)
    {
        float epsilon = 0.05F;
        Vector3f current = new Vector3f();

        orthonormalize(sampler.get().get3x3(new Matrix3f()), current);

        for (int axis = 0; axis < 3; axis++)
        {
            float saved = transform.scale.get(axis);

            transform.scale.setComponent(axis, saved + epsilon);

            Vector3f perturbed = new Vector3f();

            orthonormalize(sampler.get().get3x3(new Matrix3f()), perturbed);
            transform.scale.setComponent(axis, saved);

            float slope = (perturbed.get(axis) - current.get(axis)) / epsilon;

            if (Math.abs(slope) > 1.0E-6F)
            {
                transform.scale.setComponent(axis, saved + (targetScale.get(axis) - current.get(axis)) / slope);
            }
        }
    }
}
