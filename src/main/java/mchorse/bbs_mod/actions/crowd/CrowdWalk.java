package mchorse.bbs_mod.actions.crowd;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * One waypoint on a crowd's walk. The crowd stands here at this keyframe's tick and travels
 * to the next keyframe's waypoint over the ticks between them, so speed is authored by moving
 * keyframes on the timeline rather than by typing a number.
 *
 * <p>A waypoint carries only what changes how the crowd gets there: how ragged it is about
 * setting off, and how far the formation is allowed to loosen on the way. How the crowd
 * accelerates is the keyframe's own interpolation, not a setting here - there used to be one,
 * and having two controls over the same thing meant the one nobody could see overrode the one
 * they had picked. Everything else about the crowd's shape stays on the form.</p>
 */
public class CrowdWalk
{
    public float x;
    public float y;
    public float z;

    /**
     * How ragged the crowd is about setting off, over a few ticks at most. 0 moves them as one
     * rigid block; 1 spreads them across three ticks, so some are a step behind the rest for the
     * whole walk and arrive a step late.
     *
     * <p>Deliberately a small number of ticks rather than a share of the trip. As a share, a long
     * walk left some members standing while the rest were already away, and the same setting
     * looked like a different crowd depending only on how far apart the waypoints were.</p>
     */
    public float stagger = 0.3F;

    /**
     * How much the formation is allowed to loosen mid-trip. The shape is exact at both ends
     * and widest halfway, so a crowd breathes while walking without losing its silhouette.
     */
    public float spread = 0.2F;

    public boolean run;
    public boolean faceTravel = true;
    public boolean terrainFollow = true;
    public boolean showPath = true;
    public boolean showPoint = true;

    public CrowdWalk copy()
    {
        CrowdWalk point = new CrowdWalk();

        point.x = this.x;
        point.y = this.y;
        point.z = this.z;
        point.stagger = this.stagger;
        point.spread = this.spread;
        point.run = this.run;
        point.faceTravel = this.faceTravel;
        point.terrainFollow = this.terrainFollow;
        point.showPath = this.showPath;
        point.showPoint = this.showPoint;

        return point;
    }

    public Vec3d position()
    {
        return new Vec3d(this.x, this.y, this.z);
    }

    public MapType toData()
    {
        MapType data = new MapType();

        data.putFloat("x", this.x);
        data.putFloat("y", this.y);
        data.putFloat("z", this.z);
        data.putFloat("stagger", this.stagger);
        data.putFloat("spread", this.spread);
        data.putBool("run", this.run);
        data.putBool("face", this.faceTravel);
        data.putBool("terrain", this.terrainFollow);
        data.putBool("show_path", this.showPath);
        data.putBool("show_point", this.showPoint);

        return data;
    }

    public static CrowdWalk fromData(BaseType data)
    {
        CrowdWalk point = new CrowdWalk();

        if (data == null || !data.isMap())
        {
            return point;
        }

        MapType map = data.asMap();

        point.x = finite(map.getFloat("x"), 0F, -1_000_000F, 1_000_000F);
        point.y = finite(map.getFloat("y"), 0F, -1_000_000F, 1_000_000F);
        point.z = finite(map.getFloat("z"), 0F, -1_000_000F, 1_000_000F);
        point.stagger = finite(map.getFloat("stagger", 0.3F), 0.3F, 0F, 1F);
        point.spread = finite(map.getFloat("spread", 0.2F), 0.2F, 0F, 1F);
        point.run = map.getBool("run", false);
        point.faceTravel = map.getBool("face", true);
        point.terrainFollow = map.getBool("terrain", true);
        point.showPath = map.getBool("show_path", true);
        point.showPoint = map.getBool("show_point", true);

        return point;
    }

    private static float finite(float value, float fallback, float min, float max)
    {
        return Float.isFinite(value) ? MathHelper.clamp(value, min, max) : fallback;
    }
}
