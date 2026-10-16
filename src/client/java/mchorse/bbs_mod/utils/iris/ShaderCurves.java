package mchorse.bbs_mod.utils.iris;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.utils.glsl.GlslOption;
import net.irisshaders.iris.uniforms.custom.cached.CachedUniform;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shader pack options that curves can animate: the ones the loaded pack's programs turned into
 * uniforms ({@link IrisShaderCurves} does the turning while Iris loads the pack), and the values
 * curves set for them. Free of Iris types, so curve clips can use it without Iris installed.
 */
public class ShaderCurves
{
    public static final String BRIGHTNESS = "brightness";
    public static final String SUN_ROTATION = "sun_rotation";
    public static final String SUN_HORIZONTAL_ROTATION = "sun_horizontal_rotation";
    public static final String WEATHER = "weather";

    public static final String UNIFORM_IDENTIFIER = "bbs_";

    /** Options that became uniforms in the loaded pack, by name */
    public static Map<String, ShaderVariable> variableMap = new ConcurrentHashMap<>();

    /** Options of the loaded pack that have to stay as the pack has them, with the reason */
    static final Map<String, String> blocked = new ConcurrentHashMap<>();

    /** Options that can be animated */
    public static Set<String> getCurvable()
    {
        Set<String> curvable = new HashSet<>(variableMap.keySet());

        curvable.removeAll(blocked.keySet());

        return curvable;
    }

    public static boolean isBlocked(String name)
    {
        return blocked.containsKey(name);
    }

    /** Sets the value an animatable option's uniform has without a curve, from the option's value */
    public static void setDefault(String name, String value)
    {
        ShaderVariable variable = variableMap.get(name);
        Float parsed = parse(value);

        if (variable != null && parsed != null)
        {
            variable.defaultValue = parsed;
        }
    }

    /** A number of an option's value ("true"/"false" too), or null */
    public static Float parse(String value)
    {
        if (value == null)
        {
            return null;
        }

        String text = value.trim();

        if (text.equals("true"))
        {
            return 1F;
        }
        else if (text.equals("false"))
        {
            return 0F;
        }

        if (text.endsWith("f") || text.endsWith("F"))
        {
            text = text.substring(0, text.length() - 1);
        }

        try
        {
            return Float.parseFloat(text);
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    public static void addUniforms(List<CachedUniform> list)
    {
        BBSRendering.addUniforms(list, variableMap);
    }

    public static class ShaderVariable
    {
        public final String name;
        public final String uniformName;
        public final GlslOption.Kind kind;
        public final boolean integer;
        public float defaultValue;

        /** The value a curve sets for the next frame, if any */
        public Float value;

        public ShaderVariable(GlslOption option, String defaultValue)
        {
            Float parsed = parse(defaultValue);

            this.name = option.name;
            this.uniformName = UNIFORM_IDENTIFIER + option.name;
            this.kind = option.kind;
            this.integer = option.kind != GlslOption.Kind.FLOAT;
            this.defaultValue = parsed == null ? 0F : parsed;
        }

        public float getValue()
        {
            Float value = this.value;

            this.value = null;

            /* An option that has to stay as the pack has it ignores curves */
            if (value == null || isBlocked(this.name))
            {
                return this.defaultValue;
            }

            return value;
        }

        /** The uniform's value: an on/off option is on from half up */
        public int getIntValue()
        {
            float value = this.getValue();

            if (this.kind == GlslOption.Kind.BOOLEAN)
            {
                return value >= 0.5F ? 1 : 0;
            }

            return (int) value;
        }
    }
}
