package mchorse.bbs_mod.utils.keyframes.factories;

/**
 * A replay id selected by a Crowd look keyframe. The value itself is discrete;
 * CrowdFormRuntimeManager uses the keyframe segment's interpolation to blend the
 * positions of the two selected replay targets.
 */
public class CrowdLookTargetKeyframeFactory extends StringKeyframeFactory
{
    @Override
    public boolean isStepped()
    {
        return false;
    }
}
