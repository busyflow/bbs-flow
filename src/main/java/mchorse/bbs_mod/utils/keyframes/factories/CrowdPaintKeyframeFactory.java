package mchorse.bbs_mod.utils.keyframes.factories;

import mchorse.bbs_mod.actions.crowd.CrowdPaint;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.interps.IInterp;

/** Keyframe factory for keyframed painted crowd formations. */
public class CrowdPaintKeyframeFactory implements IKeyframeFactory<CrowdPaint>
{
    @Override
    public CrowdPaint fromData(BaseType data)
    {
        return CrowdPaint.fromData(data);
    }

    @Override
    public BaseType toData(CrowdPaint value)
    {
        return (value == null ? new CrowdPaint() : value).toData();
    }

    @Override
    public CrowdPaint createEmpty()
    {
        return new CrowdPaint();
    }

    @Override
    public CrowdPaint copy(CrowdPaint value)
    {
        return value == null ? new CrowdPaint() : value.copy();
    }

    @Override
    public CrowdPaint interpolate(CrowdPaint preA, CrowdPaint a, CrowdPaint b, CrowdPaint postB,
        IInterp interpolation, float x)
    {
        if (a == null)
        {
            return b == null ? new CrowdPaint() : b.copy();
        }

        if (b == null || x <= 0F)
        {
            return a.copy();
        }

        if (x >= 1F)
        {
            return b.copy();
        }

        float t = (float) interpolation.interpolate(0D, 1D, x);
        CrowdPaint result = (t >= 0.5F ? b : a).copy();

        result.stagger = a.stagger + (b.stagger - a.stagger) * t;
        result.spread = a.spread + (b.spread - a.spread) * t;

        return result;
    }
}
