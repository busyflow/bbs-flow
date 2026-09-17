package mchorse.bbs_mod.utils.keyframes.factories;

import mchorse.bbs_mod.actions.crowd.CrowdTexture;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.interps.IInterp;

/** Texture changes are discrete so members never flicker between assignments. */
public class CrowdTextureKeyframeFactory implements IKeyframeFactory<CrowdTexture>
{
    @Override
    public CrowdTexture fromData(BaseType data)
    {
        return CrowdTexture.fromData(data);
    }

    @Override
    public BaseType toData(CrowdTexture value)
    {
        return (value == null ? new CrowdTexture() : value).toData();
    }

    @Override
    public CrowdTexture createEmpty()
    {
        return new CrowdTexture();
    }

    @Override
    public CrowdTexture copy(CrowdTexture value)
    {
        return value == null ? new CrowdTexture() : value.copy();
    }

    @Override
    public CrowdTexture interpolate(CrowdTexture preA, CrowdTexture a, CrowdTexture b,
        CrowdTexture postB, IInterp interpolation, float x)
    {
        return a == null ? new CrowdTexture() : a.copy();
    }
}
