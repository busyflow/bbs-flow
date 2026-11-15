package ans.mixin;

import ans.ActionsAndStuffClientMod;
import net.minecraft.client.color.world.FoliageColors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Overrides birch, spruce/evergreen, and mangrove foliage tint defaults with SP2 colormap values.
 */
@Mixin(FoliageColors.class)
public class FoliageColorsMixin {

    @Inject(method = "getSpruceColor()I", at = @At("HEAD"), cancellable = true)
    private static void ans$getSpruceColor(CallbackInfoReturnable<Integer> cir) {
        if (ActionsAndStuffClientMod.isAtmosphereReady()) {
            cir.setReturnValue(ActionsAndStuffClientMod.ATMOSPHERE.getSpruceFoliageColor());
        }
    }

    @Inject(method = "getBirchColor()I", at = @At("HEAD"), cancellable = true)
    private static void ans$getBirchColor(CallbackInfoReturnable<Integer> cir) {
        if (ActionsAndStuffClientMod.isAtmosphereReady()) {
            cir.setReturnValue(ActionsAndStuffClientMod.ATMOSPHERE.getBirchFoliageColor());
        }
    }

    @Inject(method = "getMangroveColor()I", at = @At("HEAD"), cancellable = true)
    private static void ans$getMangroveColor(CallbackInfoReturnable<Integer> cir) {
        if (ActionsAndStuffClientMod.isAtmosphereReady()) {
            cir.setReturnValue(ActionsAndStuffClientMod.ATMOSPHERE.getMangroveFoliageColor());
        }
    }
}
