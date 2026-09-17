package mchorse.bbs_mod.camera.clips;

import mchorse.bbs_mod.camera.clips.converters.IClipConverter;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.HashMap;
import java.util.Map;

public class ClipFactoryData
{
    public final Icon icon;
    public final int color;
    public final Map<Link, IClipConverter<? extends Clip, ? extends Clip>> converters = new HashMap<>();

    public ClipFactoryData(Icon icon, int color)
    {
        this.icon = icon;
        this.color = color & Colors.RGB;
    }

    /**
     * Whether this type is kept out of the editor's add-clip menu.
     *
     * <p>For types that must stay registered so older films still deserialize, but which nobody
     * should be able to make any more - a retired clip that does nothing would otherwise sit in
     * the menu offering itself.</p>
     */
    public boolean hidden;

    public ClipFactoryData hidden()
    {
        this.hidden = true;

        return this;
    }

    public ClipFactoryData withConverter(Link to, IClipConverter<? extends Clip, ? extends Clip> converter)
    {
        this.converters.put(to, converter);

        return this;
    }
}