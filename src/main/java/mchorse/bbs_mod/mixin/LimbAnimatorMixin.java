package mchorse.bbs_mod.mixin;

import net.minecraft.entity.LimbAnimator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LimbAnimator.class)
public class LimbAnimatorMixin implements ILimbAnimatorExtension
{
    @Unique
    private float bbs$step = -1F;

    @Override
    public void bbs$setStep(float step)
    {
        this.bbs$step = step;
    }

    @Override
    public float bbs$getStep()
    {
        return this.bbs$step;
    }

    @Inject(method = "updateLimbs(FF)V", at = @At("HEAD"))
    public void onUpdateLimbsVanilla(float speed, float multiplier, CallbackInfo ci)
    {
        this.bbs$step = -1F;
    }

    @Inject(method = "getPos(F)F", at = @At("HEAD"), cancellable = true)
    public void onGetPos(float tickDelta, CallbackInfoReturnable<Float> cir)
    {
        if (this.bbs$step >= 0F)
        {
            LimbAnimator self = (LimbAnimator) (Object) this;
            cir.setReturnValue(self.getPos() - this.bbs$step * (1.0F - tickDelta));
        }
    }
}
