package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * The gizmo target for one or more selected replays' placement in the world &mdash; where the
 * actors stand and which way they face &mdash; as opposed to a bone inside their form.
 *
 * <p>When multiple replays are selected, the gizmo sits at the combined average center of all
 * selected replays, and dragging or turning the gizmo moves/rotates all selected replays smoothly
 * together and shifts their trajectories cleanly.</p>
 */
public class UIReplayPropTransform extends UIPropTransform
{
    /**
     * What a record can be driven by: the move bars, planes and screen cube, plus the
     * rotation rings for yaw (Y) and pitch (X). No scale, no roll ring, no trackball or
     * view ring &mdash; those all need a third rotational degree of freedom to land in.
     */
    public static final Gizmo.HandleMask MASK = Gizmo.HandleMask.of(
        EnumSet.of(Gizmo.Op.MOVE, Gizmo.Op.SCREEN, Gizmo.Op.ROTATE),
        EnumSet.of(Axis.X, Axis.Y)
    );

    public static class ReplayEntry
    {
        public final Replay replay;
        public final IEntity entity;
        public final double startX;
        public final double startY;
        public final double startZ;
        public final double startYaw;
        public final double startPitch;
        public final double headYawOffset;
        public final double bodyYawOffset;

        public double[] origX;
        public double[] origY;
        public double[] origZ;
        public double[] origYaw;
        public double[] origHeadYaw;
        public double[] origBodyYaw;
        public double[] origPitch;
        private boolean channelsSnapshotted;

        public ReplayEntry(Replay replay, IEntity entity)
        {
            this.replay = replay;
            this.entity = entity;
            this.startX = entity.getX();
            this.startY = entity.getY();
            this.startZ = entity.getZ();
            this.startYaw = entity.getYaw();
            this.startPitch = entity.getPitch();
            this.headYawOffset = entity.getHeadYaw() - this.startYaw;
            this.bodyYawOffset = entity.getBodyYaw() - this.startYaw;
        }

        public void ensureChannelSnapshots()
        {
            if (this.channelsSnapshotted)
            {
                return;
            }

            this.channelsSnapshotted = true;
            this.origX = snapshotChannel(this.replay.keyframes.x);
            this.origY = snapshotChannel(this.replay.keyframes.y);
            this.origZ = snapshotChannel(this.replay.keyframes.z);
            this.origYaw = snapshotChannel(this.replay.keyframes.yaw);
            this.origHeadYaw = snapshotChannel(this.replay.keyframes.headYaw);
            this.origBodyYaw = snapshotChannel(this.replay.keyframes.bodyYaw);
            this.origPitch = snapshotChannel(this.replay.keyframes.pitch);
        }

        private static double[] snapshotChannel(KeyframeChannel<Double> channel)
        {
            List<Keyframe<Double>> kfs = channel.getKeyframes();
            double[] values = new double[kfs.size()];

            for (int i = 0; i < kfs.size(); i++)
            {
                Double v = kfs.get(i).getValue();

                values[i] = v == null ? 0D : v;
            }

            return values;
        }
    }

    private final Transform scratch = new Transform();
    private final List<ReplayEntry> entries = new ArrayList<>();

    private Replay replay;
    private IEntity entity;
    private float tick;
    private boolean running;

    private double startCenterX;
    private double startCenterY;
    private double startCenterZ;
    private double startPrimaryYaw;
    private double startPrimaryPitch;

    public Runnable onTransformChanged;

    public void setRunning(boolean running)
    {
        this.running = running;
    }

    @Override
    public boolean isRunning()
    {
        return this.running;
    }

    public UIReplayPropTransform()
    {
        /* One undo entry per gesture rather than per written channel: every write is
         * bracketed on the whole keyframes group, and the gesture's end closes the block
         * so consecutive drags stay separately undoable. This is also what tells
         * UIFilmUndoHandler to sync the change to the server. */
        this.callbacks(
            () -> this.notifyKeyframes(0),
            this::postNotifyKeyframes,
            () -> this.notifyKeyframes(IValueListener.FLAG_UNMERGEABLE)
        );

        this.setVisible(false);
    }

    private void notifyKeyframes(int flag)
    {
        if (!this.entries.isEmpty())
        {
            for (ReplayEntry entry : this.entries)
            {
                entry.replay.keyframes.preNotify(flag);
            }
        }
        else if (this.replay != null)
        {
            this.replay.keyframes.preNotify(flag);
        }
    }

    private void postNotifyKeyframes()
    {
        if (!this.entries.isEmpty())
        {
            for (ReplayEntry entry : this.entries)
            {
                entry.replay.keyframes.postNotify();
            }
        }
        else if (this.replay != null)
        {
            this.replay.keyframes.postNotify();
        }
    }

    public Replay getReplay()
    {
        return this.replay;
    }

    public List<ReplayEntry> getEntries()
    {
        return this.entries;
    }

    /**
     * Load the selected replays' placement at the playhead into the scratch transform,
     * positioning the gizmo at the combined center of all selected replays.
     */
    public void syncFromReplays(Replay primary, IEntity primaryEntity, List<Replay> selectedReplays, Map<String, IEntity> entityMap, float tick)
    {
        if (this.isEditing())
        {
            this.tick = tick;
            return;
        }

        this.replay = primary;
        this.entity = primaryEntity;
        this.tick = tick;
        this.entries.clear();

        if (primary == null || primaryEntity == null)
        {
            this.setTransform(null);
            return;
        }

        if (selectedReplays != null && entityMap != null)
        {
            for (Replay r : selectedReplays)
            {
                if (r == null)
                {
                    continue;
                }

                IEntity e = entityMap.get(r.getId());

                if (e != null)
                {
                    this.entries.add(new ReplayEntry(r, e));
                }
            }
        }

        boolean hasPrimary = false;

        for (ReplayEntry entry : this.entries)
        {
            if (entry.replay == primary)
            {
                hasPrimary = true;
                break;
            }
        }

        if (!hasPrimary)
        {
            this.entries.add(0, new ReplayEntry(primary, primaryEntity));
        }

        double sumX = 0D;
        double sumY = 0D;
        double sumZ = 0D;

        for (ReplayEntry entry : this.entries)
        {
            sumX += entry.startX;
            sumY += entry.startY;
            sumZ += entry.startZ;
        }

        int count = this.entries.size();

        this.startCenterX = sumX / count;
        this.startCenterY = sumY / count;
        this.startCenterZ = sumZ / count;
        this.startPrimaryYaw = primaryEntity.getYaw();
        this.startPrimaryPitch = primaryEntity.getPitch();

        this.scratch.rotationMode = Transform.RotationMode.EULER;
        this.scratch.translate.set((float) this.startCenterX, (float) this.startCenterY, (float) this.startCenterZ);
        this.scratch.rotate.set(MathUtils.toRad((float) this.startPrimaryPitch), MathUtils.toRad((float) -this.startPrimaryYaw), 0F);
        this.scratch.scale.set(1F, 1F, 1F);

        this.setTransform(this.scratch);
    }

    public void syncFromReplay(Replay replay, IEntity entity, float tick)
    {
        this.syncFromReplays(replay, entity, null, null, tick);
    }

    /**
     * Compute the world-space center of the selected replays for rendering and raycasting the gizmo.
     */
    public Vector3d getGizmoCenter(float transition)
    {
        if (this.isEditing() && !this.entries.isEmpty())
        {
            return new Vector3d(this.scratch.translate.x, this.scratch.translate.y, this.scratch.translate.z);
        }

        if (this.entries.size() > 1)
        {
            double sumX = 0D;
            double sumY = 0D;
            double sumZ = 0D;

            for (ReplayEntry entry : this.entries)
            {
                sumX += Lerps.lerp(entry.entity.getPrevX(), entry.entity.getX(), transition);
                sumY += Lerps.lerp(entry.entity.getPrevY(), entry.entity.getY(), transition);
                sumZ += Lerps.lerp(entry.entity.getPrevZ(), entry.entity.getZ(), transition);
            }

            int count = this.entries.size();

            return new Vector3d(sumX / count, sumY / count, sumZ / count);
        }

        if (this.entity != null)
        {
            return new Vector3d(
                Lerps.lerp(this.entity.getPrevX(), this.entity.getX(), transition),
                Lerps.lerp(this.entity.getPrevY(), this.entity.getY(), transition),
                Lerps.lerp(this.entity.getPrevZ(), this.entity.getZ(), transition)
            );
        }

        return new Vector3d(this.scratch.translate.x, this.scratch.translate.y, this.scratch.translate.z);
    }

    /**
     * Build the camera-relative placement matrix for the replay gizmo at the combined center of all selected replays.
     */
    public Matrix4f getPlacementMatrix(IEntity fallbackEntity, double cameraX, double cameraY, double cameraZ, float transition)
    {
        IEntity ref = this.entity != null ? this.entity : fallbackEntity;
        Vector3d center;

        if (ref != null && (this.entries.size() > 1 || this.isEditing()))
        {
            center = this.getGizmoCenter(transition);
        }
        else if (ref != null)
        {
            center = new Vector3d(
                Lerps.lerp(ref.getPrevX(), ref.getX(), transition),
                Lerps.lerp(ref.getPrevY(), ref.getY(), transition),
                Lerps.lerp(ref.getPrevZ(), ref.getZ(), transition)
            );
        }
        else
        {
            center = new Vector3d();
        }

        Matrix4f matrix = new Matrix4f();
        float bodyYaw = ref == null ? 0F : Lerps.lerp(ref.getPrevBodyYaw(), ref.getBodyYaw(), transition);

        matrix.translate((float) (center.x - cameraX), (float) (center.y - cameraY), (float) (center.z - cameraZ));
        matrix.rotateY(MathUtils.toRad(-bodyYaw));

        return matrix;
    }

    private float writeTick(Replay targetReplay)
    {
        if (targetReplay == null)
        {
            return this.tick;
        }

        int whole = Math.round(this.tick);

        if (mchorse.bbs_mod.BBSSettings.editorSnapToTicks.get() || this.isRunning())
        {
            return targetReplay.getTick(whole);
        }

        return targetReplay.getTick((int) this.tick) + (this.tick - (int) this.tick);
    }

    private static void applyChannelTransform(KeyframeChannel<Double> channel, double[] origValues, double delta, float tick, double targetValue, boolean isMulti)
    {
        List<Keyframe<Double>> kfs = channel.getKeyframes();

        if (isMulti && !kfs.isEmpty() && kfs.size() == origValues.length)
        {
            for (int i = 0; i < kfs.size(); i++)
            {
                kfs.get(i).setValue(origValues[i] + delta, true);
            }
        }
        else
        {
            channel.insert(tick, targetValue);
        }
    }

    @Override
    public boolean isRotationChannelOnly()
    {
        return true;
    }

    @Override
    public String readoutPrefix()
    {
        if (this.entries.size() > 1)
        {
            return this.entries.size() + " replays: ";
        }

        return this.replay == null ? null : this.replay.getName() + ": ";
    }

    @Override
    public void setT(Axis axis, double x, double y, double z)
    {
        if (this.replay == null && this.entries.isEmpty())
        {
            return;
        }

        this.preCallback();

        this.scratch.translate.set((float) x, (float) y, (float) z);

        double dx = x - this.startCenterX;
        double dy = y - this.startCenterY;
        double dz = z - this.startCenterZ;
        boolean isMulti = this.entries.size() > 1;

        for (ReplayEntry entry : this.entries)
        {
            if (isMulti) entry.ensureChannelSnapshots();

            double nx = entry.startX + dx;
            double ny = entry.startY + dy;
            double nz = entry.startZ + dz;
            float tick = this.writeTick(entry.replay);
            ReplayKeyframes keyframes = entry.replay.keyframes;

            applyChannelTransform(keyframes.x, entry.origX, dx, tick, nx, isMulti);
            applyChannelTransform(keyframes.y, entry.origY, dy, tick, ny, isMulti);
            applyChannelTransform(keyframes.z, entry.origZ, dz, tick, nz, isMulti);

            if (entry.entity != null)
            {
                entry.entity.setPosition(nx, ny, nz);
                entry.entity.setPrevX(nx);
                entry.entity.setPrevY(ny);
                entry.entity.setPrevZ(nz);
            }
        }

        this.postCallback();

        if (this.onTransformChanged != null)
        {
            this.onTransformChanged.run();
        }
    }

    @Override
    public void setS(Axis axis, double x, double y, double z)
    {
        /* A record has no size. */
    }

    @Override
    public void setR(Axis axis, double x, double y, double z)
    {
        if (this.replay == null && this.entries.isEmpty())
        {
            return;
        }

        this.preCallback();

        this.scratch.rotate.set(MathUtils.toRad((float) x), MathUtils.toRad((float) y), 0F);

        double newPrimaryYaw = -y;
        double dYaw = newPrimaryYaw - this.startPrimaryYaw;
        double dPitch = x - this.startPrimaryPitch;
        double rad = -MathUtils.toRad((float) dYaw);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);

        double curCenterX = this.scratch.translate.x;
        double curCenterZ = this.scratch.translate.z;
        boolean isMulti = this.entries.size() > 1;

        for (ReplayEntry entry : this.entries)
        {
            if (isMulti) entry.ensureChannelSnapshots();

            float tick = this.writeTick(entry.replay);
            ReplayKeyframes keyframes = entry.replay.keyframes;

            double nYaw = entry.startYaw + dYaw;
            double nHeadYaw = nYaw + entry.headYawOffset;
            double nBodyYaw = nYaw + entry.bodyYawOffset;
            double nPitch = entry.startPitch + dPitch;

            applyChannelTransform(keyframes.yaw, entry.origYaw, dYaw, tick, nYaw, isMulti);
            applyChannelTransform(keyframes.headYaw, entry.origHeadYaw, dYaw, tick, nHeadYaw, isMulti);
            applyChannelTransform(keyframes.bodyYaw, entry.origBodyYaw, dYaw, tick, nBodyYaw, isMulti);
            applyChannelTransform(keyframes.pitch, entry.origPitch, dPitch, tick, nPitch, isMulti);

            if (isMulti && Math.abs(dYaw) > 1E-5D)
            {
                double relX = entry.startX - this.startCenterX;
                double relZ = entry.startZ - this.startCenterZ;
                double rotX = relX * cos - relZ * sin;
                double rotZ = relX * sin + relZ * cos;
                double nx = curCenterX + rotX;
                double nz = curCenterZ + rotZ;
                double dx = nx - entry.startX;
                double dz = nz - entry.startZ;

                applyChannelTransform(keyframes.x, entry.origX, dx, tick, nx, true);
                applyChannelTransform(keyframes.z, entry.origZ, dz, tick, nz, true);

                if (entry.entity != null)
                {
                    double ny = entry.entity.getY();
                    entry.entity.setPosition(nx, ny, nz);
                    entry.entity.setPrevX(nx);
                    entry.entity.setPrevZ(nz);
                }
            }

            if (entry.entity != null)
            {
                entry.entity.setYaw((float) nYaw);
                entry.entity.setHeadYaw((float) nHeadYaw);
                entry.entity.setBodyYaw((float) nBodyYaw);
                entry.entity.setPitch((float) nPitch);
                entry.entity.setPrevYaw((float) nYaw);
                entry.entity.setPrevHeadYaw((float) nHeadYaw);
                entry.entity.setPrevBodyYaw((float) nBodyYaw);
                entry.entity.setPrevPitch((float) nPitch);
            }
        }

        this.postCallback();

        if (this.onTransformChanged != null)
        {
            this.onTransformChanged.run();
        }
    }
}
