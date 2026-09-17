package mchorse.bbs_mod.utils.keyframes.factories;

import mchorse.bbs_mod.actions.crowd.CrowdWalk;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.AutoBezier;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

/** A timeline-authored crowd waypoint. Only its position interpolates; walk settings are stepped. */
public class CrowdWalkKeyframeFactory implements IKeyframeFactory<CrowdWalk>
{
    @Override
    public CrowdWalk fromData(BaseType data)
    {
        return CrowdWalk.fromData(data);
    }

    @Override
    public BaseType toData(CrowdWalk value)
    {
        return (value == null ? new CrowdWalk() : value).toData();
    }

    @Override
    public CrowdWalk createEmpty()
    {
        return new CrowdWalk();
    }

    @Override
    public CrowdWalk copy(CrowdWalk value)
    {
        return value == null ? new CrowdWalk() : value.copy();
    }

    @Override
    public CrowdWalk interpolate(Keyframe<CrowdWalk> preA, Keyframe<CrowdWalk> a,
        Keyframe<CrowdWalk> b, Keyframe<CrowdWalk> postB, IInterp interpolation, float x)
    {
        CrowdWalk av = a.getValue();
        CrowdWalk bv = b.getValue();

        if (av == null || bv == null || av.position().squaredDistanceTo(bv.position()) <= 1.0E-10D)
        {
            return av == null ? new CrowdWalk() : av.copy();
        }

        CrowdWalk value = av.copy();
        CrowdWalk pre = preA.getValue() == null ? av : preA.getValue();
        CrowdWalk post = postB.getValue() == null ? bv : postB.getValue();

        if (interpolation.has(Interpolations.BEZIER))
        {
            /* This custom track has no three-axis handle editor. Preserve Bezier timing without
             * inventing invalid coordinate handles; AUTO modes below provide spatial curves. */
            double t = interpolation.interpolate(0D, 1D, x);
            value.x = (float) (av.x + (bv.x - av.x) * t);
            value.y = (float) (av.y + (bv.y - av.y) * t);
            value.z = (float) (av.z + (bv.z - av.z) * t);
        }
        else if (interpolation.has(Interpolations.AUTO) || interpolation.has(Interpolations.AUTO_CLAMPED))
        {
            boolean clamped = interpolation.has(Interpolations.AUTO_CLAMPED);

            value.x = (float) AutoBezier.get(pre.x, av.x, bv.x, post.x, preA.getTick(), a.getTick(), b.getTick(), postB.getTick(), clamped, x);
            value.y = (float) AutoBezier.get(pre.y, av.y, bv.y, post.y, preA.getTick(), a.getTick(), b.getTick(), postB.getTick(), clamped, x);
            value.z = (float) AutoBezier.get(pre.z, av.z, bv.z, post.z, preA.getTick(), a.getTick(), b.getTick(), postB.getTick(), clamped, x);
        }
        else
        {
            value.x = (float) interpolation.interpolate(IInterp.context.set(pre.x, av.x, bv.x, post.x, x));
            value.y = (float) interpolation.interpolate(IInterp.context.set(pre.y, av.y, bv.y, post.y, x));
            value.z = (float) interpolation.interpolate(IInterp.context.set(pre.z, av.z, bv.z, post.z, x));
        }

        value.x = clampSegment(value.x, av.x, bv.x);
        value.y = clampSegment(value.y, av.y, bv.y);
        value.z = clampSegment(value.z, av.z, bv.z);

        return value;
    }

    @Override
    public CrowdWalk interpolate(CrowdWalk preA, CrowdWalk a, CrowdWalk b, CrowdWalk postB, IInterp interpolation, float x)
    {
        if (a == null) return b == null ? new CrowdWalk() : b.copy();
        if (b == null) return a.copy();

        CrowdWalk value = a.copy();

        if (a.position().squaredDistanceTo(b.position()) > 1.0E-10D)
        {
            double t = interpolation.interpolate(0D, 1D, x);

            value.x = (float) (a.x + (b.x - a.x) * t);
            value.y = (float) (a.y + (b.y - a.y) * t);
            value.z = (float) (a.z + (b.z - a.z) * t);
        }

        return value;
    }

    private static float clampSegment(float value, float a, float b)
    {
        return Math.max(Math.min(a, b), Math.min(Math.max(a, b), value));
    }
}
