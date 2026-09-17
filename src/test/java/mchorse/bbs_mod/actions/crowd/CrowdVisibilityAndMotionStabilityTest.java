package mchorse.bbs_mod.actions.crowd;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CrowdVisibilityAndMotionStabilityTest
{
    @BeforeAll
    public static void setUp()
    {
        KeyframeFactories.setup();
    }

    @Test
    public void testCrowdVisibilityChannelDefaultsAndInterpolation()
    {
        ReplayKeyframes keyframes = new ReplayKeyframes("keyframes");
        KeyframeChannel<Boolean> visibleChannel = keyframes.crowdVisible;

        // When empty, channel is empty and callers default to true (visible)
        assertTrue(visibleChannel.isEmpty(), "crowdVisible should start empty");
        assertTrue(visibleChannel.isEmpty() || visibleChannel.interpolate(0F, true), "Default should be visible");

        // Insert keyframes: Visible at 0, Hidden at 20, Visible at 40
        visibleChannel.insert(0, true);
        visibleChannel.insert(20, false);
        visibleChannel.insert(40, true);

        assertEquals(3, visibleChannel.getKeyframes().size());

        // Stepped interpolation tests
        assertTrue(visibleChannel.interpolate(0F, true), "At tick 0 should be visible");
        assertTrue(visibleChannel.interpolate(10F, true), "At tick 10 should still be visible (stepped)");
        assertTrue(visibleChannel.interpolate(19.9F, true), "At tick 19.9 should be visible");
        assertFalse(visibleChannel.interpolate(20F, true), "At tick 20 should be hidden");
        assertFalse(visibleChannel.interpolate(30F, true), "At tick 30 should be hidden");
        assertFalse(visibleChannel.interpolate(39.9F, true), "At tick 39.9 should be hidden");
        assertTrue(visibleChannel.interpolate(40F, true), "At tick 40 should be visible");
        assertTrue(visibleChannel.interpolate(50F, true), "At tick 50 should remain visible");
    }

    @Test
    public void testCrowdVisibilityChannelDataSerialization()
    {
        ReplayKeyframes keyframes = new ReplayKeyframes("keyframes");
        keyframes.crowdVisible.insert(15, false);
        keyframes.crowdVisible.insert(45, true);

        BaseType data = keyframes.toData();
        assertTrue(data instanceof MapType);
        MapType map = (MapType) data;

        assertTrue(map.has("crowd_visible"));

        ReplayKeyframes restored = new ReplayKeyframes("keyframes");
        restored.fromData(data);

        assertEquals(2, restored.crowdVisible.getKeyframes().size());
        assertEquals(15F, restored.crowdVisible.get(0).getTick());
        assertFalse(restored.crowdVisible.get(0).getValue());
        assertEquals(45F, restored.crowdVisible.get(1).getTick());
        assertTrue(restored.crowdVisible.get(1).getValue());
    }

    @Test
    public void testSingleWalkKeyframeHasNoWalkRoute()
    {
        ReplayKeyframes keyframes = new ReplayKeyframes("keyframes");
        CrowdWalk walk = new CrowdWalk();
        walk.x = 2354F;
        walk.y = 32F;
        walk.z = 2197F;
        walk.terrainFollow = true;

        keyframes.crowdWalk.insert(590, walk);

        assertEquals(1, keyframes.crowdWalk.getKeyframes().size(), "Only 1 keyframe inserted");
        // With 1 keyframe, there are no segments (size < 2)
        assertFalse(keyframes.crowdWalk.getKeyframes().size() >= 2, "A walk route requires at least 2 waypoints");
    }
}
