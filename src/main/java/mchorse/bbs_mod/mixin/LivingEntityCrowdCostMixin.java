package mchorse.bbs_mod.mixin;

import mchorse.bbs_mod.actions.types.crowd.CrowdUtils;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Takes entity-vs-entity pushing off crowd members.
 *
 * <p>Cramming is the cost that does not scale: every member queries the entities overlapping it
 * and then shoves each one, so a packed crowd is quadratic in its local density. It is also the
 * one part of vanilla movement a crowd does not need - the behaviour clip already spaces members
 * with its own separation steering, which reads a bucketed grid instead.</p>
 *
 * <p>Without this a four-figure crowd cannot hold 20 TPS on any machine, and a server that
 * cannot keep up plays the whole scene back in slow motion.</p>
 */
@Mixin(LivingEntity.class)
public class LivingEntityCrowdCostMixin
{
    @Inject(method = "tickCramming", at = @At("HEAD"), cancellable = true)
    private void bbs$skipCramming(CallbackInfo info)
    {
        if (this.bbs$isCrowdMember())
        {
            info.cancel();
        }
    }

    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private void bbs$notPushable(CallbackInfoReturnable<Boolean> info)
    {
        if (this.bbs$isCrowdMember())
        {
            info.setReturnValue(false);
        }
    }

    private boolean bbs$isCrowdMember()
    {
        return ((LivingEntity) (Object) this).getCommandTags().contains(CrowdUtils.INTERNAL_TAG);
    }
}
