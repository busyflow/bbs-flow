package mchorse.bbs_mod.film.replays.tracks.behaviours;

import mchorse.bbs_mod.film.replays.tracks.TrackBehaviour;
import mchorse.bbs_mod.film.replays.tracks.TrackContext;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.ParticleForm;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;

import java.util.List;

/**
 * A plain form property: the track drives the property's runtime value, which is what the form
 * reports while something is animating it. Off the end of the track the runtime value is dropped
 * and the property shows its own again.
 */
public class PropertyTrack implements TrackBehaviour
{
    @Override
    public void apply(TrackContext context, TrackId track, KeyframeChannel channel, float tick, float blend)
    {
        BaseValueBasic property = FormUtils.getProperty(context.root(), track.toKey());

        if (property == null)
        {
            return;
        }

        if (channel.isMuted())
        {
            if (blend >= 1F)
            {
                property.setRuntimeValue(null);
            }

            return;
        }

        KeyframeSegment segment = channel.find(tick);

        if (segment != null)
        {
            Object sampled = segment.createInterpolated(property.getOriginalValue());
            Object current = property.get();
            var factory = channel.getFactory();
            property.setRuntimeValue(blend >= 1F ? sampled : factory.blend(current, sampled, blend));

            if (property.getParent() instanceof ModelForm modelForm && modelForm.isSnowballModel() && property == modelForm.snowballImpact)
            {
                modelForm.snowballImpactKeyTick = findLatestBoolTick(channel, tick, true);
            }
            else if (property.getParent() instanceof ParticleForm particleForm && property == particleForm.paused)
            {
                particleForm.unpausedKeyTick = findLatestBoolTick(channel, tick, false);
            }
        }
        else if (blend >= 1F)
        {
            property.setRuntimeValue(null);

            if (property.getParent() instanceof ModelForm modelForm && modelForm.isSnowballModel() && property == modelForm.snowballImpact)
            {
                modelForm.snowballImpactKeyTick = Float.NaN;
            }
            else if (property.getParent() instanceof ParticleForm particleForm && property == particleForm.paused)
            {
                particleForm.unpausedKeyTick = Float.NaN;
            }
        }
    }

    private static float findLatestBoolTick(KeyframeChannel channel, float tick, boolean targetValue)
    {
        List<?> keyframes = channel.getKeyframes();

        for (int i = keyframes.size() - 1; i >= 0; i--)
        {
            Keyframe<?> keyframe = (Keyframe<?>) keyframes.get(i);

            if (keyframe.getTick() <= tick && Boolean.valueOf(targetValue).equals(keyframe.getValue()))
            {
                return keyframe.getTick();
            }
        }

        return Float.NaN;
    }

    @Override
    public void reset(Form root, TrackId track)
    {
        BaseValueBasic property = FormUtils.getProperty(root, track.toKey());

        /* A track whose path no longer resolves on this form (a removed body part, or a state authored
         * against another form) is skipped, not thrown on. */
        if (property != null)
        {
            property.setRuntimeValue(null);

            if (property.getParent() instanceof ModelForm modelForm && modelForm.isSnowballModel() && property == modelForm.snowballImpact)
            {
                modelForm.snowballImpactKeyTick = Float.NaN;
            }
            else if (property.getParent() instanceof ParticleForm particleForm && property == particleForm.paused)
            {
                particleForm.unpausedKeyTick = Float.NaN;
            }
        }
    }
}
