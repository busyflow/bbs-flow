package mchorse.bbs_mod.tools;

import mchorse.bbs_mod.cubic.spline.SplinePath;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.SplineForm;
import mchorse.bbs_mod.utils.manager.storage.CompressedDataStorage;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.File;

public class AnalyzeSpline
{
    public static void main(String[] args) throws Exception
    {
        File file = new File("C:/Users/jacqu/AppData/Roaming/ModrinthApp/profiles/Joofy - Gta/saves/City/bbs/films/33.6.dat");
        CompressedDataStorage storage = new CompressedDataStorage();
        MapType mapType = storage.load(file);
        ListType replays = mapType.getList("replays");

        MapType splineRep = replays.getMap(15);
        MapType formMap = splineRep.getMap("form");
        SplineForm spline = new SplineForm();
        spline.fromData(formMap);

        System.out.println("Spline closed: " + spline.closed());
        System.out.println("Spline points count: " + spline.points.getAllTyped().size());

        int idx = 0;
        for (var pt : spline.points.getAllTyped())
        {
            var t = spline.position(pt.getId()).translate;
            System.out.println(String.format("Point #%d (id=%s): [%.2f, %.2f, %.2f]", idx++, pt.getId(), t.x, t.y, t.z));
        }

        SplinePath path = SplinePath.create(spline, new Matrix4f());
        System.out.println("\nSpline path created! Total length: " + path.length() + " blocks");

        System.out.println("\n--- Sampling frame() along spline every 2% ---");
        Vector3f prevPos = null;
        for (float p = 0; p <= 100; p += 2)
        {
            Matrix4f frame = path.frame(p, false);
            Vector3f pos = frame.getTranslation(new Vector3f());
            Vector3f forward = frame.transformDirection(new Vector3f(0, 0, 1));
            String moveInfo = "";
            if (prevPos != null)
            {
                float stepDist = pos.distance(prevPos);
                moveInfo = String.format("step=%.2f", stepDist);
            }
            prevPos = new Vector3f(pos);
            System.out.println(String.format("P=%5.1f%%: pos=[%6.2f, %6.2f, %6.2f], fwd=[%5.2f, %5.2f, %5.2f] %s",
                p, pos.x, pos.y, pos.z, forward.x, forward.y, forward.z, moveInfo));
        }

        System.out.println("\n--- Checking Replay 17 vs Replay 16 Anchor values ---");
        MapType shark17 = replays.getMap(17);
        MapType shark16 = replays.getMap(16);
        System.out.println("Shark 17 anchor: " + shark17.getMap("keyframes").get("anchor"));
        System.out.println("Shark 16 anchor: " + shark16.getMap("keyframes").get("anchor"));
    }
}
