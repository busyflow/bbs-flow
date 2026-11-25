package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.film.ExportPlayerLock;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.entity.effect.StatusEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin
{
    @Redirect(
        method = "renderFirstPersonItem",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;isInvisible()Z")
    )
    private boolean bbs$showFirstPersonArmWhileExportLocked(AbstractClientPlayerEntity player)
    {
        if (ExportPlayerLock.isLocked() && !player.hasStatusEffect(StatusEffects.INVISIBILITY))
        {
            return false;
        }

        return player.isInvisible();
    }

    @Redirect(
        method = {"renderMapInOneHand", "renderMapInBothHands"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isInvisible()Z")
    )
    private boolean bbs$showMapArmWhileExportLocked(ClientPlayerEntity player)
    {
        if (ExportPlayerLock.isLocked() && !player.hasStatusEffect(StatusEffects.INVISIBILITY))
        {
            return false;
        }

        return player.isInvisible();
    }
}
