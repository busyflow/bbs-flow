package mchorse.bbs_mod.ui.film.utils.shader;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.clips.misc.CurveClip;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.iris.ShaderCurves;
import net.minecraft.client.MinecraftClient;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * A curve of the world rather than of a shader pack (time of day, weather and the like); it works
 * with shaders off too.
 */
public class WorldCurve
{
    /** The curves of the world, in the order the curve picker lists them */
    public static final List<WorldCurve> ALL = create();

    public final String id;
    public final IKey label;
    public final Icon icon;

    /** The world's current value in the curve's own units, which a new curve starts from */
    public Supplier<Double> value;

    /** For a colour curve: the current colour, shown as a swatch instead of a value */
    public IntSupplier color;

    private static List<WorldCurve> create()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        return List.of(
            new WorldCurve(ShaderCurves.SUN_ROTATION, UIKeys.CAMERA_PANELS_CURVES_SUN_ROTATION, Icons.SUN)
                .value(() -> mc.world == null ? null : (mc.world.getTimeOfDay() % 24000L) / 1000D),
            new WorldCurve(ShaderCurves.SUN_HORIZONTAL_ROTATION, UIKeys.CAMERA_PANELS_CURVES_SUN_HORIZONTAL_ROTATION, Icons.ORBIT)
                .value(() -> (double) BBSRendering.getSunHorizontalRotation()),
            new WorldCurve(ShaderCurves.WEATHER, UIKeys.CAMERA_PANELS_CURVES_WEATHER, Icons.DROP)
                .value(() -> mc.world == null ? null : (double) mc.world.getRainGradient(1F)),
            new WorldCurve(ShaderCurves.BRIGHTNESS, UIKeys.CAMERA_PANELS_CURVES_BRIGHTNESS, Icons.LIGHT)
                .value(() -> mc.options.getGamma().getValue()),
            new WorldCurve(CurveClip.CHROMA_SKY_COLOR, UIKeys.CAMERA_PANELS_CURVES_CHROMA_SKY_COLOR, Icons.COLOR)
                .color(() -> BBSSettings.chromaSkyColor.get())
        );
    }

    /** The curve of the world with the given channel id, or null */
    public static WorldCurve get(String id)
    {
        for (WorldCurve curve : ALL)
        {
            if (curve.id.equals(id))
            {
                return curve;
            }
        }

        return null;
    }

    public WorldCurve(String id, IKey label, Icon icon)
    {
        this.id = id;
        this.label = label;
        this.icon = icon;
    }

    public WorldCurve value(Supplier<Double> value)
    {
        this.value = value;

        return this;
    }

    public WorldCurve color(IntSupplier color)
    {
        this.color = color;

        return this;
    }

    /** What a curve of it starts from: the world's value now (zero without a world), or colour */
    public Object start()
    {
        if (this.color != null)
        {
            return new Color().set(this.color.getAsInt(), true);
        }

        Double value = this.value.get();

        return value == null ? 0D : value;
    }
}
