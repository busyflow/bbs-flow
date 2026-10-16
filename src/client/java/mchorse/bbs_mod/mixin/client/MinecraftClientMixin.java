package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.client.ModelBlockConversion;
import mchorse.bbs_mod.forms.structure.StructureWand;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The structure wand takes the mouse buttons here, before the game decides what a click means:
 * this is the one place a click passes through whether it lands on a block, an entity or the air,
 * and the wand must act the same on all three. Alt + right click with a model block, which turns
 * a block into one, is taken here for the same reason.
 */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin
{
    @Shadow
    private int itemUseCooldown;

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    public void wandAttack(CallbackInfoReturnable<Boolean> cir)
    {
        if (StructureWand.onAttack())
        {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    public void wandUse(CallbackInfo ci)
    {
        if (StructureWand.onUse())
        {
            ci.cancel();
        }
        else if (ModelBlockConversion.onUse())
        {
            /* Held down, it repeats at vanilla's placing pace instead of every tick */
            this.itemUseCooldown = 4;
            ci.cancel();
        }
    }
}
