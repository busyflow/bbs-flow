package ans.mixin;

import ans.ActionsAndStuffClientMod;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Advances active Bedrock particle emitters and handles initial lazy pack loading on client ticks.
 */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Inject(method = "render(Z)V", at = @At("HEAD"))
    private void ans$onRenderHead(boolean tick, CallbackInfo ci) {
        ActionsAndStuffClientMod.updateFrameClock();
    }

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void ans$onTick(CallbackInfo ci) {
        ActionsAndStuffClientMod.onClientTick((MinecraftClient) (Object) this);
    }
}
