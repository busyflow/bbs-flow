package mchorse.bbs_mod.settings.values.core;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;

/**
 * A value holding raw data, kept only so that Wemppy's physics addon still links.
 *
 * <p>Upstream deleted this on 31 Aug (01aeb52f5, "cleanup: dead code") and was right to: nothing
 * in BBS refers to it any more. But {@code bbs_physics 1.0-1.20.1-1.20.4} was compiled against a
 * BBS that had it, and ten of its classes name it - including ones reached while the client is
 * still starting up. With the class gone, the addon takes the game down at the entrypoint with
 * NoClassDefFoundError before a window ever opens.</p>
 *
 * <p>Restored rather than worked around because the alternative is asking someone to choose
 * between the physics addon and every upstream change since 28 August. It costs one file that
 * BBS itself never loads.</p>
 *
 * <p><b>Fork-local, and deliberately disposable.</b> The moment a build of the physics addon
 * exists that does not reference it, delete this file and the branch that carries it. It is not
 * upstream's and should never be sent there.</p>
 */
public class ValueData extends BaseValueBasic<BaseType>
{
    public ValueData(String id)
    {
        super(id, null);
    }

    @Override
    public BaseType toData()
    {
        return this.value == null ? null : this.value.copy();
    }

    @Override
    public void fromData(BaseType data)
    {
        this.value = data == null ? null : data.copy();
    }
}
