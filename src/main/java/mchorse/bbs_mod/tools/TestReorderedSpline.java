package mchorse.bbs_mod.tools;

import mchorse.bbs_mod.cubic.spline.SplineCurve;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class TestReorderedSpline
{
    public static void main(String[] args)
    {
        // Original points:
        // [0] 24b12040: [0.0, 0.0]
        // [1] 316ba31c: [-11.80, -3.10]
        // [2] 449909dd: [-18.26, -4.72]
        // [3] 95c96bb9: [-19.25, -6.97]
        // [4] fdce0cc7: [-16.07, -7.23]
        // [5] 2ee6c443: [-0.19, 5.04]
        // [6] e86032b4: [-35.74, -28.35]
        // [7] a2c55b6f: [16.67, -41.21]

        Vector3f p0 = new Vector3f(0.0f, 0.0f, 0.0f);
        Vector3f p1 = new Vector3f(-11.80f, 0.0f, -3.10f);
        Vector3f p2 = new Vector3f(-18.26f, 0.0f, -4.72f);
        Vector3f p3 = new Vector3f(-19.25f, 0.0f, -6.97f);
        Vector3f p4 = new Vector3f(-16.07f, 0.0f, -7.23f);
        Vector3f p5 = new Vector3f(-0.19f, 0.0f, 5.04f);
        Vector3f p6 = new Vector3f(-35.74f, 0.0f, -28.35f);
        Vector3f p7 = new Vector3f(16.67f, 0.0f, -41.21f);

        // Natural geometric circuit:
        // 0 -> 1 -> 2 -> 3 -> 4 -> 6 -> 7 -> 5 -> back to 0!
        List<Vector3f> reordered = List.of(p0, p1, p2, p3, p4, p6, p7, p5);

        System.out.println("--- REORDERED SPLINE LEGS ---");
        for (int i = 0; i < reordered.size(); i++)
        {
            Vector3f a = reordered.get(i);
            Vector3f b = reordered.get((i + 1) % reordered.size());
            System.out.println(String.format("Leg %d -> %d: [%.1f, %.1f] to [%.1f, %.1f] (dist = %.2f m)",
                i, (i + 1) % reordered.size(), a.x, a.z, b.x, b.z, a.distance(b)));
        }

        List<Vector3f> samples = SplineCurve.sample(reordered, 100, true);
        float totalDist = 0;
        for (int i = 0; i < samples.size() - 1; i++) totalDist += samples.get(i).distance(samples.get(i + 1));
        System.out.println("\nTotal perimeter of reordered spline: " + totalDist + " m");

        // Check if there are any reversals or sharp turns in reordered spline
        boolean hasZigZag = false;
        Vector3f prevDir = null;
        for (int i = 0; i < samples.size() - 1; i++)
        {
            Vector3f dir = new Vector3f(samples.get(i + 1)).sub(samples.get(i));
            if (dir.length() > 0.001f) dir.normalize();
            if (prevDir != null)
            {
                float dot = prevDir.dot(dir);
                float angle = (float) Math.toDegrees(Math.acos(Math.max(-1f, Math.min(1f, dot))));
                if (angle > 60f)
                {
                    System.out.println(String.format("Sharp angle %.1f deg at sample %d", angle, i));
                    hasZigZag = true;
                }
            }
            prevDir = dir;
        }

        if (!hasZigZag)
        {
            System.out.println("SUCCESS: Reordered spline is 100% smooth with ZERO reversals or zig-zags!");
        }
    }
}
