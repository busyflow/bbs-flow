package mchorse.bbs_mod.ui.film.live;

import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.tracks.TrackContext;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.film.replays.tracks.behaviours.PropertyTrack;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that resetting a keyframe after a live recording take on pose tracks
 * (both base pose and pose_overlay) registers immediately and evaluates to rest,
 * without reviving older transforms across the reset boundary.
 */
public class LiveRecordingResetTest
{
    @BeforeAll
    public static void setup()
    {
        BBSSettings.recordingPoseOverlays = new ValueInt("pose_overlays", 0);
        BBSSettings.recordingTransformOverlays = new ValueInt("transform_overlays", 0);
        KeyframeFactories.setup();
    }

    private static Pose createTakePose(float headAngle)
    {
        Pose pose = new Pose();
        PoseTransform head = pose.getOrCreate("head");

        head.rotate.y = headAngle;

        return pose;
    }

    private static Pose createResetPose()
    {
        Pose pose = new Pose();

        /* In the UI, UIPoseEditor#pickBones creates a default transform for the primary bone */
        pose.getOrCreate("root");

        return pose;
    }

    @Test
    public void takeOnOverlayTrackFollowedByResetKeyframeEvaluatesToRest()
    {
        PropertyTrack behaviour = new PropertyTrack();
        ModelForm form = new ModelForm();
        TrackId track = TrackId.property("", "pose_overlay");
        KeyframeChannel<Pose> channel = new KeyframeChannel<>("pose_overlay", KeyframeFactories.POSE);

        /* Simulate a live recording take of head motion between ticks 10 and 20 */
        for (int tick = 10; tick <= 20; tick++)
        {
            channel.insert(tick, createTakePose(45F));
        }

        /* User inserts a keyframe a few frames later and resets it */
        channel.insert(25, createResetPose());

        /* Evaluate at tick 25 (the reset keyframe itself) */
        behaviour.apply(TrackContext.of(form), track, channel, 25F, 1F);
        Pose result25 = form.poseOverlay.getRuntimeValue();

        assertNotNull(result25, "Runtime pose should not be null on track");
        assertNull(result25.get("head"), "Head transform must not leak into reset keyframe at tick 25");

        /* Evaluate at tick 30 (past the reset keyframe) */
        behaviour.apply(TrackContext.of(form), track, channel, 30F, 1F);
        Pose result30 = form.poseOverlay.getRuntimeValue();

        assertNotNull(result30);
        assertNull(result30.get("head"), "Head transform must remain at rest past tick 25");
    }

    @Test
    public void resetKeyframeActsAsSparseBoundary()
    {
        PropertyTrack behaviour = new PropertyTrack();
        ModelForm form = new ModelForm();
        TrackId track = TrackId.property("", "pose_overlay");
        KeyframeChannel<Pose> channel = new KeyframeChannel<>("pose_overlay", KeyframeFactories.POSE);

        /* Ticks 10 to 20: live take on head */
        for (int tick = 10; tick <= 20; tick++)
        {
            channel.insert(tick, createTakePose(45F));
        }

        /* Tick 25: reset keyframe */
        channel.insert(25, createResetPose());

        /* Tick 35: new keyframe authoring ONLY right_arm */
        Pose armPose = new Pose();
        armPose.getOrCreate("right_arm").rotate.x = 30F;
        channel.insert(35, armPose);

        /* Evaluate at tick 35: right_arm should be active, but head must NOT leak from ticks 10-20 */
        behaviour.apply(TrackContext.of(form), track, channel, 35F, 1F);
        Pose result35 = form.poseOverlay.getRuntimeValue();

        assertNotNull(result35);
        assertNotNull(result35.get("right_arm"), "Right arm must be authored at tick 35");
        assertEquals(30F, result35.get("right_arm").rotate.x, 0.001F);
        assertNull(result35.get("head"), "Head must NOT cross the reset boundary into tick 35");
    }

    @Test
    public void takeOnBasePoseTrackFollowedByResetKeyframeEvaluatesToRest()
    {
        PropertyTrack behaviour = new PropertyTrack();
        ModelForm form = new ModelForm();
        TrackId track = TrackId.property("", FormProperties.POSE_PROPERTY);
        KeyframeChannel<Pose> channel = new KeyframeChannel<>(FormProperties.POSE_PROPERTY, KeyframeFactories.POSE);

        /* Live take on base pose track */
        for (int tick = 10; tick <= 20; tick++)
        {
            channel.insert(tick, createTakePose(45F));
        }

        /* Reset keyframe at tick 25 */
        channel.insert(25, createResetPose());

        /* Evaluate at tick 25 */
        behaviour.apply(TrackContext.of(form), track, channel, 25F, 1F);
        Pose result = form.pose.getRuntimeValue();

        assertNotNull(result);
        assertNull(result.get("head"), "Base pose track must also evaluate head to rest on reset keyframe");
    }

    @Test
    public void subtleSliderAdjustmentIsRecognized()
    {
        PropertyTrack behaviour = new PropertyTrack();
        ModelForm form = new ModelForm();
        TrackId track = TrackId.property("", "pose_overlay");
        KeyframeChannel<Pose> channel = new KeyframeChannel<>("pose_overlay", KeyframeFactories.POSE);

        for (int tick = 10; tick <= 20; tick++)
        {
            channel.insert(tick, createTakePose(45F));
        }

        /* Reset keyframe with a deliberate non-default adjustment */
        Pose adjustedPose = createResetPose();
        adjustedPose.getOrCreate("head").rotate.y = 5F;
        channel.insert(25, adjustedPose);

        behaviour.apply(TrackContext.of(form), track, channel, 25F, 1F);
        Pose result = form.poseOverlay.getRuntimeValue();

        assertNotNull(result);
        assertNotNull(result.get("head"));
        assertEquals(5F, result.get("head").rotate.y, 0.001F, "Authored non-default transform must evaluate accurately");
    }

    @Test
    public void smoothInterpolationTowardsReset()
    {
        PropertyTrack behaviour = new PropertyTrack();
        ModelForm form = new ModelForm();
        TrackId track = TrackId.property("", "pose_overlay");
        KeyframeChannel<Pose> channel = new KeyframeChannel<>("pose_overlay", KeyframeFactories.POSE);

        channel.insert(20, createTakePose(40F));
        channel.insert(30, createResetPose());

        /* Midpoint tick 25: should interpolate from 40F toward 0F (approx 20F with linear) */
        behaviour.apply(TrackContext.of(form), track, channel, 25F, 1F);
        Pose result = form.poseOverlay.getRuntimeValue();

        assertNotNull(result);
        assertNotNull(result.get("head"));
        assertEquals(20F, result.get("head").rotate.y, 1.0F, "Should interpolate smoothly towards rest at reset keyframe");
    }

    @Test
    public void takeOnBasePoseTrackFollowedByResetAndFixLimbDoesNotRevivePriorPose()
    {
        PropertyTrack behaviour = new PropertyTrack();
        ModelForm form = new ModelForm();
        TrackId track = TrackId.property("", FormProperties.POSE_PROPERTY);
        KeyframeChannel<Pose> channel = new KeyframeChannel<>(FormProperties.POSE_PROPERTY, KeyframeFactories.POSE);

        /* Keyframe at tick 20: both arms raised up */
        Pose raisedArms = new Pose();
        raisedArms.getOrCreate("left_arm").rotate.x = -90F;
        raisedArms.getOrCreate("right_arm").rotate.x = -90F;
        channel.insert(20, raisedArms);

        /* Keyframe at tick 41: pose reset, then Fix toggled to 1 on right_arm */
        Pose resetAndFixPose = new Pose();
        PoseTransform rightArm = resetAndFixPose.getOrCreate("right_arm");
        rightArm.fix = 1.0F;
        rightArm.rotate.set(0F, 0F, 0F);
        rightArm.translate.set(0F, 0F, 0F);
        channel.insert(41, resetAndFixPose);

        /* Evaluate at tick 41 */
        behaviour.apply(TrackContext.of(form), track, channel, 41F, 1F);
        Pose result = form.pose.getRuntimeValue();

        assertNotNull(result);
        PoseTransform resRight = result.get("right_arm");
        PoseTransform resLeft = result.get("left_arm");

        assertNotNull(resRight, "Right arm transform must exist");
        assertEquals(1.0F, resRight.fix, 0.001F, "Right arm fix must be 1");
        assertEquals(0F, resRight.rotate.x, 0.001F, "Right arm must stay at rest 0, not snap to -90");

        if (resLeft != null)
        {
            assertEquals(0F, resLeft.rotate.x, 0.001F, "Left arm must stay at rest 0, not snap to -90");
        }
    }

    @Test
    public void scrubbingOnBasePoseTrackWithPartialBoneEditsDoesNotLeakOtherBones()
    {
        PropertyTrack behaviour = new PropertyTrack();
        ModelForm form = new ModelForm();
        TrackId track = TrackId.property("", FormProperties.POSE_PROPERTY);
        KeyframeChannel<Pose> channel = new KeyframeChannel<>(FormProperties.POSE_PROPERTY, KeyframeFactories.POSE);

        /* Keyframe at tick 20: right_arm crossed */
        Pose crossedPose = new Pose();
        crossedPose.getOrCreate("right_arm").rotate.set(45F, 30F, 0F);
        channel.insert(20, crossedPose);

        /* Keyframe at tick 40: only left_arm edited */
        Pose leftArmPose = new Pose();
        leftArmPose.getOrCreate("left_arm").rotate.set(128F, 7F, -28F);
        channel.insert(40, leftArmPose);

        /* Evaluate at tick 40: left_arm authored, right_arm must NOT borrow tick 20 crossed rotation */
        behaviour.apply(TrackContext.of(form), track, channel, 40F, 1F);
        Pose result = form.pose.getRuntimeValue();

        assertNotNull(result);
        assertNotNull(result.get("left_arm"));
        assertEquals(128F, result.get("left_arm").rotate.x, 0.001F);

        PoseTransform rightArm = result.get("right_arm");
        if (rightArm != null)
        {
            assertEquals(0F, rightArm.rotate.x, 0.001F, "Right arm must NOT be crossed at tick 40");
            assertEquals(0F, rightArm.rotate.y, 0.001F, "Right arm must NOT be crossed at tick 40");
        }
    }

    @Test
    public void poseOverlayStacksAdditivelyOnBasePose()
    {
        PropertyTrack behaviour = new PropertyTrack();
        ModelForm form = new ModelForm();

        /* Base pose has right_arm raised forward (rotate.x = -90F) */
        TrackId baseTrack = TrackId.property("", FormProperties.POSE_PROPERTY);
        KeyframeChannel<Pose> baseChannel = new KeyframeChannel<>(FormProperties.POSE_PROPERTY, KeyframeFactories.POSE);
        Pose basePose = new Pose();
        basePose.getOrCreate("right_arm").rotate.x = -90F;
        baseChannel.insert(20, basePose);
        behaviour.apply(TrackContext.of(form), baseTrack, baseChannel, 20F, 1F);

        /* Overlay track has right_arm tilted outward (rotate.z = 15F, fix = 0) */
        TrackId overlayTrack = TrackId.property("", "pose_overlay");
        KeyframeChannel<Pose> overlayChannel = new KeyframeChannel<>("pose_overlay", KeyframeFactories.POSE);
        Pose overlayPose = new Pose();
        PoseTransform overlayRightArm = overlayPose.getOrCreate("right_arm");
        overlayRightArm.rotate.z = 15F;
        overlayRightArm.fix = 0F;
        overlayChannel.insert(20, overlayPose);
        behaviour.apply(TrackContext.of(form), overlayTrack, overlayChannel, 20F, 1F);

        /* ModelFormRenderer applyPose simulation: base pose + overlay */
        Pose finalPose = form.pose.get().copy();
        Pose overlay = form.poseOverlay.get();
        for (java.util.Map.Entry<String, PoseTransform> entry : overlay.transforms.entrySet())
        {
            PoseTransform pt = finalPose.getOrCreate(entry.getKey());
            pt.addRotation(entry.getValue());
        }

        /* Must stack: rotate.x == -90F, rotate.z == 15F */
        assertEquals(-90F, finalPose.get("right_arm").rotate.x, 0.001F, "Base pose rotation.x must be preserved");
        assertEquals(15F, finalPose.get("right_arm").rotate.z, 0.001F, "Overlay rotation.z must be added");
    }

    @Test
    public void keyframeWithAllLimbsFollowedByResetAndSingleLimbEditDoesNotRevivePriorLimbs()
    {
        PropertyTrack behaviour = new PropertyTrack();
        ModelForm form = new ModelForm();
        TrackId track = TrackId.property("", "pose_overlay");
        KeyframeChannel<Pose> channel = new KeyframeChannel<>("pose_overlay", KeyframeFactories.POSE);

        /* Keyframe 1 at tick 10: all limbs posed */
        Pose kf1 = new Pose();
        kf1.getOrCreate("head").rotate.set(20F, 10F, 0F);
        kf1.getOrCreate("body").rotate.set(5F, 0F, 0F);
        kf1.getOrCreate("left_arm").rotate.set(45F, 0F, 0F);
        kf1.getOrCreate("right_arm").rotate.set(-45F, 0F, 0F);
        kf1.getOrCreate("left_leg").rotate.set(30F, 0F, 0F);
        kf1.getOrCreate("right_leg").rotate.set(-30F, 0F, 0F);
        channel.insert(10, kf1);

        /* Keyframe 2 at tick 20: entire pose reset, then right_arm edited to 15 deg */
        Pose kf2 = new Pose();
        kf2.getOrCreate("head");
        kf2.getOrCreate("body");
        kf2.getOrCreate("left_arm");
        kf2.getOrCreate("left_leg");
        kf2.getOrCreate("right_leg");
        kf2.getOrCreate("right_arm").rotate.x = 15F;

        /* Verify copy retains default transforms */
        Pose kf2Copy = kf2.copy();
        assertNotNull(kf2Copy.get("left_arm"), "Default transforms must survive Pose.copy");
        assertTrue(kf2Copy.get("left_arm").isDefault(), "Left arm must remain default in copy");

        channel.insert(20, kf2);

        /* Evaluate at tick 20 */
        behaviour.apply(TrackContext.of(form), track, channel, 20F, 1F);
        Pose result20 = form.poseOverlay.getRuntimeValue();

        assertNotNull(result20);
        assertNotNull(result20.get("right_arm"), "Right arm must be authored at tick 20");
        assertEquals(15F, result20.get("right_arm").rotate.x, 0.001F);
        assertNull(result20.get("left_arm"), "Left arm must remain at rest (0) at tick 20, not snap to tick 10");
        assertNull(result20.get("head"), "Head must remain at rest (0) at tick 20, not snap to tick 10");
        assertNull(result20.get("body"), "Body must remain at rest (0) at tick 20, not snap to tick 10");
    }
}


