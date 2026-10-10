package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.ui.framework.elements.input.UIDeltaPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.function.Consumer;

/** Delta editing with one undo snapshot for the whole value gesture. */
public abstract class UIKeyframePropTransform extends UIDeltaPropTransform
{
    private boolean applying;
    protected abstract UIKeyframes getKeyframes();

    @Override public boolean isUserEditing() { return this.applying || super.isUserEditing(); }

    @Override
    public boolean isRunning()
    {
        return this.getKeyframes() != null && this.getKeyframes().isRunning();
    }

    @Override
    public void setT(mchorse.bbs_mod.utils.Axis axis, double x, double y, double z)
    {
        if (this.isRunning())
        {
            this.preCallback();
            this.applyToTarget((t) -> t.translate.set((float) x, (float) y, (float) z));
            this.postCallback();
            this.syncTargetTransform();
            return;
        }

        super.setT(axis, x, y, z);
    }

    @Override
    public void setS(mchorse.bbs_mod.utils.Axis axis, double x, double y, double z)
    {
        if (this.isRunning())
        {
            this.preCallback();
            this.applyToTarget((t) -> t.scale.set((float) x, (float) y, (float) z));
            this.postCallback();
            this.syncTargetTransform();
            return;
        }

        super.setS(axis, x, y, z);
    }

    @Override
    public void setR(mchorse.bbs_mod.utils.Axis axis, double x, double y, double z)
    {
        if (this.isRunning())
        {
            float rx = mchorse.bbs_mod.utils.MathUtils.toRad((float) x);
            float ry = mchorse.bbs_mod.utils.MathUtils.toRad((float) y);
            float rz = mchorse.bbs_mod.utils.MathUtils.toRad((float) z);

            this.preCallback();
            this.applyToTarget((t) ->
            {
                if (t.rotationMode == Transform.RotationMode.QUATERNION)
                {
                    t.quat.set(mchorse.bbs_mod.utils.joml.Matrices.toQuaternionZYXRadians(rx, ry, rz));
                }
                else
                {
                    t.rotate.set(rx, ry, rz);
                }
            });
            this.postCallback();
            this.syncTargetTransform();
            return;
        }

        super.setR(axis, x, y, z);
    }

    @Override
    public void setRQuat(org.joml.Quaternionf quat)
    {
        if (this.isRunning())
        {
            this.preCallback();
            this.applyToTarget((t) ->
            {
                if (t.rotationMode == Transform.RotationMode.QUATERNION)
                {
                    t.quat.set(quat);
                }
                else
                {
                    mchorse.bbs_mod.utils.joml.Matrices.toCompatibleEulerZYXRadians(quat, t.rotate, t.rotate);
                }
            });
            this.postCallback();
            this.syncTargetTransform();
            return;
        }

        super.setRQuat(quat);
    }

    @Override
    protected void applyToTarget(Consumer<Transform> edit)
    {
        this.applying = true;
        try
        {
            this.applyToSelection(edit);
            /* The fields show a copy of the key's value, never a stored key: move it with the edit. */
            if (this.getTransform() != null) edit.accept(this.getTransform());
        }
        finally { this.applying = false; }
    }

    @Override public void beginGesture() { this.getKeyframes().beginValueGesture(); }
    @Override public void endGesture() { this.getKeyframes().endValueGesture(); }
    @Override public boolean cancelGesture()
    {
        this.getKeyframes().cancelValueGesture();
        this.syncTargetTransform();
        return true;
    }
}
