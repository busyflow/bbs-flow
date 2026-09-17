package mchorse.bbs_mod.actions.crowd;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import mchorse.bbs_mod.actions.types.area.ValueAreaCells;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import net.minecraft.util.math.MathHelper;

/**
 * A snapshot of hand-painted crowd ground at a keyframe tick.
 * Holds painted surface cells and transition settings (e.g. stagger, spread).
 */
public class CrowdPaint
{
    private final Long2IntOpenHashMap cells = new Long2IntOpenHashMap();

    public float stagger = 0.2F;
    public float spread = 0.15F;
    public boolean terrainFollow = true;
    public boolean run = false;

    public CrowdPaint()
    {}

    public CrowdPaint(Long2IntOpenHashMap source)
    {
        if (source != null)
        {
            this.cells.putAll(source);
        }
    }

    public Long2IntOpenHashMap getCells()
    {
        return this.cells;
    }

    public void setCells(Long2IntOpenHashMap source)
    {
        this.cells.clear();

        if (source != null)
        {
            this.cells.putAll(source);
        }
    }

    public boolean isEmpty()
    {
        return this.cells.isEmpty();
    }

    public int size()
    {
        return this.cells.size();
    }

    public CrowdPaint copy()
    {
        CrowdPaint copy = new CrowdPaint(this.cells);

        copy.stagger = this.stagger;
        copy.spread = this.spread;
        copy.terrainFollow = this.terrainFollow;
        copy.run = this.run;

        return copy;
    }

    public BaseType toData()
    {
        MapType map = new MapType();
        ListType list = new ListType();

        for (Long2IntMap.Entry entry : this.cells.long2IntEntrySet())
        {
            list.addInt(ValueAreaCells.keyX(entry.getLongKey()));
            list.addInt(entry.getIntValue());
            list.addInt(ValueAreaCells.keyZ(entry.getLongKey()));
        }

        map.put("cells", list);
        map.putFloat("stagger", this.stagger);
        map.putFloat("spread", this.spread);
        map.putBool("terrain", this.terrainFollow);
        map.putBool("run", this.run);

        return map;
    }

    public static CrowdPaint fromData(BaseType data)
    {
        CrowdPaint paint = new CrowdPaint();

        if (data == null || !data.isMap())
        {
            return paint;
        }

        MapType map = data.asMap();

        if (map.has("cells") && map.get("cells").isList())
        {
            ListType list = map.getList("cells");

            for (int i = 0; i + 2 < list.size(); i += 3)
            {
                paint.cells.put(ValueAreaCells.key(list.getInt(i), list.getInt(i + 2)), list.getInt(i + 1));
            }
        }

        paint.stagger = MathHelper.clamp(map.getFloat("stagger", 0.2F), 0F, 1F);
        paint.spread = MathHelper.clamp(map.getFloat("spread", 0.15F), 0F, 1F);
        paint.terrainFollow = map.getBool("terrain", true);
        paint.run = map.getBool("run", false);

        return paint;
    }
}
