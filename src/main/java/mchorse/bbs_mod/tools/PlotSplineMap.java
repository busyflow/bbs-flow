package mchorse.bbs_mod.tools;

import mchorse.bbs_mod.cubic.spline.SplineCurve;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class PlotSplineMap
{
    public static void main(String[] args)
    {
        // Points from 33.6.dat Replay #15:
        // [0] 24b12040: [0.0, 0.0]
        // [1] 316ba31c: [-11.80, -3.10]
        // [2] 449909dd: [-18.26, -4.72]
        // [3] 95c96bb9: [-19.25, -6.97]
        // [4] fdce0cc7: [-16.07, -7.23]
        // [5] 2ee6c443: [-0.19, 5.04]
        // [6] e86032b4: [-35.74, -28.35]
        // [7] a2c55b6f: [16.67, -41.21]

        Vector3f[] pts = new Vector3f[] {
            new Vector3f(0.0f, 0.0f, 0.0f),         // 0
            new Vector3f(-11.80f, 0.0f, -3.10f),    // 1
            new Vector3f(-18.26f, 0.0f, -4.72f),    // 2
            new Vector3f(-19.25f, 0.0f, -6.97f),    // 3
            new Vector3f(-16.07f, 0.0f, -7.23f),    // 4
            new Vector3f(-0.19f, 0.0f, 5.04f),      // 5
            new Vector3f(-35.74f, 0.0f, -28.35f),   // 6
            new Vector3f(16.67f, 0.0f, -41.21f)     // 7
        };

        System.out.println("CURRENT ORDER:");
        for (int i = 0; i < pts.length; i++)
        {
            int next = (i + 1) % pts.length;
            float dist = pts[i].distance(pts[next]);
            System.out.println(String.format("Pt %d [%.1f, %.1f] -> Pt %d [%.1f, %.1f]: dist=%.2f",
                i, pts[i].x, pts[i].z, next, pts[next].x, pts[next].z, dist));
        }

        // Evaluate Catmull-Rom curve with closed=true
        List<Vector3f> pointsList = List.of(pts);
        List<Vector3f> samples = SplineCurve.sample(pointsList, 50, true);
        System.out.println("\nSampled " + samples.size() + " points along curve.");

        // Check distance traveled
        float totalDist = 0;
        for (int i = 0; i < samples.size() - 1; i++)
        {
            totalDist += samples.get(i).distance(samples.get(i + 1));
        }
        System.out.println("Total curve perimeter: " + totalDist + " blocks");

        // Print first 5% of curve (where shark 17 travels!)
        System.out.println("\n--- SHARK 17 TRAVEL (0% to 5%): ---");
        float running = 0;
        for (int i = 0; i < samples.size() - 1; i++)
        {
            float d = samples.get(i).distance(samples.get(i + 1));
            running += d;
            float percent = (running / totalDist) * 100f;
            if (percent <= 15f)
            {
                System.out.println(String.format("  P=%5.2f%%: [%.2f, %.2f]", percent, samples.get(i+1).x, samples.get(i+1).z));
            }
        }
    }
}
