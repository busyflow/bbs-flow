package mchorse.bbs_mod.camera.clips.misc;

import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.util.HashMap;
import java.util.Map;

/**
 * Camera clip for keyframing Realistic Biomes atmospheric parameters (starting with ground fog opacity).
 */
public class RealisticBiomesClip extends CameraClip
{
    public static final String GROUND_FOG_OPACITY = "ground_fog_opacity";

    public final KeyframeChannel<Double> groundFogOpacity = new KeyframeChannel<>(GROUND_FOG_OPACITY, KeyframeFactories.DOUBLE);

    public static Map<String, Double> getValues(ClipContext context)
    {
        return context.clipData.get("realistic_biomes_data", HashMap::new);
    }

    public RealisticBiomesClip()
    {
        this.add(this.groundFogOpacity);
    }

    @Override
    protected void applyClip(ClipContext context, Position position)
    {
        Map<String, Double> values = getValues(context);

        if (this.groundFogOpacity.hasEnabledKeyframes())
        {
            double val = this.groundFogOpacity.interpolate(context.relativeTick + context.transition);

            values.put(GROUND_FOG_OPACITY, Math.max(0.0D, Math.min(1.0D, val)));
        }
    }

    @Override
    protected Clip create()
    {
        return new RealisticBiomesClip();
    }
}
