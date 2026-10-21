package mchorse.bbs_mod.cubic.shake;

import mchorse.bbs_mod.cubic.chains.ChainControls;

/**
 * The shake track's keyframe value: each shaking bone's {@link ShakeControl}, keyed by the bone.
 */
public class ShakeControls extends ChainControls<ShakeControl, ShakeControls>
{
    @Override
    protected ShakeControls createControls()
    {
        return new ShakeControls();
    }

    @Override
    protected ShakeControl createControl()
    {
        return new ShakeControl();
    }

    @Override
    protected String getDataKey()
    {
        return "shake";
    }
}
