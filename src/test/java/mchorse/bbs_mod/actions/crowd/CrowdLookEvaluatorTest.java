package mchorse.bbs_mod.actions.crowd;

import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CrowdLookEvaluatorTest
{
    private static final float MAX_HEAD_TURN = 60F;

    @Test
    public void testKeyframeFactoryNotStepped()
    {
        // Crowd look target keyframes must not be stepped by default so they interpolate smoothly
        assertFalse(KeyframeFactories.CROWD_LOOK_TARGET.isStepped(),
            "CrowdLookTargetKeyframeFactory should not be stepped so it defaults to smooth LINEAR interpolation");
    }

    @Test
    public void testBodyRotationWhileWalking()
    {
        // Travel direction North (0 degrees)
        float travelYaw = 0F;
        float lookYaw = 90F;

        // When bodyYaw is enabled in control: torso/body must accurately face lookYaw
        CrowdLookTarget withBodyYaw = new CrowdLookTarget("target", true, true, true, true);
        float bodyWhenEnabled = withBodyYaw.bodyYaw() ? lookYaw : travelYaw;
        assertEquals(90F, bodyWhenEnabled, 0.001F, "Body must accurately face lookYaw when bodyYaw is enabled");

        // When bodyYaw is disabled in control: body stays aligned to travelYaw (no diagonal skew)
        CrowdLookTarget withoutBodyYaw = new CrowdLookTarget("target", true, true, false, true);
        float bodyWhenDisabled = withoutBodyYaw.bodyYaw() ? lookYaw : travelYaw;
        assertEquals(0F, bodyWhenDisabled, 0.001F, "Body must stay aligned with travel direction without diagonal skew");
    }

    @Test
    public void testSmoothTransitionBetweenTwoTargets()
    {
        // Member at origin (0, 0, 0)
        double eyeX = 0D;
        double eyeY = 1.62D;
        double eyeZ = 0D;

        // Target A at (0, 1.62, 10) -> South (yaw = 0)
        Vec3d targetA = new Vec3d(0D, 1.62D, 10D);
        // Target B at (10, 1.62, 0) -> East (yaw = -90)
        Vec3d targetB = new Vec3d(10D, 1.62D, 0D);

        CrowdLookTarget control = new CrowdLookTarget("targetB", true, true, true, true);
        float[] output = new float[2];

        // At progress 0.0: should point directly at Target A
        CrowdLookEvaluator.Sample sampleStart = new CrowdLookEvaluator.Sample(targetA, targetB, 0.0D, control);
        assertTrue(CrowdLookEvaluator.rotation(eyeX, eyeY, eyeZ, 0F, 0F, sampleStart, output));
        assertEquals(0F, MathHelper.wrapDegrees(output[0]), 0.5F, "Yaw at progress 0 should point at Target A (0 deg)");

        // At progress 1.0: should point directly at Target B
        CrowdLookEvaluator.Sample sampleEnd = new CrowdLookEvaluator.Sample(targetA, targetB, 1.0D, control);
        assertTrue(CrowdLookEvaluator.rotation(eyeX, eyeY, eyeZ, 0F, 0F, sampleEnd, output));
        assertEquals(-90F, MathHelper.wrapDegrees(output[0]), 0.5F, "Yaw at progress 1 should point at Target B (-90 deg)");

        // At progress 0.5: should point halfway between A and B (-45 degrees)
        CrowdLookEvaluator.Sample sampleMid = new CrowdLookEvaluator.Sample(targetA, targetB, 0.5D, control);
        assertTrue(CrowdLookEvaluator.rotation(eyeX, eyeY, eyeZ, 0F, 0F, sampleMid, output));
        assertEquals(-45F, MathHelper.wrapDegrees(output[0]), 0.5F, "Yaw at progress 0.5 should be halfway (-45 deg)");
    }

    @Test
    public void testTransitionFromForwardToTarget()
    {
        double eyeX = 0D;
        double eyeY = 1.62D;
        double eyeZ = 0D;
        float currentYaw = 180F; // facing North
        float currentPitch = 0F;

        // Target B at (10, 1.62, 0) -> East (yaw = -90)
        Vec3d targetB = new Vec3d(10D, 1.62D, 0D);
        CrowdLookTarget control = new CrowdLookTarget("targetB", true, true, true, true);
        float[] output = new float[2];

        // When Target A is null (transitioning from no-target/forward into Target B)
        CrowdLookEvaluator.Sample sampleMid = new CrowdLookEvaluator.Sample(null, targetB, 0.5D, control);
        assertTrue(CrowdLookEvaluator.rotation(eyeX, eyeY, eyeZ, currentYaw, currentPitch, sampleMid, output));

        // Halfway between 180 (North) and -90 (East) the short way round:
        // wrapDegrees(-90 - 180) = wrapDegrees(-270) = +90
        // 180 + 90 * 0.5 = 225 = -135
        assertEquals(-135F, MathHelper.wrapDegrees(output[0]), 0.5F,
            "Should smoothly interpolate from forward heading to target heading");
    }
}
