package mchorse.bbs_mod.actions.crowd;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import mchorse.bbs_mod.actions.types.area.ValueAreaCells;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CrowdPaintEvaluatorTest
{
    @Test
    public void testDonutHoleAvoidance()
    {
        KeyframeChannel<CrowdPaint> channel = new KeyframeChannel<>("crowd_paint", KeyframeFactories.CROWD_PAINT);
        int count = 20;

        // Formation A: A square block from X: -3..3, Z: -15..-10
        Long2IntOpenHashMap squareCells = new Long2IntOpenHashMap();
        for (int x = -3; x <= 3; x++)
        {
            for (int z = -15; z <= -10; z++)
            {
                squareCells.put(ValueAreaCells.key(x, z), 64);
            }
        }

        // Formation B: A donut centered at (0, 10) with outer radius 7 and inner hole radius 3
        Long2IntOpenHashMap donutCells = new Long2IntOpenHashMap();
        int centerX = 0;
        int centerZ = 10;
        int innerRadius = 3;
        int outerRadius = 7;

        for (int x = centerX - outerRadius; x <= centerX + outerRadius; x++)
        {
            for (int z = centerZ - outerRadius; z <= centerZ + outerRadius; z++)
            {
                double dist = Math.hypot(x - centerX, z - centerZ);
                if (dist >= innerRadius && dist <= outerRadius)
                {
                    donutCells.put(ValueAreaCells.key(x, z), 64);
                }
            }
        }

        CrowdPaint paintA = new CrowdPaint(squareCells);
        CrowdPaint paintB = new CrowdPaint(donutCells);

        channel.insert(0, paintA);
        channel.insert(40, paintB);

        // Test over several intermediate ticks during the transition
        double[] pos = new double[3];

        for (float tick = 1; tick < 40; tick += 2)
        {
            CrowdPaintEvaluator.Frame frame = CrowdPaintEvaluator.frame(channel, "donut_test", tick, count);
            assertNotNull(frame, "Frame should not be null during transition");

            for (int i = 0; i < count; i++)
            {
                frame.memberPosition(i, pos);

                // When member is in the donut area, verify they never step on an unpainted hole cell
                int cellX = (int) Math.floor(pos[0]);
                int cellZ = (int) Math.floor(pos[2]);
                double distToDonutCenter = Math.hypot(pos[0] - centerX, pos[2] - centerZ);

                // Unpainted hole is cells within radius < innerRadius (e.g. distToCenter <= 2.0)
                if (distToDonutCenter <= 2.0D)
                {
                    throw new AssertionError(String.format(
                        "Member %d stepped into the unpainted donut hole at tick %.1f: pos=(%.2f, %.2f, %.2f), dist=%.2f <= 2.0",
                        i, tick, pos[0], pos[1], pos[2], distToDonutCenter
                    ));
                }
            }
        }
    }

    @Test
    public void testEvenDistributionAndNoOvershoot()
    {
        KeyframeChannel<CrowdPaint> channel = new KeyframeChannel<>("crowd_paint", KeyframeFactories.CROWD_PAINT);
        int count = 16;

        Long2IntOpenHashMap cellsA = new Long2IntOpenHashMap();
        for (int x = -2; x <= 2; x++)
        {
            for (int z = -2; z <= 2; z++)
            {
                cellsA.put(ValueAreaCells.key(x, z), 64);
            }
        }

        Long2IntOpenHashMap cellsB = new Long2IntOpenHashMap();
        for (int x = 20; x <= 24; x++)
        {
            for (int z = 20; z <= 24; z++)
            {
                cellsB.put(ValueAreaCells.key(x, z), 64);
            }
        }

        channel.insert(0, new CrowdPaint(cellsA));
        channel.insert(50, new CrowdPaint(cellsB));

        CrowdPaintEvaluator.Frame finalFrame = CrowdPaintEvaluator.frame(channel, "even_test", 50, count);
        assertNotNull(finalFrame);

        double[] pos50 = new double[3];
        double[] pos70 = new double[3];

        Set<String> uniqueTargetSlots = new HashSet<>();

        for (int i = 0; i < count; i++)
        {
            finalFrame.memberPosition(i, pos50);
            uniqueTargetSlots.add(String.format("%.1f_%.1f", pos50[0], pos50[2]));

            // Verify member stands within target cellsB area
            assertTrue(pos50[0] >= 19.5D && pos50[0] <= 25.5D, "Member " + i + " X out of target bounds: " + pos50[0]);
            assertTrue(pos50[2] >= 19.5D && pos50[2] <= 25.5D, "Member " + i + " Z out of target bounds: " + pos50[2]);

            // Test no overshoot past keyframe tick 50 (e.g. at tick 70)
            CrowdPaintEvaluator.Frame pastFrame = CrowdPaintEvaluator.frame(channel, "even_test", 70, count);
            pastFrame.memberPosition(i, pos70);

            assertEquals(pos50[0], pos70[0], 0.001D, "Member position drifted past tick 50 (X overshoot)");
            assertEquals(pos50[2], pos70[2], 0.001D, "Member position drifted past tick 50 (Z overshoot)");
            assertFalse(pastFrame.isMoving(i), "Member should not be moving past keyframe tick");
        }

        // Verify members are evenly matched to unique slots
        assertEquals(count, uniqueTargetSlots.size(), "All members should occupy unique target slots");
    }

    @Test
    public void testArrivalPositionContinuityNoChessboardRearrange()
    {
        KeyframeChannel<CrowdPaint> channel = new KeyframeChannel<>("crowd_paint", KeyframeFactories.CROWD_PAINT);
        int count = 10;

        // Formation A: square at (0, 0)
        Long2IntOpenHashMap cellsA = new Long2IntOpenHashMap();
        for (int x = -1; x <= 1; x++)
        {
            for (int z = -1; z <= 1; z++)
            {
                cellsA.put(ValueAreaCells.key(x, z), 64);
            }
        }

        // Formation B: shifted shape at (15, 10)
        Long2IntOpenHashMap cellsB = new Long2IntOpenHashMap();
        for (int x = 14; x <= 16; x++)
        {
            for (int z = 9; z <= 11; z++)
            {
                cellsB.put(ValueAreaCells.key(x, z), 64);
            }
        }

        channel.insert(0, new CrowdPaint(cellsA));
        channel.insert(40, new CrowdPaint(cellsB));

        CrowdPaintEvaluator.Frame frameArrivalApproach = CrowdPaintEvaluator.frame(channel, "arrival_test", 39.99F, count);
        CrowdPaintEvaluator.Frame frameArrivalExact = CrowdPaintEvaluator.frame(channel, "arrival_test", 40.0F, count);
        CrowdPaintEvaluator.Frame frameArrivalPast1 = CrowdPaintEvaluator.frame(channel, "arrival_test", 41.0F, count);
        CrowdPaintEvaluator.Frame frameArrivalPast20 = CrowdPaintEvaluator.frame(channel, "arrival_test", 60.0F, count);

        double[] posApproach = new double[3];
        double[] posExact = new double[3];
        double[] posPast1 = new double[3];
        double[] posPast20 = new double[3];

        for (int i = 0; i < count; i++)
        {
            frameArrivalApproach.memberPosition(i, posApproach);
            frameArrivalExact.memberPosition(i, posExact);
            frameArrivalPast1.memberPosition(i, posPast1);
            frameArrivalPast20.memberPosition(i, posPast20);

            // 1. Assert ZERO sudden slot swap / chessboard jump between transit end and static arrival
            double distApproachToExact = Math.hypot(posApproach[0] - posExact[0], posApproach[2] - posExact[2]);
            assertTrue(distApproachToExact < 0.1D,
                String.format("Member %d jumped at arrival! approach=(%.2f, %.2f), exact=(%.2f, %.2f), delta=%.2f",
                    i, posApproach[0], posApproach[2], posExact[0], posExact[2], distApproachToExact));

            // 2. Assert member remains rock-solid in its assigned arrival slot indefinitely
            assertEquals(posExact[0], posPast1[0], 0.0001D, "Member " + i + " X changed after arrival tick");
            assertEquals(posExact[2], posPast1[2], 0.0001D, "Member " + i + " Z changed after arrival tick");
            assertEquals(posExact[0], posPast20[0], 0.0001D, "Member " + i + " X drifted across 20 ticks");
            assertEquals(posExact[2], posPast20[2], 0.0001D, "Member " + i + " Z drifted across 20 ticks");

            // 3. Assert arrival facing is non-null and steady
            net.minecraft.util.math.Vec3d facing = frameArrivalExact.memberFacing(i);
            assertNotNull(facing, "Arrival facing should be non-null for member " + i);
            assertTrue(facing.lengthSquared() > 0.01D, "Arrival facing vector should be non-zero");

            net.minecraft.util.math.Vec3d pastFacing = frameArrivalPast20.memberFacing(i);
            assertNotNull(pastFacing, "Settled facing should be preserved past arrival tick");
            assertEquals(facing.x, pastFacing.x, 0.0001D, "Facing X should be preserved");
            assertEquals(facing.z, pastFacing.z, 0.0001D, "Facing Z should be preserved");
        }
    }

    @Test
    public void testMultiKeyframeChainSlotContinuity()
    {
        KeyframeChannel<CrowdPaint> channel = new KeyframeChannel<>("crowd_paint", KeyframeFactories.CROWD_PAINT);
        int count = 8;

        // Keyframe 0: at (0, 0)
        Long2IntOpenHashMap cells0 = new Long2IntOpenHashMap();
        for (int x = -1; x <= 1; x++)
        {
            for (int z = -1; z <= 1; z++)
            {
                cells0.put(ValueAreaCells.key(x, z), 64);
            }
        }

        // Keyframe 1: at (10, 0)
        Long2IntOpenHashMap cells1 = new Long2IntOpenHashMap();
        for (int x = 9; x <= 11; x++)
        {
            for (int z = -1; z <= 1; z++)
            {
                cells1.put(ValueAreaCells.key(x, z), 64);
            }
        }

        // Keyframe 2: at (20, 5)
        Long2IntOpenHashMap cells2 = new Long2IntOpenHashMap();
        for (int x = 19; x <= 21; x++)
        {
            for (int z = 4; z <= 6; z++)
            {
                cells2.put(ValueAreaCells.key(x, z), 64);
            }
        }

        channel.insert(0, new CrowdPaint(cells0));
        channel.insert(40, new CrowdPaint(cells1));
        channel.insert(80, new CrowdPaint(cells2));

        CrowdPaintEvaluator.Frame frame40 = CrowdPaintEvaluator.frame(channel, "chain_test", 40.0F, count);
        CrowdPaintEvaluator.Frame frame40_01 = CrowdPaintEvaluator.frame(channel, "chain_test", 40.01F, count);

        double[] pos40 = new double[3];
        double[] pos40_01 = new double[3];

        for (int i = 0; i < count; i++)
        {
            frame40.memberPosition(i, pos40);
            frame40_01.memberPosition(i, pos40_01);

            // Member i must depart for keyframe 2 from the exact slot it arrived at in keyframe 1
            double delta = Math.hypot(pos40[0] - pos40_01[0], pos40[2] - pos40_01[2]);
            assertTrue(delta < 0.05D, String.format("Member %d jumped departing K1! delta=%.4f", i, delta));
        }

        // Verify arrival at keyframe 2
        CrowdPaintEvaluator.Frame frame79_99 = CrowdPaintEvaluator.frame(channel, "chain_test", 79.99F, count);
        CrowdPaintEvaluator.Frame frame80 = CrowdPaintEvaluator.frame(channel, "chain_test", 80.0F, count);

        double[] pos79 = new double[3];
        double[] pos80 = new double[3];

        for (int i = 0; i < count; i++)
        {
            frame79_99.memberPosition(i, pos79);
            frame80.memberPosition(i, pos80);

            double delta = Math.hypot(pos79[0] - pos80[0], pos79[2] - pos80[2]);
            assertTrue(delta < 0.05D, String.format("Member %d jumped arriving at K2! delta=%.4f", i, delta));
        }
    }
}
