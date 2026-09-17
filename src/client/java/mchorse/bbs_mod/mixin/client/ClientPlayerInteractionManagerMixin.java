package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.client.CreativeStatusBars;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets the status bars be drawn in creative when they have been asked for.
 *
 * <p>This one answer is what the HUD asks before drawing hearts, armour, hunger and air, so it is
 * the whole gate. Which of those actually appear is decided in {@link InGameHudMixin}.</p>
 */
@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin
{
    @Inject(method = "hasStatusBars", at = @At("HEAD"), cancellable = true)
    private void bbs$showStatusBarsInCreative(CallbackInfoReturnable<Boolean> info)
    {
        if (CreativeStatusBars.isForced())
        {
            info.setReturnValue(true);
        }
    }

    /** The experience bar and the level number above it are both behind this one answer. */
    @Inject(method = "hasExperienceBar", at = @At("HEAD"), cancellable = true)
    private void bbs$showExperienceBarInCreative(CallbackInfoReturnable<Boolean> info)
    {
        if (CreativeStatusBars.showsExperience())
        {
            info.setReturnValue(true);
        }
    }
}
