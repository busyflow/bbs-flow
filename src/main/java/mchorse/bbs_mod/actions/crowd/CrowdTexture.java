package mchorse.bbs_mod.actions.crowd;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.resources.LinkUtils;

/** A discrete crowd texture state: one chosen texture or a deterministic random folder. */
public class CrowdTexture
{
    public boolean random;
    public Link texture;
    public Link folder;
    public boolean recursive;

    public CrowdTexture copy()
    {
        CrowdTexture value = new CrowdTexture();

        value.random = this.random;
        value.texture = LinkUtils.copy(this.texture);
        value.folder = LinkUtils.copy(this.folder);
        value.recursive = this.recursive;

        return value;
    }

    public MapType toData()
    {
        MapType data = new MapType();

        data.putBool("random", this.random);
        data.putBool("recursive", this.recursive);

        if (this.texture != null) data.put("texture", LinkUtils.toData(this.texture));
        if (this.folder != null) data.put("folder", LinkUtils.toData(this.folder));

        return data;
    }

    public static CrowdTexture fromData(BaseType data)
    {
        CrowdTexture value = new CrowdTexture();

        if (data == null || !data.isMap())
        {
            return value;
        }

        MapType map = data.asMap();

        value.random = map.getBool("random");
        value.recursive = map.getBool("recursive");
        value.texture = map.has("texture") ? LinkUtils.create(map.get("texture")) : null;
        value.folder = map.has("folder") ? LinkUtils.create(map.get("folder")) : null;

        return value;
    }
}
