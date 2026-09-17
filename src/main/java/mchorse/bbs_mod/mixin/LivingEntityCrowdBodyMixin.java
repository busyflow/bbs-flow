package mchorse.bbs_mod.mixin;

import mchorse.bbs_mod.actions.types.crowd.CrowdDrivenEntity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets the crowd behaviour clip point a torso and have it stay pointed.
 *
 * <p>Vanilla decides a living entity's body yaw for itself at the end of every tick: it swings the
 * body toward whichever way the entity is walking, and drags it after the head whenever the two
 * are more than seventy-five degrees apart. Both run after the clip has written, so a crowd told
 * to face the camera faced it for the length of one tick and then went back to facing wherever it
 * was walking - the heads turned and the bodies did not, which is exactly what it looks like when
 * the body setting does nothing at all.</p>
 *
 * <p>Only skipped for the tick the clip actually asked for, so a crowd member with body yaw turned
 * off still walks the way vanilla turns it, and the neck limit still applies to everyone else.</p>
 */
@Mixin(LivingEntity.class)
public class LivingEntityCrowdBodyMixin implements CrowdDrivenEntity
{
    @Unique
    private boolean bbs$bodyYawDriven;

    @Unique
    private int bbs$crowdLookTick = Integer.MIN_VALUE;

    @Unique
    private int bbs$crowdIndex = Integer.MIN_VALUE;

    @Override
    public int bbs$getCrowdIndex()
    {
        return this.bbs$crowdIndex;
    }

    @Override
    public void bbs$setCrowdIndex(int index)
    {
        this.bbs$crowdIndex = index;
    }

    @Override
    public int bbs$getCrowdLookTick()
    {
        return this.bbs$crowdLookTick;
    }

    @Override
    public void bbs$setCrowdLookTick(int tick)
    {
        this.bbs$crowdLookTick = tick;
    }

    @Override
    public void bbs$driveBodyYaw()
    {
        this.bbs$bodyYawDriven = true;
    }

    @Override
    public boolean bbs$takeBodyYawDrive()
    {
        boolean driven = this.bbs$bodyYawDriven;

        this.bbs$bodyYawDriven = false;

        return driven;
    }

    /**
     * Leave the body where the clip put it, and hand back the head rotation untouched - the return
     * value only tells the renderer which way to swing the limbs, and a body that never turned has
     * nothing to flip.
     */
    @Inject(method = "turnHead", at = @At("HEAD"), cancellable = true)
    private void bbs$keepCrowdBodyYaw(float bodyRotation, float headRotation, CallbackInfoReturnable<Float> info)
    {
        if (this.bbs$takeBodyYawDrive())
        {
            info.setReturnValue(headRotation);
        }
    }
}
