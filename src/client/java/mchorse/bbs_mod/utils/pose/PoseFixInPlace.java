package mchorse.bbs_mod.utils.pose;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.RigBone;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.utils.ILivePoseHost.LivePose;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fixing bones in place: the fix goes to 1, so the model's animation no longer reaches them, and
 * the pose takes over whatever the animation was doing, so nothing on screen moves.
 *
 * <p>Measured, not derived: the bones are read off the live model as the frame on screen evaluates
 * them, then again with the fix at 1, and the difference goes into the pose. Whatever sits under or
 * over the pose — the actions, the config's default pose, an overlay — is accounted for the way the
 * render accounts for it. Only the channels are compared: IK, physics and limits act on top of the
 * pose and keep doing so.</p>
 */
public class PoseFixInPlace
{
    /** What one bone's pose value gets: the fix, plus the shift and the turn the animation gave it. */
    public static class Correction
    {
        private final Vector3f translate;
        private final Vector3f scale;
        private final PoseTransform total;
        private final PoseTurn.Before fixed;
        private final Quaternionf turn;
        private final Quaternionf target;

        private Correction(Vector3f translate, Vector3f scale, PoseTransform total, PoseTurn.Before fixed, Quaternionf turn, Quaternionf target)
        {
            this.translate = translate;
            this.scale = scale;
            this.total = total;
            this.fixed = fixed;
            this.turn = turn;
            this.target = target;
        }

        public void apply(PoseTransform value)
        {
            value.fix = 1F;
            value.translate.add(this.translate);
            value.scale.add(this.scale);

            PoseTurn.apply(value, this.total, this.fixed, this.turn, this.target);
        }
    }

    /** A bone's channels as evaluated: what has to survive the fix. */
    private record Channels(Vector3f translate, Vector3f scale, Quaternionf rotation)
    {
        static Channels of(RigBone bone)
        {
            return new Channels(new Vector3f(bone.getBoneTransform().translate), new Vector3f(bone.getBoneTransform().scale), bone.evaluatedRotation());
        }
    }

    /** Whether the pose behind {@code property} drives a model the fix can be measured on. */
    public static boolean supports(BaseValue property)
    {
        return property != null && FormUtils.getForm(property) instanceof ModelForm;
    }

    /**
     * The corrections that fix {@code bones} in place, by bone; null when the live pose drives no
     * model to measure. The live pose is left exactly as found — the caller lays the corrections
     * into the pose it edits, through its own undo.
     */
    public static Map<String, Correction> measure(LivePose live, Collection<String> bones)
    {
        if (live == null || !supports(live.value()) || !(live.value().get() instanceof Pose))
        {
            return null;
        }

        ModelForm form = (ModelForm) FormUtils.getForm(live.value());

        if (!(FormUtilsClient.getRenderer(form) instanceof ModelFormRenderer renderer))
        {
            return null;
        }

        /* As the render poses it: a state that animates the pose puts its value in first. */
        form.applyStates(live.transition());

        try
        {
            ModelInstance model = renderer.evaluateChannels(live.entity(), live.transition());

            if (model == null)
            {
                return null;
            }

            Map<String, Channels> shown = new LinkedHashMap<>();

            for (String bone : bones)
            {
                RigBone rig = model.model.getBone(bone);

                if (rig != null)
                {
                    shown.put(bone, Channels.of(rig));
                }
            }

            return measureFixed(renderer, model, live, (Pose) live.value().get(), shown);
        }
        finally
        {
            form.unapplyStates();
        }
    }

    private static Map<String, Correction> measureFixed(ModelFormRenderer renderer, ModelInstance model, LivePose live, Pose pose, Map<String, Channels> shown)
    {
        Map<String, Correction> corrections = new LinkedHashMap<>();
        /* The fix each bone had, null where the pose had no entry for it */
        Map<String, Float> fixes = new HashMap<>();

        try
        {
            for (String bone : shown.keySet())
            {
                PoseTransform transform = pose.get(bone);

                fixes.put(bone, transform == null ? null : transform.fix);
                pose.getOrCreate(bone).fix = 1F;
            }

            renderer.evaluateChannels(live.entity(), live.transition());

            Pose total = renderer.getPose();

            for (Map.Entry<String, Channels> entry : shown.entrySet())
            {
                RigBone rig = model.model.getBone(entry.getKey());
                Channels before = entry.getValue();
                PoseTurn.Before fixed = PoseTurn.Before.of(rig);
                Quaternionf turn = new Quaternionf(fixed.evaluated()).conjugate().mul(before.rotation());

                corrections.put(entry.getKey(), new Correction(
                    before.translate().sub(rig.getBoneTransform().translate, new Vector3f()),
                    before.scale().sub(rig.getBoneTransform().scale, new Vector3f()),
                    total.get(entry.getKey()), fixed, turn, before.rotation()
                ));
            }
        }
        finally
        {
            for (Map.Entry<String, Float> entry : fixes.entrySet())
            {
                if (entry.getValue() == null)
                {
                    pose.transforms.remove(entry.getKey());
                }
                else
                {
                    pose.get(entry.getKey()).fix = entry.getValue();
                }
            }

            /* The model holds the measurement now, not the frame: the next render evaluates afresh. */
            model.clearChannels();
        }

        return corrections;
    }
}
