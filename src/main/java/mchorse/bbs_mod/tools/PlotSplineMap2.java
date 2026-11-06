package mchorse.bbs_mod.tools;

import mchorse.bbs_mod.cubic.spline.SplineCurve;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class PlotSplineMap2
{
    public static void main(String[] args)
    {
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

        List<Vector3f> pointsList = List.of(pts);
        List<Vector3f> samples = SplineCurve.sample(pointsList, 100, true);

        float totalDist = 0;
        for (int i = 0; i < samples.size() - 1; i++) totalDist += samples.get(i).distance(samples.get(i + 1));

        float running = 0;
        for (int i = 0; i < samples.size() - 1; i++)
        {
            float d = samples.get(i).distance(samples.get(i + 1));
            running += d;
            float percent = (running / totalDist) * 100f;
            if (percent >= 13f && percent <= 45f && (i % 5 == 0))
            {
                System.out.println(String.format("  P=%5.1f%%: [%6.2f, %6.2f]", percent, samples.get(i+1).x, samples.get(i+1).z));
            }
        }
    }
}
