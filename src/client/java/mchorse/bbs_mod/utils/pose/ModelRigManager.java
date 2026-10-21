package mchorse.bbs_mod.utils.pose;

import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.cubic.rig.RigPresets;
import mchorse.bbs_mod.cubic.rig.RigStep;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.presets.DataManager;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The rig presets of a model: {@code rig.json} in its folder. Listed with them, as presets of one
 * step each, is what the tabs of the steps saved before the rig tab took them over — their files
 * stay as they are and are only read. A new preset goes into {@code rig.json} alone.
 */
public class ModelRigManager extends DataManager
{
    public static final ModelRigManager INSTANCE = new ModelRigManager();

    /** The files of the old tabs, by the step each held. */
    private static final Map<RigStep, DataManager> LEGACY = new LinkedHashMap<>();

    static
    {
        LEGACY.put(RigStep.SHAKE, ModelShakeManager.INSTANCE);
        LEGACY.put(RigStep.IK, ModelIKManager.INSTANCE);
        LEGACY.put(RigStep.PHYSICS, ModelPhysicsManager.INSTANCE);
        LEGACY.put(RigStep.SPLINE, ModelSplineManager.INSTANCE);
        LEGACY.put(RigStep.LIMITS, ModelConstraintsManager.INSTANCE);
    }

    /** The list of every group: the rig's own presets and the old ones, under the names shown. */
    private final Map<String, MapType> lists = new HashMap<>();

    /** Where a listed old preset lives: its file and its name there. */
    private final Map<String, Map<String, Source>> sources = new HashMap<>();

    private record Source(DataManager manager, String key)
    {}

    @Override
    protected Link getFile(String group)
    {
        return Link.assets(ModelManager.MODELS_PREFIX + group + "/rig.json");
    }

    @Override
    public void clear()
    {
        super.clear();

        this.lists.clear();
        this.sources.clear();

        for (DataManager manager : LEGACY.values())
        {
            manager.clear();
        }
    }

    @Override
    public MapType getData(String group)
    {
        MapType list = this.lists.get(group);

        if (list != null)
        {
            return list;
        }

        MapType own = this.load(group);
        Map<String, Source> sources = new HashMap<>();

        list = new MapType();

        for (String key : own.keys())
        {
            list.put(key, own.getMap(key));
        }

        for (Map.Entry<RigStep, DataManager> entry : LEGACY.entrySet())
        {
            MapType old = entry.getValue().getData(group);

            for (String key : old.keys())
            {
                String name = key;

                /* A name already in the list gets a key of its own; the list still shows the old
                 * name (see getLabel), the row's icons tell the two apart. */
                for (int i = 1; list.has(name); i++)
                {
                    name = key + " (" + entry.getKey().title().get() + (i > 1 ? " " + i : "") + ")";
                }

                list.put(name, RigPresets.legacy(entry.getKey(), old.getMap(key)));
                sources.put(name, new Source(entry.getValue(), key));
            }
        }

        this.lists.put(group, list);
        this.sources.put(group, sources);

        return list;
    }

    @Override
    public String getLabel(String group, String key)
    {
        Map<String, Source> sources = this.sources.get(group);
        Source source = sources == null ? null : sources.get(key);

        return source == null ? key : source.key();
    }

    @Override
    public void saveData(String group, String key, MapType preset)
    {
        MapType list = this.getData(group);
        Source source = this.sources.get(group).remove(key);

        /* Saving over an old preset's name leaves the old file be: the new one goes to the rig's. */
        if (source != null)
        {
            list.remove(key);
        }

        super.saveData(group, key, preset);
        list.put(key, preset);
    }

    @Override
    public boolean removeData(String group, String key)
    {
        MapType list = this.getData(group);
        Source source = this.sources.get(group).get(key);
        boolean removed = source == null ? super.removeData(group, key) : source.manager().removeData(group, source.key());

        if (removed)
        {
            list.remove(key);
            this.sources.get(group).remove(key);
        }

        return removed;
    }
}
