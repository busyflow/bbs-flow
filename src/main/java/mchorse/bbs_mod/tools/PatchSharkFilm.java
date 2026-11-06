package mchorse.bbs_mod.tools;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.FloatType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.manager.storage.CompressedDataStorage;

import java.io.File;

public class PatchSharkFilm
{
    private static ListType createVec3(float x, float y, float z)
    {
        ListType list = new ListType();
        list.add(new FloatType(x));
        list.add(new FloatType(y));
        list.add(new FloatType(z));
        return list;
    }

    public static void main(String[] args) throws Exception
    {
        File originalFile = new File("C:/Users/jacqu/AppData/Roaming/ModrinthApp/profiles/Joofy - Gta/saves/City/bbs/films/33.6.dat");
        CompressedDataStorage storage = new CompressedDataStorage();
        MapType filmData = storage.load(originalFile);

        ListType replays = filmData.getList("replays");
        MapType splineRep = replays.getMap(15);
        MapType shark17 = replays.getMap(17);

        // 1. Spline points reorder: move point 5 (id="2ee6c443") to the end
        ListType points = splineRep.getMap("form").getList("points");
        System.out.println("Original points order:");
        for (int i = 0; i < points.size(); i++)
        {
            System.out.println("  " + i + ": " + points.getMap(i).getString("id"));
        }

        BaseType pt5 = points.get(5);
        points.remove(5);
        points.add(pt5);

        System.out.println("\nPatched points order:");
        for (int i = 0; i < points.size(); i++)
        {
            System.out.println("  " + i + ": " + points.getMap(i).getString("id"));
        }

        // 2. Fix Shark 17 Anchor Keyframes:
        MapType kfs = shark17.getMap("keyframes");
        ListType anchorKfs = kfs.getMap("anchor").getList("keyframes");

        // KF 0 @ 1182.0
        MapType kf0 = anchorKfs.getMap(0);
        MapType val0 = kf0.getMap("value");
        val0.putByte("horizontal", (byte) 1);
        MapType t0 = val0.getMap("transform");
        t0.put("t", createVec3(0.0f, -0.5933722f, 0.0f));
        t0.put("r", createVec3(0.0f, 0.0f, 0.0f));

        // KF 1 @ 1670.0 (extend from 1213.0)
        MapType kf1 = anchorKfs.getMap(1);
        kf1.putFloat("tick", 1670.0f);
        MapType val1 = kf1.getMap("value");
        val1.putByte("horizontal", (byte) 1);
        val1.putFloat("progress", 100.0f);
        MapType t1 = val1.getMap("transform");
        t1.put("t", createVec3(0.0f, -0.5933722f, 0.0f));
        t1.put("r", createVec3(0.0f, 0.0f, 0.0f));

        System.out.println("\nPatched Shark 17 Anchor KFs:");
        for (int i = 0; i < anchorKfs.size(); i++)
        {
            System.out.println("  " + anchorKfs.get(i));
        }

        // Save test file to check
        File testFile = new File("C:/Users/jacqu/AppData/Roaming/ModrinthApp/profiles/Joofy - Gta/saves/City/bbs/films/33.6_patched_test.dat");
        storage.save(testFile, filmData);
        System.out.println("\nSaved test file: " + testFile.getAbsolutePath() + " (size: " + testFile.length() + " bytes)");

        // Reload to verify
        MapType reloaded = storage.load(testFile);
        System.out.println("Reloaded successfully! Replays count: " + reloaded.getList("replays").size());

        // Clean up test file
        testFile.delete();
        System.out.println("Cleaned up test file.");
    }
}
