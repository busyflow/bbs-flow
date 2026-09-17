package mchorse.bbs_mod.film.crowds;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;

/** Stores a crowd's {@link CrowdArmor} in the value tree so it saves and loads with the film. */
public class ValueCrowdArmor extends BaseValueBasic<CrowdArmor>
{
    public ValueCrowdArmor(String id)
    {
        super(id, new CrowdArmor());
    }

    @Override
    public BaseType toData()
    {
        return this.value.toData();
    }

    @Override
    public void fromData(BaseType data)
    {
        this.value.fromData(data);
    }
}
