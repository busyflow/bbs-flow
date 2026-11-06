package mchorse.bbs_mod.tools;

import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.manager.storage.CompressedDataStorage;

import java.io.File;

public class InspectSharkTracks
{
    public static void main(String[] args) throws Exception
    {
        File file = new File("C:/Users/jacqu/AppData/Roaming/ModrinthApp/profiles/Joofy - Gta/saves/City/bbs/films/33.6.dat");
        CompressedDataStorage storage = new CompressedDataStorage();
        MapType mapType = storage.load(file);
        ListType replays = mapType.getList("replays");

        MapType shark = replays.getMap(17);
        System.out.println("Shark label: " + shark.getString("label"));
        System.out.println("Shark id: " + shark.getString("id"));

        MapType kfs = shark.getMap("keyframes");
        for (String key : kfs.keys())
        {
            MapType ch = kfs.getMap(key);
            ListType list = ch.getList("keyframes");
            if (list.size() > 0)
            {
                System.out.println("Channel '" + key + "' (" + list.size() + " kfs):");
                for (int i = 0; i < list.size(); i++)
                {
                    MapType kf = list.getMap(i);
                    System.out.println("  " + kf.getDouble("tick") + " -> " + kf.get("value"));
                }
            }
        }

        // Also check "properties"
        if (shark.has("properties"))
        {
            MapType props = shark.getMap("properties");
            System.out.println("Properties: " + DataToString.toString(props, true));
        }
    }
}
