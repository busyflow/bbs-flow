package mchorse.bbs_mod.mixin;

import mchorse.bbs_mod.actions.types.crowd.CrowdUtils;
import net.minecraft.entity.ai.control.LookControl;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands a crowd member's head to the behaviour clip alone.
 *
 * <p>Vanilla writes head yaw and pitch from {@link LookControl} at the end of every AI tick,
 * aimed at whatever a goal or a brain task last asked for - a random point on the horizon, the
 * nearest player, a workstation. The behaviour clip writes the head too, and it writes it from
 * outside the entity tick, so vanilla always had the last word: a crowd told to watch the camera
 * would turn to it for one tick and then drift back to looking around, which is exactly what it
 * looks like when the clip is ignored entirely.</p>
 *
 * <p>Restoring the angles after the fact would mean storing them for a hundred thousand members
 * and racing the same ordering every tick. Not writing them in the first place is the same
 * outcome for free, and costs a crowd member nothing it was using: the clip already stops the
 * navigator, clears the target and drives movement itself, and its own idle head motion is what
 * makes an idle crowd glance around.</p>
 */
@Mixin(LookControl.class)
public class LookControlCrowdMixin
{
    @Shadow
    @Final
    protected MobEntity entity;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void bbs$skipCrowdLook(CallbackInfo info)
    {
        if (this.entity.getCommandTags().contains(CrowdUtils.INTERNAL_TAG))
        {
            info.cancel();
        }
    }
}
