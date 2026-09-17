package mchorse.bbs_mod.actions.crowd;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import mchorse.bbs_mod.actions.types.area.ValueAreaCells;
import mchorse.bbs_mod.actions.types.crowd.CrowdPaintArea;
import mchorse.bbs_mod.film.crowds.Crowd;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Turns keyframed crowd paint formations into per-member positions and headings.
 *
 * <p>Handles smooth transitions between keyframed painted formations:
 * <ul>
 *   <li>1-to-1 non-crossing spatial matching so members fill target space evenly</li>
 *   <li>2D grid A* routing around unpainted obstacles and holes (e.g. donut hole)</li>
 *   <li>Path string-pulling shortcut smoothing for cinematic fluid walking</li>
 *   <li>Smooth arrival deceleration with no overshoot</li>
 * </ul>
 * </p>
 */
public final class CrowdPaintEvaluator
{
    private static final double EPSILON = 1.0E-6D;
    private static final int MAX_CACHE_SIZE = 50;

    private static final Map<String, ChannelPlan> CACHE = new LinkedHashMap<String, ChannelPlan>(MAX_CACHE_SIZE, 0.75F, true)
    {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ChannelPlan> eldest)
        {
            return this.size() > MAX_CACHE_SIZE;
        }
    };

    private CrowdPaintEvaluator()
    {}

    public static Frame frame(Replay replay, Crowd crowd, float filmTick, int count)
    {
        if (replay == null || replay.keyframes.crowdPaint.isEmpty() || count <= 0)
        {
            return null;
        }

        float tick = replay.getTick((int) filmTick);

        return frame(replay.keyframes.crowdPaint, replay.getId(), tick, count);
    }

    public static Frame frame(KeyframeChannel<CrowdPaint> channel, String cacheKeyPrefix, float tick, int count)
    {
        if (channel == null || channel.isEmpty() || count <= 0)
        {
            return null;
        }

        List<Keyframe<CrowdPaint>> list = channel.getKeyframes();
        int size = list.size();

        if (size == 0)
        {
            return null;
        }

        String prefix = cacheKeyPrefix == null ? "default" : cacheKeyPrefix;
        String cacheKey = channelKey(channel, prefix, count);

        ChannelPlan plan;
        synchronized (CACHE)
        {
            plan = CACHE.computeIfAbsent(cacheKey, (k) -> new ChannelPlan(channel, count));
        }

        int i = indexAt(list, tick);
        Keyframe<CrowdPaint> kfA = list.get(i);
        Keyframe<CrowdPaint> kfB = list.get(Math.min(i + 1, size - 1));

        float tickA = kfA.getTick();
        float tickB = kfB.getTick();
        CrowdPaint paintA = kfA.getValue() == null ? new CrowdPaint() : kfA.getValue();
        CrowdPaint paintB = kfB.getValue() == null ? new CrowdPaint() : kfB.getValue();

        if (size == 1 || tick <= tickA)
        {
            // Static formation at keyframe 0 (or keyframe A before transition)
            return new Frame(channel, kfA, paintA, null, plan, i, 0F, false, count);
        }

        if (i >= size - 1 || tick >= tickB)
        {
            // Static formation at keyframe B
            int targetIndex = Math.min(i + 1, size - 1);
            Keyframe<CrowdPaint> kfTarget = list.get(targetIndex);
            CrowdPaint paintTarget = kfTarget.getValue() == null ? new CrowdPaint() : kfTarget.getValue();

            return new Frame(channel, kfTarget, paintTarget, null, plan, targetIndex, 1F, false, count);
        }

        // Active transition from A to B
        float duration = tickB - tickA;
        float progress = duration > 0F ? MathHelper.clamp((tick - tickA) / duration, 0F, 1F) : 1F;

        IInterp interp = kfA.getInterpolation().getInterp();
        float easedProgress = (float) interp.interpolate(0D, 1D, progress);

        return new Frame(channel, kfA, paintA, paintB, plan, i, easedProgress, true, count);
    }

    private static String channelKey(KeyframeChannel<CrowdPaint> channel, String prefix, int count)
    {
        StringBuilder sb = new StringBuilder(prefix == null ? "default" : prefix);
        sb.append(':').append(count).append(':');

        List<Keyframe<CrowdPaint>> list = channel.getKeyframes();
        sb.append(list.size());

        for (Keyframe<CrowdPaint> kf : list)
        {
            sb.append('_').append(kf.getTick())
              .append('_').append(kf.getInterpolation().getKey());

            CrowdPaint val = kf.getValue();

            if (val != null)
            {
                sb.append('_').append(val.stagger)
                  .append('_').append(val.spread)
                  .append('_').append(val.size())
                  .append('_').append(val.getCells().hashCode());
            }
        }

        return sb.toString();
    }

    private static int indexAt(List<Keyframe<CrowdPaint>> list, float tick)
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

    private static class ChannelPlan
    {
        final int keyframeCount;
        final int memberCount;
        final int[][] slots;
        final TransitionData[] transitions;
        final Vec3d[][] staticPositions;
        final Vec3d[][] staticFacings;

        public ChannelPlan(KeyframeChannel<CrowdPaint> channel, int memberCount)
        {
            List<Keyframe<CrowdPaint>> list = channel.getKeyframes();
            int m = list.size();
            int n = Math.max(1, memberCount);

            this.keyframeCount = m;
            this.memberCount = n;
            this.slots = new int[m][n];
            this.transitions = new TransitionData[Math.max(0, m - 1)];
            this.staticPositions = new Vec3d[m][n];
            this.staticFacings = new Vec3d[m][n];

            if (m == 0)
            {
                return;
            }

            CrowdPaint paint0 = list.get(0).getValue() == null ? new CrowdPaint() : list.get(0).getValue();
            CrowdPaintArea area0 = new CrowdPaintArea(paint0.getCells());
            Vec3d[] currentPositions = new Vec3d[n];

            for (int k = 0; k < n; k++)
            {
                this.slots[0][k] = k;
                Vec3d pt = area0.point(k, n, 0);
                if (pt == null) pt = Vec3d.ZERO;
                currentPositions[k] = pt;
                this.staticPositions[0][k] = pt;
                this.staticFacings[0][k] = null;
            }

            for (int j = 0; j < m - 1; j++)
            {
                Keyframe<CrowdPaint> kfA = list.get(j);
                Keyframe<CrowdPaint> kfB = list.get(j + 1);

                CrowdPaint paintA = kfA.getValue() == null ? new CrowdPaint() : kfA.getValue();
                CrowdPaint paintB = kfB.getValue() == null ? new CrowdPaint() : kfB.getValue();

                CrowdPaintArea areaB = new CrowdPaintArea(paintB.getCells());
                Vec3d[] targetSlots = new Vec3d[n];

                for (int s = 0; s < n; s++)
                {
                    Vec3d pt = areaB.point(s, n, 0);
                    if (pt == null) pt = Vec3d.ZERO;
                    targetSlots[s] = pt;
                }

                int[] assignment = TransitionData.computeMatching(currentPositions, targetSlots, n);

                MemberRoute[] routes = new MemberRoute[n];
                Long2IntOpenHashMap cellsB = paintB.getCells();
                Vec3d[] nextPositions = new Vec3d[n];

                for (int k = 0; k < n; k++)
                {
                    int targetSlot = assignment[k];
                    this.slots[j + 1][k] = targetSlot;

                    Vec3d startPt = currentPositions[k];
                    Vec3d targetPt = targetSlots[targetSlot];
                    nextPositions[k] = targetPt;

                    routes[k] = TransitionData.computeRoute(startPt, targetPt, cellsB, k, paintA.stagger);
                    this.staticPositions[j + 1][k] = targetPt;
                    this.staticFacings[j + 1][k] = routes[k].getArrivalFacing();
                }

                this.transitions[j] = new TransitionData(routes);
                currentPositions = nextPositions;
            }
        }
    }

    public static class Frame
    {
        private final KeyframeChannel<CrowdPaint> channel;
        private final Keyframe<CrowdPaint> keyframe;
        private final CrowdPaint paintA;
        private final CrowdPaint paintB;
        private final ChannelPlan plan;
        private final int keyframeIndex;
        private final float progress;
        private final boolean moving;
        private final int count;

        public Frame(KeyframeChannel<CrowdPaint> channel, Keyframe<CrowdPaint> keyframe,
            CrowdPaint paintA, CrowdPaint paintB, ChannelPlan plan,
            int keyframeIndex, float progress, boolean moving, int count)
        {
            this.channel = channel;
            this.keyframe = keyframe;
            this.paintA = paintA;
            this.paintB = paintB;
            this.plan = plan;
            this.keyframeIndex = keyframeIndex;
            this.progress = progress;
            this.moving = moving;
            this.count = count;
        }

        public CrowdPaint path()
        {
            return this.paintA != null ? this.paintA : this.paintB;
        }

        public boolean moving()
        {
            return this.moving;
        }

        public boolean isMoving(int memberIndex)
        {
            if (!this.moving || this.plan == null || this.keyframeIndex < 0 || this.keyframeIndex >= this.plan.transitions.length)
            {
                return false;
            }

            TransitionData transition = this.plan.transitions[this.keyframeIndex];
            MemberRoute route = transition != null ? transition.getRoute(memberIndex) : null;

            if (route == null || route.totalDistance < EPSILON)
            {
                return false;
            }

            float t = route.memberProgress(this.progress);

            return t > 0F && t < 1F;
        }

        public void memberPosition(int index, double[] output)
        {
            if (output == null || output.length < 3)
            {
                return;
            }

            if (this.plan == null || index < 0 || index >= this.count)
            {
                output[0] = 0D;
                output[1] = 0D;
                output[2] = 0D;

                return;
            }

            if (!this.moving || this.keyframeIndex < 0 || this.keyframeIndex >= this.plan.transitions.length)
            {
                int kfIdx = MathHelper.clamp(this.keyframeIndex, 0, this.plan.keyframeCount - 1);
                Vec3d p = this.plan.staticPositions[kfIdx][index];

                if (p != null)
                {
                    output[0] = p.x;
                    output[1] = p.y;
                    output[2] = p.z;

                    return;
                }

                output[0] = 0D;
                output[1] = 0D;
                output[2] = 0D;

                return;
            }

            TransitionData transition = this.plan.transitions[this.keyframeIndex];
            MemberRoute route = transition != null ? transition.getRoute(index) : null;

            if (route == null)
            {
                int kfIdx = MathHelper.clamp(this.keyframeIndex, 0, this.plan.keyframeCount - 1);
                Vec3d p = this.plan.staticPositions[kfIdx][index];

                output[0] = p != null ? p.x : 0D;
                output[1] = p != null ? p.y : 0D;
                output[2] = p != null ? p.z : 0D;

                return;
            }

            route.evaluatePosition(this.progress, output);
        }

        public Vec3d memberFacing(int index)
        {
            if (this.plan == null || index < 0 || index >= this.count)
            {
                return null;
            }

            if (!this.moving || this.keyframeIndex < 0 || this.keyframeIndex >= this.plan.transitions.length)
            {
                int kfIdx = MathHelper.clamp(this.keyframeIndex, 0, this.plan.keyframeCount - 1);

                return this.plan.staticFacings[kfIdx][index];
            }

            TransitionData transition = this.plan.transitions[this.keyframeIndex];
            MemberRoute route = transition != null ? transition.getRoute(index) : null;

            if (route == null)
            {
                int kfIdx = MathHelper.clamp(this.keyframeIndex, 0, this.plan.keyframeCount - 1);

                return this.plan.staticFacings[kfIdx][index];
            }

            Vec3d facing = route.evaluateFacing(this.progress);

            return facing != null ? facing : route.getArrivalFacing();
        }
    }

    private static class TransitionData
    {
        private final MemberRoute[] routes;

        public TransitionData(MemberRoute[] routes)
        {
            this.routes = routes;
        }

        public MemberRoute getRoute(int index)
        {
            if (index < 0 || index >= this.routes.length)
            {
                return null;
            }

            return this.routes[index];
        }

        private static int[] computeMatching(Vec3d[] starts, Vec3d[] targets, int n)
        {
            int[] assignment = new int[n];
            boolean[] used = new boolean[n];

            double cxA = 0D, czA = 0D;
            double cxB = 0D, czB = 0D;

            for (int i = 0; i < n; i++)
            {
                cxA += starts[i].x;
                czA += starts[i].z;
                cxB += targets[i].x;
                czB += targets[i].z;
            }

            cxA /= n; czA /= n;
            cxB /= n; czB /= n;

            double dirX = cxB - cxA;
            double dirZ = czB - czA;
            double len = Math.hypot(dirX, dirZ);

            if (len > EPSILON)
            {
                dirX /= len;
                dirZ /= len;
            }
            else
            {
                dirX = 0D;
                dirZ = 1D;
            }

            List<Integer> order = new ArrayList<>(n);

            for (int i = 0; i < n; i++)
            {
                order.add(i);
            }

            final double fDirX = dirX;
            final double fDirZ = dirZ;

            order.sort((a, b) ->
            {
                double projA = starts[a].x * fDirX + starts[a].z * fDirZ;
                double projB = starts[b].x * fDirX + starts[b].z * fDirZ;

                return Double.compare(projB, projA);
            });

            for (int k : order)
            {
                int bestTarget = -1;
                double bestDist = Double.MAX_VALUE;

                for (int j = 0; j < n; j++)
                {
                    if (used[j])
                    {
                        continue;
                    }

                    double d = starts[k].squaredDistanceTo(targets[j]);

                    if (d < bestDist)
                    {
                        bestDist = d;
                        bestTarget = j;
                    }
                }

                if (bestTarget != -1)
                {
                    assignment[k] = bestTarget;
                    used[bestTarget] = true;
                }
            }

            // 2-opt uncrossing passes to eliminate intersecting paths
            for (int pass = 0; pass < 2; pass++)
            {
                for (int i = 0; i < n; i++)
                {
                    for (int j = i + 1; j < n; j++)
                    {
                        int ti = assignment[i];
                        int tj = assignment[j];

                        double curDist = starts[i].squaredDistanceTo(targets[ti]) + starts[j].squaredDistanceTo(targets[tj]);
                        double swapDist = starts[i].squaredDistanceTo(targets[tj]) + starts[j].squaredDistanceTo(targets[ti]);

                        if (swapDist < curDist - 0.01D)
                        {
                            assignment[i] = tj;
                            assignment[j] = ti;
                        }
                    }
                }
            }

            return assignment;
        }

        private static MemberRoute computeRoute(Vec3d start, Vec3d target, Long2IntOpenHashMap cellsB, int index, float stagger)
        {
            List<Vec3d> waypoints = new ArrayList<>();
            waypoints.add(start);

            int sx = MathHelper.floor(start.x);
            int sz = MathHelper.floor(start.z);
            int tx = MathHelper.floor(target.x);
            int tz = MathHelper.floor(target.z);

            boolean sInB = cellsB.containsKey(ValueAreaCells.key(sx, sz));
            int ex = sx;
            int ez = sz;
            Vec3d entryPoint = start;

            if (!sInB)
            {
                long entryKey = findFirstIntersection(sx, sz, tx, tz, cellsB);

                if (entryKey != Long.MIN_VALUE)
                {
                    ex = ValueAreaCells.keyX(entryKey);
                    ez = ValueAreaCells.keyZ(entryKey);
                    int ey = cellsB.get(entryKey);
                    entryPoint = new Vec3d(ex + 0.5D, ey + 1.0D, ez + 0.5D);

                    if (entryPoint.squaredDistanceTo(start) > 0.01D)
                    {
                        waypoints.add(entryPoint);
                    }
                }
                else
                {
                    ex = tx;
                    ez = tz;
                    entryPoint = target;
                }
            }

            boolean clearLOS = hasClearLineOfSight(ex, ez, tx, tz, cellsB);

            if (clearLOS)
            {
                if (waypoints.get(waypoints.size() - 1).squaredDistanceTo(target) > 0.01D)
                {
                    waypoints.add(target);
                }
            }
            else
            {
                List<Vec3d> path = findPathThroughCells(ex, ez, tx, tz, cellsB);

                if (path != null && !path.isEmpty())
                {
                    List<Vec3d> smoothed = smoothPath(path, cellsB);

                    for (Vec3d wp : smoothed)
                    {
                        if (waypoints.get(waypoints.size() - 1).squaredDistanceTo(wp) > 0.01D)
                        {
                            waypoints.add(wp);
                        }
                    }
                }

                if (waypoints.get(waypoints.size() - 1).squaredDistanceTo(target) > 0.01D)
                {
                    waypoints.add(target);
                }
            }

            int numWp = waypoints.size();
            Vec3d[] wpArray = waypoints.toArray(new Vec3d[numWp]);
            double[] cumDist = new double[numWp];
            double totalDist = 0D;
            cumDist[0] = 0D;

            for (int w = 1; w < numWp; w++)
            {
                totalDist += wpArray[w].distanceTo(wpArray[w - 1]);
                cumDist[w] = totalDist;
            }

            float delay = stagger > 0F ? (float) (hash(index, 0x9E3779B9) * Math.min(stagger * 0.25F, 0.35F)) : 0F;

            return new MemberRoute(wpArray, cumDist, totalDist, delay);
        }
    }

    private static class MemberRoute
    {
        private final Vec3d[] waypoints;
        private final double[] cumulativeDistances;
        private final double totalDistance;
        private final float delay;
        private final Vec3d initialFacing;
        private final Vec3d arrivalFacing;

        public MemberRoute(Vec3d[] waypoints, double[] cumulativeDistances, double totalDistance, float delay)
        {
            this.waypoints = waypoints;
            this.cumulativeDistances = cumulativeDistances;
            this.totalDistance = totalDistance;
            this.delay = delay;

            if (waypoints != null && waypoints.length >= 2)
            {
                Vec3d init = waypoints[1].subtract(waypoints[0]);
                this.initialFacing = init.lengthSquared() > EPSILON ? init.normalize() : null;

                Vec3d arr = waypoints[waypoints.length - 1].subtract(waypoints[waypoints.length - 2]);
                this.arrivalFacing = arr.lengthSquared() > EPSILON ? arr.normalize() : null;
            }
            else
            {
                this.initialFacing = null;
                this.arrivalFacing = null;
            }
        }

        public Vec3d getArrivalFacing()
        {
            return this.arrivalFacing;
        }

        public Vec3d getInitialFacing()
        {
            return this.initialFacing;
        }

        public float memberProgress(float globalProgress)
        {
            if (this.delay <= 0F)
            {
                return MathHelper.clamp(globalProgress, 0F, 1F);
            }

            float maxDelay = 0.35F;
            float p = (globalProgress - this.delay) / (1F - maxDelay);

            return MathHelper.clamp(p, 0F, 1F);
        }

        public void evaluatePosition(float globalProgress, double[] output)
        {
            if (this.totalDistance < EPSILON || this.waypoints.length == 1)
            {
                Vec3d target = this.waypoints[this.waypoints.length - 1];
                output[0] = target.x;
                output[1] = target.y;
                output[2] = target.z;

                return;
            }

            float p = this.memberProgress(globalProgress);
            double targetDist = p * this.totalDistance;

            int n = this.waypoints.length;

            for (int i = 0; i < n - 1; i++)
            {
                double d0 = this.cumulativeDistances[i];
                double d1 = this.cumulativeDistances[i + 1];

                if (targetDist <= d1 || i == n - 2)
                {
                    double segmentLen = d1 - d0;
                    double alpha = segmentLen > EPSILON ? MathHelper.clamp((targetDist - d0) / segmentLen, 0D, 1D) : 0D;

                    Vec3d p0 = this.waypoints[i];
                    Vec3d p1 = this.waypoints[i + 1];

                    output[0] = p0.x + (p1.x - p0.x) * alpha;
                    output[1] = p0.y + (p1.y - p0.y) * alpha;
                    output[2] = p0.z + (p1.z - p0.z) * alpha;

                    return;
                }
            }

            Vec3d last = this.waypoints[n - 1];
            output[0] = last.x;
            output[1] = last.y;
            output[2] = last.z;
        }

        public Vec3d evaluateFacing(float globalProgress)
        {
            if (this.totalDistance < EPSILON || this.waypoints.length == 1)
            {
                return null;
            }

            float p = this.memberProgress(globalProgress);

            if (p <= 0F)
            {
                return this.initialFacing;
            }

            if (p >= 1F)
            {
                return this.arrivalFacing;
            }

            double currentDist = p * this.totalDistance;
            double aheadDist = Math.min(this.totalDistance, currentDist + 0.5D);

            double[] pos = new double[3];
            double[] ahead = new double[3];

            this.evaluateAtDistance(currentDist, pos);
            this.evaluateAtDistance(aheadDist, ahead);

            double vx = ahead[0] - pos[0];
            double vy = ahead[1] - pos[1];
            double vz = ahead[2] - pos[2];
            double len = Math.hypot(vx, vz);

            if (len > EPSILON)
            {
                return new Vec3d(vx / len, 0D, vz / len);
            }

            return this.arrivalFacing != null ? this.arrivalFacing : this.initialFacing;
        }

        private void evaluateAtDistance(double targetDist, double[] output)
        {
            int n = this.waypoints.length;

            for (int i = 0; i < n - 1; i++)
            {
                double d0 = this.cumulativeDistances[i];
                double d1 = this.cumulativeDistances[i + 1];

                if (targetDist <= d1 || i == n - 2)
                {
                    double segmentLen = d1 - d0;
                    double alpha = segmentLen > EPSILON ? MathHelper.clamp((targetDist - d0) / segmentLen, 0D, 1D) : 0D;

                    Vec3d p0 = this.waypoints[i];
                    Vec3d p1 = this.waypoints[i + 1];

                    output[0] = p0.x + (p1.x - p0.x) * alpha;
                    output[1] = p0.y + (p1.y - p0.y) * alpha;
                    output[2] = p0.z + (p1.z - p0.z) * alpha;

                    return;
                }
            }

            Vec3d last = this.waypoints[n - 1];
            output[0] = last.x;
            output[1] = last.y;
            output[2] = last.z;
        }
    }

    private static long findFirstIntersection(int x0, int z0, int x1, int z1, Long2IntOpenHashMap cells)
    {
        int dx = Math.abs(x1 - x0);
        int dz = Math.abs(z1 - z0);
        int sx = x0 < x1 ? 1 : -1;
        int sz = z0 < z1 ? 1 : -1;
        int err = dx - dz;

        int x = x0;
        int z = z0;

        while (true)
        {
            long key = ValueAreaCells.key(x, z);

            if (cells.containsKey(key))
            {
                return key;
            }

            if (x == x1 && z == z1)
            {
                break;
            }

            int e2 = 2 * err;

            if (e2 > -dz)
            {
                err -= dz;
                x += sx;
            }

            if (e2 < dx)
            {
                err += dx;
                z += sz;
            }
        }

        return Long.MIN_VALUE;
    }

    private static boolean hasClearLineOfSight(int x0, int z0, int x1, int z1, Long2IntOpenHashMap cells)
    {
        int dx = Math.abs(x1 - x0);
        int dz = Math.abs(z1 - z0);
        int sx = x0 < x1 ? 1 : -1;
        int sz = z0 < z1 ? 1 : -1;
        int err = dx - dz;

        int x = x0;
        int z = z0;

        while (true)
        {
            if (!cells.containsKey(ValueAreaCells.key(x, z)))
            {
                return false;
            }

            if (x == x1 && z == z1)
            {
                break;
            }

            int e2 = 2 * err;

            if (e2 > -dz)
            {
                err -= dz;
                x += sx;
            }

            if (e2 < dx)
            {
                err += dx;
                z += sz;
            }
        }

        return true;
    }

    private static List<Vec3d> findPathThroughCells(int sx, int sz, int tx, int tz, Long2IntOpenHashMap cells)
    {
        long startKey = ValueAreaCells.key(sx, sz);
        long targetKey = ValueAreaCells.key(tx, tz);

        if (startKey == targetKey)
        {
            int y = cells.get(startKey);

            return Collections.singletonList(new Vec3d(sx + 0.5D, y + 1.0D, sz + 0.5D));
        }

        record Node(long key, double fScore) implements Comparable<Node>
        {
            @Override
            public int compareTo(Node o)
            {
                return Double.compare(this.fScore, o.fScore);
            }
        }

        PriorityQueue<Node> openSet = new PriorityQueue<>();
        Long2DoubleOpenHashMap gScore = new Long2DoubleOpenHashMap();
        Long2LongOpenHashMap cameFrom = new Long2LongOpenHashMap();
        LongOpenHashSet closedSet = new LongOpenHashSet();

        gScore.defaultReturnValue(Double.POSITIVE_INFINITY);
        cameFrom.defaultReturnValue(Long.MIN_VALUE);

        gScore.put(startKey, 0D);
        double hStart = Math.hypot(tx - sx, tz - sz);
        openSet.add(new Node(startKey, hStart));

        long bestNodeKey = startKey;
        double bestDist = hStart;

        int maxIterations = Math.min(cells.size() * 3, 5000);
        int iterations = 0;

        int[] dxs = {-1, 0, 1, -1, 1, -1, 0, 1};
        int[] dzs = {-1, -1, -1, 0, 0, 1, 1, 1};
        double[] costs = {1.4142D, 1D, 1.4142D, 1D, 1D, 1.4142D, 1D, 1.4142D};

        while (!openSet.isEmpty() && iterations++ < maxIterations)
        {
            Node current = openSet.poll();
            long currKey = current.key;

            if (currKey == targetKey)
            {
                bestNodeKey = targetKey;
                break;
            }

            if (closedSet.contains(currKey))
            {
                continue;
            }

            closedSet.add(currKey);

            int cx = ValueAreaCells.keyX(currKey);
            int cz = ValueAreaCells.keyZ(currKey);
            double currentG = gScore.get(currKey);

            double distToTarget = Math.hypot(tx - cx, tz - cz);

            if (distToTarget < bestDist)
            {
                bestDist = distToTarget;
                bestNodeKey = currKey;
            }

            for (int i = 0; i < 8; i++)
            {
                int nx = cx + dxs[i];
                int nz = cz + dzs[i];
                long neighborKey = ValueAreaCells.key(nx, nz);

                if (!cells.containsKey(neighborKey) || closedSet.contains(neighborKey))
                {
                    continue;
                }

                if (dxs[i] != 0 && dzs[i] != 0)
                {
                    if (!cells.containsKey(ValueAreaCells.key(cx + dxs[i], cz))
                        && !cells.containsKey(ValueAreaCells.key(cx, cz + dzs[i])))
                    {
                        continue;
                    }
                }

                double tentativeG = currentG + costs[i];

                if (tentativeG < gScore.get(neighborKey))
                {
                    cameFrom.put(neighborKey, currKey);
                    gScore.put(neighborKey, tentativeG);
                    double f = tentativeG + Math.hypot(tx - nx, tz - nz);
                    openSet.add(new Node(neighborKey, f));
                }
            }
        }

        List<Vec3d> path = new ArrayList<>();
        long curr = bestNodeKey;

        while (curr != Long.MIN_VALUE)
        {
            int x = ValueAreaCells.keyX(curr);
            int z = ValueAreaCells.keyZ(curr);
            int y = cells.get(curr);
            path.add(new Vec3d(x + 0.5D, y + 1.0D, z + 0.5D));

            if (curr == startKey)
            {
                break;
            }

            curr = cameFrom.get(curr);
        }

        Collections.reverse(path);

        return path;
    }

    private static List<Vec3d> smoothPath(List<Vec3d> rawPath, Long2IntOpenHashMap cells)
    {
        if (rawPath.size() <= 2)
        {
            return rawPath;
        }

        List<Vec3d> smoothed = new ArrayList<>();
        smoothed.add(rawPath.get(0));

        int currentIdx = 0;

        while (currentIdx < rawPath.size() - 1)
        {
            int nextIdx = currentIdx + 1;

            for (int testIdx = rawPath.size() - 1; testIdx > currentIdx + 1; testIdx--)
            {
                Vec3d a = rawPath.get(currentIdx);
                Vec3d b = rawPath.get(testIdx);

                if (hasClearLineOfSight((int) Math.floor(a.x), (int) Math.floor(a.z),
                                        (int) Math.floor(b.x), (int) Math.floor(b.z), cells))
                {
                    nextIdx = testIdx;
                    break;
                }
            }

            smoothed.add(rawPath.get(nextIdx));
            currentIdx = nextIdx;
        }

        return smoothed;
    }

    private static double hash(int index, int seed)
    {
        long h = (long) index * 0x5DEECE66DL + seed;
        h = (h ^ (h >>> 16)) * 0x45d9f3b;
        h = (h ^ (h >>> 16)) * 0x45d9f3b;
        h = h ^ (h >>> 16);

        return (h & 0xFFFFFF) / (double) 0x1000000;
    }
}
