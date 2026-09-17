package mchorse.bbs_mod.actions.types.area;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;

/**
 * A painted set of surface columns: for each (x, z) the Y of the surface block that was painted.
 *
 * <p>Keyed on the packed column rather than on a boxed position, because a brush stroke over a
 * spawn area for a five-figure crowd paints tens of thousands of cells and every one of them is
 * looked up again on the next stroke and on every frame that draws the outline.</p>
 */
public class ValueAreaCells extends BaseValueBasic<Long2IntOpenHashMap>
{
    public ValueAreaCells(String id)
    {
        super(id, new Long2IntOpenHashMap());
    }

    public static long key(int x, int z)
    {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    public static int keyX(long key)
    {
        return (int) (key >> 32);
    }

    public static int keyZ(long key)
    {
        return (int) key;
    }

    public boolean isEmpty()
    {
        return this.get().isEmpty();
    }

    public boolean has(int x, int z)
    {
        return this.get().containsKey(key(x, z));
    }

    @Override
    public BaseType toData()
    {
        ListType list = new ListType();

        /* Flat triples rather than a map of stringified keys: the list is the whole point of the
         * clip and a big one is written on every film save. */
        for (Long2IntMap.Entry entry : this.value.long2IntEntrySet())
        {
            list.addInt(keyX(entry.getLongKey()));
            list.addInt(entry.getIntValue());
            list.addInt(keyZ(entry.getLongKey()));
        }

        return list;
    }

    @Override
    public void fromData(BaseType data)
    {
        this.value.clear();

        if (!data.isList())
        {
            return;
        }

        ListType list = data.asList();

        for (int i = 0; i + 2 < list.size(); i += 3)
        {
            this.value.put(key(list.getInt(i), list.getInt(i + 2)), list.getInt(i + 1));
        }
    }
}
