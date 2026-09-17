package mchorse.bbs_mod.utils.keyframes.factories;

import mchorse.bbs_mod.actions.crowd.CrowdBehavior;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.interps.IInterp;

/** Which behaviour the crowd is running at this keyframe. Stepped - a mode is a hard cut, not a blend. */
public class CrowdBehaviorKeyframeFactory implements IKeyframeFactory<CrowdBehavior>
{
    @Override
    public CrowdBehavior fromData(BaseType data)
    {
        return CrowdBehavior.fromData(data);
    }

    @Override
    public BaseType toData(CrowdBehavior value)
    {
        return (value == null ? new CrowdBehavior() : value).toData();
    }

    @Override
    public CrowdBehavior createEmpty()
    {
        return new CrowdBehavior();
    }

    @Override
    public CrowdBehavior copy(CrowdBehavior value)
    {
        return value == null ? new CrowdBehavior() : value.copy();
    }

    @Override
    public CrowdBehavior interpolate(CrowdBehavior preA, CrowdBehavior av, CrowdBehavior bv, CrowdBehavior postB,
        IInterp interpolation, float x)
    {
        /* Held, not blended: the behaviour is whatever the last keyframe said until the next one. */
        return av == null ? new CrowdBehavior() : av.copy();
    }
}
