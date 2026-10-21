package mchorse.bbs_mod.cubic.shake;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.RigBone;
import mchorse.bbs_mod.cubic.rig.RigClaim.Hold;
import mchorse.bbs_mod.cubic.rig.RigOwners;
import mchorse.bbs_mod.cubic.rig.RigStep;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Vector3f;

import java.util.List;

/**
 * Layers each shaking bone's {@link ShakeControl} over the pose, as one more additive layer of it.
 *
 * <p>It runs where the pose is evaluated, before IK, physics and the limits, so everything after
 * it sees the bones where they are drawn: a physics chain swings off a shaking head, a limit clamps
 * a shake that went too far, and an IK chain whose target bone shakes trembles while it stays
 * solved. A bone whose rotation a solver owns loses its own rotational shake to the solve.</p>
 *
 * <p>The shake is a pure function of time — no state, nothing random — so the same tick looks the
 * same when it is scrubbed to, played through and exported.</p>
 */
public final class ModelShakeRuntime
{
    private static final double TICKS_PER_SECOND = 20D;

    /**
     * Noise cells per swing there and back. The noise turns around about 0.7 times per cell
     * (measured: 10.5 turns per second at 5 swings, where a sine of 5 Hz makes 10), so three cells
     * keep the frequency meaning what it says.
     */
    private static final double CELLS_PER_SWING = 3D;

    /** Roughness spreads over this many layers, each twice as fast as the last. */
    private static final int OCTAVES = 3;

    /**
     * The shake runs on fixed tempos this many to an octave and fades between the two around the
     * frequency asked for. A noise sampled at time times frequency would race through its whole
     * history the moment a keyframe changes the frequency late in a take; a fixed tempo never
     * moves, so changing the frequency only shifts the mix.
     */
    private static final int TEMPOS_PER_OCTAVE = 3;

    /** Noise channels set aside per component for its tempos, wider than the frequency's range. */
    private static final int TEMPO_SLOTS = 64;

    private static final float MIN_FREQUENCY = 0.01F;
    private static final float MAX_FREQUENCY = 100F;

    /* The noise channel of each component. Scale has only one: the bone swells and shrinks as a
     * whole, its amplitudes setting the proportions, rather than warping axis by axis. */
    private static final int TRANSLATE = 0;
    private static final int ROTATE = 3;
    private static final int SCALE = 6;

    private ModelShakeRuntime()
    {}

    /**
     * Shakes the form's bones on the model as it stands.
     *
     * @param ticks the time the shake is sampled at, in ticks (fractional)
     */
    public static void apply(IModel model, ModelForm form, double ticks)
    {
        if (model == null || form == null)
        {
            return;
        }

        ShakeControls controls = null;

        for (BaseValue value : form.bones.getAll())
        {
            if (!(value instanceof FormBone bone) || !bone.hasShake())
            {
                continue;
            }

            RigBone rig = model.getBone(bone.getId());

            if (rig == null)
            {
                continue;
            }

            if (controls == null)
            {
                controls = form.shake.get();
            }

            /* No control means nothing set yet: a freshly switched on bone shakes the default way. */
            ShakeControl control = controls.controls.getOrDefault(bone.getId(), ShakeControl.DEFAULT);

            if (control.strength != 0F)
            {
                shake(rig, control, ticks / TICKS_PER_SECOND, channels(bone.getId(), control.seed));
            }
        }
    }

    /**
     * The shake's hand on the form's bones (see {@link RigOwners}): every bone switched to shake, the
     * shake added on top of its pose. It acts while it has a strength, read live as the film keys it.
     */
    public static void claim(IModel model, ModelForm form, RigOwners owners)
    {
        ShakeControls controls = form.shake.get();

        for (BaseValue value : form.bones.getAll())
        {
            if (!(value instanceof FormBone bone) || !bone.hasShake())
            {
                continue;
            }

            String id = bone.getId();
            ShakeControl control = controls.controls.getOrDefault(id, ShakeControl.DEFAULT);

            owners.chain(RigStep.SHAKE, id, id, List.of(id), Hold.ADDS, true, control.strength != 0F && model.getBone(id) != null);
        }
    }

    private static void shake(RigBone bone, ShakeControl shake, double seconds, int channels)
    {
        float strength = shake.strength;
        Tempo tempo = new Tempo(shake.frequency, shake.roughness);
        Transform transform = bone.getBoneTransform();

        if (isMoving(shake.translate))
        {
            transform.translate.add(
                shake.translate.x * strength * tempo.noise(seconds, channels + TRANSLATE),
                shake.translate.y * strength * tempo.noise(seconds, channels + TRANSLATE + 1),
                shake.translate.z * strength * tempo.noise(seconds, channels + TRANSLATE + 2)
            );
        }

        if (isMoving(shake.rotate))
        {
            /* Added the way a pose layer adds its rotation: the channels for the readers of the
             * euler, and the same delta composed into the orientation the render follows. */
            float factor = strength * bone.fromDegrees();
            Vector3f delta = new Vector3f(
                shake.rotate.x * factor * tempo.noise(seconds, channels + ROTATE),
                shake.rotate.y * factor * tempo.noise(seconds, channels + ROTATE + 1),
                shake.rotate.z * factor * tempo.noise(seconds, channels + ROTATE + 2)
            );

            transform.rotate.add(delta);
            bone.composeOrient(bone.orientFromEuler(delta));
        }

        if (isMoving(shake.scale))
        {
            float swell = strength * tempo.noise(seconds, channels + SCALE);

            transform.scale.add(shake.scale.x * swell, shake.scale.y * swell, shake.scale.z * swell);
        }
    }

    private static boolean isMoving(Vector3f amplitude)
    {
        return amplitude.x != 0F || amplitude.y != 0F || amplitude.z != 0F;
    }

    /**
     * Where a bone's channels start: every bone, and every variant of it, shakes on its own
     * pattern, so two bones with the same settings never move in step.
     */
    private static int channels(String bone, int seed)
    {
        return (bone.hashCode() * 31 + seed) * (SCALE + 1) * OCTAVES;
    }

    /**
     * The shake's signal in roughly [-1, 1]: the main swing plus the roughness' faster layers,
     * scaled so that roughness changes the texture of the shake rather than its size.
     */
    private static float noise(double x, int channel, float roughness)
    {
        float sum = 0F;
        float norm = 0F;
        float weight = 1F;
        double scale = 1D;

        for (int i = 0; i < OCTAVES && weight > 0F; i++)
        {
            sum += weight * smoothNoise(x * scale, channel * OCTAVES + i);
            norm += weight * weight;
            weight *= roughness;
            scale *= 2D;
        }

        return sum / (float) Math.sqrt(norm);
    }

    /**
     * The two fixed tempos around a frequency and how much of each it takes. Their mix is scaled
     * back to the size of one noise, so the shake doesn't shrink between two tempos.
     */
    private static class Tempo
    {
        private final int step;
        private final float weight;
        private final float norm;
        private final float roughness;

        private Tempo(float frequency, float roughness)
        {
            double level = Math.log(Math.max(MIN_FREQUENCY, Math.min(MAX_FREQUENCY, frequency))) / Math.log(2D) * TEMPOS_PER_OCTAVE;
            double floor = Math.floor(level);

            this.step = (int) floor;
            this.weight = (float) (level - floor);
            this.norm = (float) Math.sqrt((1F - this.weight) * (1F - this.weight) + this.weight * this.weight);
            this.roughness = roughness;
        }

        private float noise(double seconds, int channel)
        {
            float slow = sample(seconds, channel, this.step);

            if (this.weight == 0F)
            {
                return slow;
            }

            float fast = sample(seconds, channel, this.step + 1);

            return ((1F - this.weight) * slow + this.weight * fast) / this.norm;
        }

        private float sample(double seconds, int channel, int step)
        {
            double x = seconds * Math.pow(2D, step / (double) TEMPOS_PER_OCTAVE) * CELLS_PER_SWING;

            return ModelShakeRuntime.noise(x, channel * TEMPO_SLOTS + step, this.roughness);
        }
    }

    /**
     * Value noise through random lattice values on a Catmull-Rom curve.
     *
     * <p>Not the camera shake clip's smoothstep: that one comes to a halt at every lattice point,
     * which a slow shake shows as moving, stopping and moving again. Here the curve passes through
     * each point still moving, so it only slows down where it actually turns around.</p>
     */
    private static float smoothNoise(double x, int channel)
    {
        double floor = Math.floor(x);
        int cell = (int) (long) floor;
        float t = (float) (x - floor);

        float p0 = hash(cell - 1, channel);
        float p1 = hash(cell, channel);
        float p2 = hash(cell + 1, channel);
        float p3 = hash(cell + 2, channel);

        return p1 + 0.5F * t * (p2 - p0 + t * (2F * p0 - 5F * p1 + 4F * p2 - p3 + t * (3F * (p1 - p2) + p3 - p0)));
    }

    /** A lattice point's value in [-1, 1), the same integer avalanche the camera shake clip uses. */
    private static float hash(int cell, int channel)
    {
        int h = cell * 374761393 + channel * 668265263;

        h = (h ^ (h >>> 13)) * 1274126177;
        h = h ^ (h >>> 16);

        return (h >>> 8) / (float) (1 << 24) * 2F - 1F;
    }
}
