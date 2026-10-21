package mchorse.bbs_mod.utils.presets;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.IOUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;

public abstract class DataManager
{
    private MapType data = new MapType();

    public void clear()
    {
        this.data = new MapType();
    }

    /** The presets listed for the group: its file's, unless a manager lists more alongside. */
    public MapType getData(String group)
    {
        return this.load(group);
    }

    /** What a listed preset is called in the list: its key, unless a manager keyed it apart from a same-named one. */
    public String getLabel(String group, String key)
    {
        return key;
    }

    /** The group's own file as loaded: what saving and removing write back. */
    protected final MapType load(String group)
    {
        MapType newData;

        if (data.has(group))
        {
            return data.getMap(group);
        }

        newData = new MapType();

        try (InputStream stream = BBSMod.getProvider().getAsset(getFile(group)))
        {
            newData = DataToString.mapFromString(IOUtils.readText(stream));
        }
        catch (FileNotFoundException e)
        {}
        catch (Exception e)
        {
            e.printStackTrace();
        }

        data.put(group, newData);

        return newData;
    }

    public void saveData(String group, String key, MapType pose)
    {
        if (group.isEmpty())
        {
            System.err.println("Can't save empty pose group!");

            return;
        }

        MapType newPoses = data.getMap(group);

        newPoses.put(key, pose);

        File file = BBSMod.getProvider().getFile(getFile(group));

        if (file != null)
        {
            file.getParentFile().mkdirs();

            DataToString.writeSilently(file, newPoses, true);
        }
    }

    public boolean removeData(String group, String key)
    {
        if (group.isEmpty())
        {
            return false;
        }

        MapType presets = this.load(group);
        File file = BBSMod.getProvider().getFile(this.getFile(group));

        if (!presets.has(key) || file == null)
        {
            return false;
        }

        MapType updated = (MapType) presets.copy();
        updated.remove(key);
        file.getParentFile().mkdirs();

        if (!DataToString.writeSilently(file, updated, true))
        {
            return false;
        }

        presets.remove(key);

        return true;
    }

    protected abstract Link getFile(String group);
}
