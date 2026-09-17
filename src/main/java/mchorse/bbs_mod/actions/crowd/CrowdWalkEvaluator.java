package mchorse.bbs_mod.actions.crowd;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Turns a crowd's walk keyframes into per-member positions. Shared by server playback and the
 * editor preview so both agree exactly.
 *
 * <p>The waypoints are one route, and a member is placed by asking where the route is at that
 * member's own time. Stagger is a delay in ticks, so a member who set off late is simply further
 * back along the same curve - it never has to catch up and there is nothing to catch up to. The
 * previous shape, where progress ran 0 to 1 within each segment and was clamped at both ends, is
 * what made the crowd gather at every waypoint and wait for its stragglers before setting off
 * again.</p>
 *
 * <p>What a member is given is a displacement - how far the route has moved from its first
 * waypoint - added to wherever that member was standing. Placing members around the waypoint
 * instead cannot work for painted ground, which is somewhere in particular and not free to be
 * re-centred.</p>
 */
public final class CrowdWalkEvaluator
{
    private static final double EPSILON = 1.0E-10D;

    /** How far ahead the route is sampled to work out which way a member is heading. */
    private static final float FACING_STEP = 1F;

    /**
     * The most a member can be behind the crowd, at full stagger.
     *
     * <p>A handful of ticks, not a share of the trip. Scaling it to the trip meant a long walk
     * spread the crowd over most of a second and left some still standing while the rest were
     * away - and the same stagger looked like a different crowd depending only on how far apart
     * the waypoints happened to be. A crowd setting off is a ragged instant, not a rolling
     * start.</p>
     */
    private static final float MAX_STAGGER_TICKS = 3F;

    private CrowdWalkEvaluator()
    {}

    public static Frame frame(Replay replay, float filmTick)
    {
        if (replay == null || replay.keyframes.crowdWalk.isEmpty())
        {
            return null;
        }

        KeyframeChannel<CrowdWalk> channel = replay.keyframes.crowdWalk;
        List<Keyframe<CrowdWalk>> list = channel.getKeyframes();
        int size = list.size();
        float tick = localTick(replay, filmTick);
        int i = indexAt(list, tick);

        CrowdWalk from = value(list, i);
        CrowdWalk to = value(list, Math.min(i + 1, size - 1));
        float startTick = list.get(i).getTick();
        float targetTick = list.get(Math.min(i + 1, size - 1)).getTick();


        Vec3d first = value(list, 0).position();
        Vec3d centre = positionAt(channel, tick);
        Vec3d ahead = positionAt(channel, tick + FACING_STEP);
        Vec3d direction = ahead.subtract(centre);

        direction = direction.lengthSquared() > EPSILON ? direction.normalize() : new Vec3d(0D, 0D, 1D);

        boolean moving = tick >= list.get(0).getTick()
            && tick < list.get(size - 1).getTick()
            && centre.squaredDistanceTo(ahead) > EPSILON;

        return new Frame(channel, list, from, to, first, centre, direction, tick, moving,
            startTick, targetTick);
    }

    /**
     * Where the route is at a given tick.
     *
     * <p>The curve is entirely the channel's own, so each waypoint's interpolation setting is
     * what decides how the crowd gets to the next one, and linear means linear - constant speed,
     * no rounding off at either end.</p>
     *
     * <p>There was a second easing here, a per-waypoint one that warped time on top of the
     * interpolation. Two controls over the same thing, and the hidden one won: a waypoint set to
     * linear still eased in and out, because the default of the knob nobody went looking for was
     * 0.75. The interpolation menu already carries every easing worth having.</p>
     */
    private static Vec3d positionAt(KeyframeChannel<CrowdWalk> channel, float tick)
    {
        CrowdWalk value = channel.interpolate(tick);

        return value == null ? Vec3d.ZERO : value.position();
    }

    /** The keyframe the given tick sits on or after. */
    private static int indexAt(List<Keyframe<CrowdWalk>> list, float tick)
    {
        int size = list.size();

        if (tick <= list.get(0).getTick())
        {
            return 0;
        }

        if (tick >= list.get(size - 1).getTick())
        {
            return size - 1;
        }

        int low = 0;
        int high = size - 1;

        while (low < high)
        {
            int mid = (low + high + 1) >>> 1;

            if (list.get(mid).getTick() <= tick)
            {
                low = mid;
            }
            else
            {
                high = mid - 1;
            }
        }

        return low;
    }

    private static CrowdWalk value(List<Keyframe<CrowdWalk>> list, int index)
    {
        CrowdWalk value = list.get(MathHelper.clamp(index, 0, list.size() - 1)).getValue();

        return value == null ? new CrowdWalk() : value;
    }

    /**
     * Where a member starting at {@code base} sits right now, written into {@code output}.
     * Allocation-free: the visual crowd calls this once per drawn member per frame.
     *
     * <p>{@code centre} is the middle of the crowd's own arrangement, which spread pushes members
     * away from. It has to be given rather than assumed to be the origin - a painted crowd's
     * positions are world coordinates, and scaling those about the world origin throws the crowd
     * a hundred blocks sideways and back again over the course of one walk.</p>
     */
    public static void memberPosition(Frame frame, int index, double baseX, double baseY, double baseZ,
        double centreX, double centreY, double centreZ, double[] output)
    {
        if (frame == null || output == null || output.length < 3)
        {
            return;
        }

        float tick = memberTick(frame, index);
        Vec3d at = positionAt(frame.channel, tick);
        double loosen = frame.from.spread * bulge(frame, tick) * 0.35D;

        output[0] = baseX + (baseX - centreX) * loosen + (at.x - frame.first.x);
        output[1] = baseY + (at.y - frame.first.y);
        output[2] = baseZ + (baseZ - centreZ) * loosen + (at.z - frame.first.z);
    }

    /** Allocating form of {@link #memberPosition}, for the live actor tier. */
    public static Vec3d member(Frame frame, int index, Vec3d base, Vec3d centre)
    {
        double[] output = new double[3];
        Vec3d at = base == null ? Vec3d.ZERO : base;
        Vec3d mid = centre == null ? Vec3d.ZERO : centre;

        memberPosition(frame, index, at.x, at.y, at.z, mid.x, mid.y, mid.z, output);

        return new Vec3d(output[0], output[1], output[2]);
    }

    /**
     * Which way a member is travelling, or null when it is standing still.
     *
     * <p>Read off the route a little ahead of where the member is rather than from how far it
     * moved last tick. A tick's worth of movement is a very short line, and near a waypoint it
     * can point almost anywhere; that is what had members turning around on the spot.</p>
     */
    public static Vec3d memberFacing(Frame frame, int index)
    {
        if (frame == null)
        {
            return null;
        }

        float tick = memberTick(frame, index);
        int size = frame.list.size();

        /* Standing at either end of the route is standing still, and a member that is not going
         * anywhere has no direction of travel to face - it keeps whatever it was given. */
        if (tick < frame.list.get(0).getTick() || tick >= frame.list.get(size - 1).getTick())
        {
            return null;
        }

        Vec3d here = positionAt(frame.channel, tick);
        Vec3d ahead = positionAt(frame.channel, tick + FACING_STEP);
        Vec3d direction = ahead.subtract(here);

        if (direction.lengthSquared() <= 1.0E-8D)
        {
            Vec3d behind = positionAt(frame.channel, tick - FACING_STEP);
            direction = here.subtract(behind);
        }

        return direction.lengthSquared() <= 1.0E-8D ? null : direction.normalize();
    }

    /**
     * The tick of the route a member is standing on.
     *
     * <p>Stagger is a delay, so a late member is further back along the same curve rather than
     * running its own compressed copy of the trip. Nobody is held at a waypoint waiting for the
     * rest to arrive, which is the whole difference between a crowd walking and a crowd
     * marching in formation.</p>
     */
    private static float memberTick(Frame frame, int index)
    {
        float stagger = MathHelper.clamp(frame.from.stagger, 0F, 1F);

        if (stagger <= 0F)
        {
            return frame.tick;
        }

        return frame.tick - (float) (stagger * offset(index) * MAX_STAGGER_TICKS);
    }

    /** How far through its current segment a member is, for spread to breathe on. */
    private static double bulge(Frame frame, float tick)
    {
        List<Keyframe<CrowdWalk>> list = frame.list;
        int i = indexAt(list, tick);

        if (i >= list.size() - 1)
        {
            return 0D;
        }

        float startTick = list.get(i).getTick();
        float span = list.get(i + 1).getTick() - startTick;

        if (span <= 0F)
        {
            return 0D;
        }

        return Math.sin(Math.PI * MathHelper.clamp((tick - startTick) / span, 0F, 1F));
    }

    /** Deterministic per-member value in [0, 1). Same member always leaves at the same moment. */
    private static double offset(int index)
    {
        int value = index * 0x9e3779b9;

        value ^= value >>> 16;
        value *= 0x7feb352d;
        value ^= value >>> 15;
        value *= 0x846ca68b;
        value ^= value >>> 16;

        return (value & 0x00ffffff) / 16777216D;
    }

    public static Vec3d replayOrigin(Replay replay, float filmTick)
    {
        float tick = localTick(replay, filmTick);

        return new Vec3d(
            replay.keyframes.x.interpolate(tick),
            replay.keyframes.y.interpolate(tick),
            replay.keyframes.z.interpolate(tick)
        );
    }

    private static float localTick(Replay replay, float filmTick)
    {
        int loop = replay.looping.get();

        if (loop <= 0)
        {
            return filmTick;
        }

        float wrapped = filmTick % loop;

        return wrapped < 0F ? wrapped + loop : wrapped;
    }

    public record Frame(KeyframeChannel<CrowdWalk> channel, List<Keyframe<CrowdWalk>> list,
                        CrowdWalk from, CrowdWalk to, Vec3d first,
                        Vec3d centre, Vec3d forward, float tick, boolean moving,
                        float startTick, float targetTick)
    {
        /** The waypoint whose settings govern this stretch of the route. */
        public CrowdWalk path()
        {
            return this.from;
        }

        public CrowdWalk target()
        {
            return this.to;
        }

        /** Crowd centre right now, before per-member stagger. */
        public Vec3d center()
        {
            return this.centre;
        }
    }
}
