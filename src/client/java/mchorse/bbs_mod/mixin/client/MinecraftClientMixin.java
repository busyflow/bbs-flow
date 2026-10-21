package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.ModelBlockConversion;
import mchorse.bbs_mod.forms.structure.StructureWand;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
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

    /**
     * Every way out of a world ends up here: the pause menu, a kick, a lost connection, closing
     * the game, and entering another world (which starts by disconnecting). By the end of it the
     * screens on top have been closed - and have saved what they had open - so it is the moment
     * BBS lets go of the session. Not when the game itself is closing: there is no next world to
     * keep this one from, and reloading the assets would only hold up the exit.
     */
    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;)V", at = @At("RETURN"))
    public void leaveWorld(Screen screen, CallbackInfo ci)
    {
        if (((MinecraftClient) (Object) this).isRunning())
        {
            BBSModClient.leaveWorld();
        }
    }
}
