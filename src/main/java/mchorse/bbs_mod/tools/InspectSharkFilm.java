package mchorse.bbs_mod.tools;

import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.manager.storage.CompressedDataStorage;

import java.io.File;

public class InspectSharkFilm
{
    public static void main(String[] args) throws Exception
    {
        File file = new File("C:/Users/jacqu/AppData/Roaming/ModrinthApp/profiles/Joofy - Gta/saves/City/bbs/films/33.6.dat");
        CompressedDataStorage storage = new CompressedDataStorage();
        MapType mapType = storage.load(file);
        ListType replays = mapType.getList("replays");

        MapType splineRep = replays.getMap(15);
        System.out.println("SPLINE REPLAY #15 POSITION KEYFRAMES:");
        MapType kfs = splineRep.getMap("keyframes");
        for (String ch : new String[] {"x", "y", "z", "yaw", "pitch", "bodyYaw"})
        {
            if (kfs.has(ch))
            {
                ListType list = kfs.getMap(ch).getList("keyframes");
                System.out.println("Channel " + ch + " (" + list.size() + " kfs):");
                for (int i = 0; i < list.size(); i++)
                {
                    System.out.println("  " + list.get(i));
                }
            }
        }

        // Also check ALL replays for any other sharks or splines
        System.out.println("\nALL SHARKS IN FILM:");
        for (int i = 0; i < replays.size(); i++)
        {
            MapType rep = replays.getMap(i);
            String label = rep.getString("label");
            String formModel = rep.has("form") ? rep.getMap("form").getString("model") : "";
            if (formModel.contains("Shark") || label.toLowerCase().contains("shark"))
            {
                System.out.println("Replay #" + i + " id=" + rep.getString("id") + " label='" + label + "':");
                MapType rkfs = rep.getMap("keyframes");
                if (rkfs.has("anchor"))
                {
                    ListType aList = rkfs.getMap("anchor").getList("keyframes");
                    System.out.println("  Anchor kfs (" + aList.size() + "):");
                    for (int k = 0; k < aList.size(); k++)
                    {
                        System.out.println("    " + aList.get(k));
                    }
                }
            }
        }
    }
}
