package mchorse.bbs_mod.tools;

import mchorse.bbs_mod.cubic.CubicLoader;
import mchorse.bbs_mod.math.molang.MolangParser;

import java.io.File;
import java.io.FileInputStream;

public class TestSplashBbsLoad
{
    public static void main(String[] args)
    {
        File file = new File("C:/Users/jacqu/AppData/Roaming/ModrinthApp/profiles/Joofy - Gta/config/bbs/assets/models/water_splash/model.bbs.json");
        try (FileInputStream fis = new FileInputStream(file))
        {
            MolangParser parser = new MolangParser();
            CubicLoader loader = new CubicLoader();
            CubicLoader.LoadingInfo info = loader.load(parser, fis, file.getAbsolutePath());
            System.out.println("Model loaded: " + (info.model != null));
            if (info.model != null)
            {
                System.out.println("Top groups count: " + info.model.topGroups.size());
            }
            if (info.animations != null)
            {
                System.out.println("Animations count: " + info.animations.getAll().size());
                for (var a : info.animations.getAll())
                {
                    System.out.println("  Anim: " + a.id + " (length: " + a.getLength() + ")");
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
