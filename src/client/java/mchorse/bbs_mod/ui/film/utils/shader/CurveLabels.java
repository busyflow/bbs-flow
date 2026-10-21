package mchorse.bbs_mod.ui.film.utils.shader;

import mchorse.bbs_mod.camera.clips.misc.CurveClip;
import mchorse.bbs_mod.utils.iris.ShaderOptions;

import java.util.Collections;
import java.util.Map;

/**
 * What a track of a curve clip is called: the world curve's name, or the name the loaded pack's
 * translations give the option. The translations are read again after every load of the pack.
 */
public final class CurveLabels
{
    private static int revision = -1;
    private static Map<String, String> lang = Collections.emptyMap();

    private CurveLabels()
    {}

    public static String of(String channelId)
    {
        WorldCurve curve = WorldCurve.get(channelId);

        if (curve != null)
        {
            return curve.label.get();
        }

        if (!channelId.startsWith(CurveClip.SHADER_CURVES_PREFIX))
        {
            return channelId;
        }

        String optionId = channelId.substring(CurveClip.SHADER_CURVES_PREFIX.length());

        return lang().getOrDefault("option." + optionId, optionId);
    }

    private static Map<String, String> lang()
    {
        int current = ShaderOptions.revision();

        if (current != revision)
        {
            revision = current;

            try
            {
                lang = ShaderOptions.lang();
            }
            catch (LinkageError e)
            {
                lang = Collections.emptyMap();
            }
        }

        return lang;
    }
}
