package mchorse.bbs_mod.film.replays.tracks.behaviours;

import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.tracks.TrackBehaviour;
import mchorse.bbs_mod.film.replays.tracks.TrackBlend;
import mchorse.bbs_mod.film.replays.tracks.TrackContext;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;

import java.util.HashSet;
import java.util.Set;

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

        KeyframeSegment segment = channel.find(tick);

        if (segment != null)
        {
            Object value;

            if (this.isSparsePoseTrack(track, channel))
            {
                Pose pose = this.interpolateSparseOverlay(channel, segment);

                if (blend < 1F)
                {
                    IKeyframeFactory factory = channel.getFactory();
                    Object from = factory.copy(property.get());
                    Object to = factory.copy(pose);

                    value = factory.copy(factory.interpolate(from, from, to, to, Interpolations.LINEAR, blend));
                }
                else
                {
                    value = pose;
                }
            }
            else
            {
                value = TrackBlend.value(channel, property.get(), segment, blend);
            }

            property.setRuntimeValue(value);
        }
        else if (blend >= 1F)
        {
            property.setRuntimeValue(null);
        }
    }

    private boolean isSparsePoseTrack(TrackId track, KeyframeChannel channel)
    {
        return channel != null && channel.getFactory() == KeyframeFactories.POSE
            && track.subject().startsWith("pose_overlay");
    }

    /**
     * Overlay keyframes are deliberately sparse: an arm left out of a later keyframe means
     * "keep its most recently authored overlay", not "return it to rest".  Resolve that meaning
     * from the channel itself for every render.  This is intentionally stateless: using the pose
     * left on screen was what made a keyframe leak across the whole film.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Pose interpolateSparseOverlay(KeyframeChannel channel, KeyframeSegment segment)
    {
        Keyframe<Pose> a = this.expanded(channel, segment.a);

        if (segment.isSame())
        {
            return a.getValue().copy();
        }

        Keyframe<Pose> preA = this.expanded(channel, segment.preA);
        Keyframe<Pose> b = this.expanded(channel, segment.b);
        Keyframe<Pose> postB = this.expanded(channel, segment.postB);

        return a.getFactory().copy(a.getFactory().interpolate(preA, a, b, postB, a.getInterpolation().getInterp(), segment.x));
    }

    private boolean isReset(Pose pose)
    {
        if (pose == null || pose.transforms.isEmpty())
        {
            return true;
        }

        for (PoseTransform transform : pose.transforms.values())
        {
            if (transform != null && !transform.isDefault())
            {
                return false;
            }
        }

        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Keyframe<Pose> expanded(KeyframeChannel channel, Keyframe<Pose> target)
    {
        if (target == null)
        {
            return null;
        }

        Pose targetPose = target.getValue();

        if (this.isReset(targetPose))
        {
            Keyframe<Pose> empty = new Keyframe<>(target.getId(), target.getFactory(), target.getTick(), new Pose());

            empty.getInterpolation().copy(target.getInterpolation());

            return empty;
        }

        int end = channel.indexOf(target);

        if (end < 0)
        {
            return target;
        }

        Pose pose = new Pose();
        Set<String> bones = new HashSet<>();

        for (int i = 0; i <= end; i++)
        {
            Pose p = (Pose) channel.get(i).getValue();

            if (p != null)
            {
                bones.addAll(p.transforms.keySet());
            }
        }

        for (String bone : bones)
        {
            for (int i = end; i >= 0; i--)
            {
                Pose p = (Pose) channel.get(i).getValue();

                if (i < end && this.isReset(p))
                {
                    break;
                }

                PoseTransform transform = p == null ? null : p.get(bone);

                if (transform != null)
                {
                    if (!transform.isDefault())
                    {
                        pose.transforms.put(bone, (PoseTransform) transform.copy());
                    }

                    break;
                }
            }
        }

        Keyframe<Pose> result = new Keyframe<>(target.getId(), target.getFactory(), target.getTick(), pose);

        result.getInterpolation().copy(target.getInterpolation());

        return result;
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
        }
    }
}
