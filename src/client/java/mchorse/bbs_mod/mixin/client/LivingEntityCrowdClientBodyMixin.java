package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.actions.types.crowd.CrowdClientMembers;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Puts a crowd member's torso and head where the server said, on the client, at once.
 *
 * <p>Two pieces of vanilla stand between a crowd clip and what ends up on screen, and neither is
 * something the server can reach.</p>
 *
 * <p>The first is that body yaw is never sent. The client works it out itself, swinging the body
 * after the way the entity appears to be moving and only dragging it toward the sent facing once
 * the two are more than seventy-five degrees apart - so a standing crowd keeps whatever facing it
 * happened to be spawned with, however plainly the server says otherwise. For a crowd the sent
 * facing is the answer, so it is simply taken.</p>
 *
 * <p>The second is that rotations arrive as something to ease into over the next few ticks, which
 * is right for a mob that turns of its own accord and wrong for one being posed: it turns a shot
 * that should begin on target into one that arrives at it several frames late. A crowd member's
 * angles are placed rather than approached.</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityCrowdClientBodyMixin extends Entity
{
    /** How far a torso may turn in a tick before it is a cut rather than a turn. */
    private static final float SNAP_DEGREES = 120F;

    public LivingEntityCrowdClientBodyMixin()
    {
        super(null, null);
    }

    /**
     * Take the sent facing as the body's, rather than deriving one. The return value only tells
     * the renderer which way to swing the limbs, and a body that faces where it is looking has
     * nothing to flip.
     */
    @Inject(method = "turnHead", at = @At("HEAD"), cancellable = true)
    private void bbs$snapCrowdBodyYaw(float bodyRotation, float headRotation, CallbackInfoReturnable<Float> info)
    {
        if (this.getWorld() != null && this.getWorld().isClient && CrowdClientMembers.isMember(this.getId()))
        {
            LivingEntity self = (LivingEntity) (Object) this;

            /* The sent facing, not one worked out from movement.
             *
             * The server sets a member's yaw for both of the things that decide where it should
             * face: a walk point sets it to the heading of travel (CrowdKeyframeRuntime, where
             * faceTravel is on) and a look keyframe sets it to the target (where the look drives
             * body yaw).
             *
             * The server already smoothly limits turn rate per tick (e.g. 18°/tick). Following the
             * server's yaw directly keeps head and torso in lockstep, preventing unnatural head
             * twisting and sudden snap thresholds during turns while sub-tick lerping stays fluid. */
            float target = self.getYaw();
            float delta = net.minecraft.util.math.MathHelper.wrapDegrees(target - self.bodyYaw);

            if (Math.abs(delta) > SNAP_DEGREES)
            {
                self.prevBodyYaw = target;
                self.bodyYaw = target;
            }
            else
            {
                self.prevBodyYaw = self.bodyYaw;
                self.bodyYaw = target;
            }

            info.setReturnValue(headRotation);
        }
    }

    @Inject(method = "updateTrackedHeadRotation", at = @At("HEAD"), cancellable = true)
    private void bbs$snapCrowdHeadYaw(float yaw, int interpolationSteps, CallbackInfo info)
    {
        if (CrowdClientMembers.isMember(this.getId()))
        {
            LivingEntity self = (LivingEntity) (Object) this;
            float delta = net.minecraft.util.math.MathHelper.wrapDegrees(yaw - self.getHeadYaw());

            /* Cuts snap both prev and current head yaw so cuts don't spin around 180°.
             * Smooth turns keep prevHeadYaw so sub-tick interpolation remains seamless. */
            if (Math.abs(delta) > SNAP_DEGREES)
            {
                self.prevHeadYaw = yaw;
                self.setHeadYaw(yaw);
            }
            else
            {
                self.prevHeadYaw = self.getHeadYaw();
                self.setHeadYaw(yaw);
            }

            info.cancel();
        }
    }

    @Inject(method = "handleStatus", at = @At("HEAD"), cancellable = true)
    private void bbs$cancelCrowdDamageTilt(byte status, CallbackInfo info)
    {
        if (this.getWorld() != null && this.getWorld().isClient && CrowdClientMembers.isMember(this.getId()))
        {
            if (status == 2 || status == 33 || status == 36 || status == 37)
            {
                info.cancel();
            }
        }
    }
}
