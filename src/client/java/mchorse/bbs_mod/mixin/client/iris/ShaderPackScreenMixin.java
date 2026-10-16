package mchorse.bbs_mod.mixin.client.iris;

import mchorse.bbs_mod.utils.iris.IrisShaderMenu;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Options that became uniforms show a change in Iris' menu at once ({@link SliderElementWidgetMixin},
 * {@link mchorse.bbs_mod.utils.iris.QueueMap}), so applying them needs no reload, and dropping
 * the changes has to put them back.
 */
@Mixin(ShaderPackScreen.class)
public class ShaderPackScreenMixin
{
    /**
     * Apply (Done and Esc too) with the same pack on: Iris reloads it only when its option queue
     * isn't empty, so a queue of animatable options is applied here and emptied first
     */
    @Inject(method = "applyChanges", at = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/Iris;getShaderPackOptionQueue()Ljava/util/Map;"), remap = false)
    private void onApplyChanges(CallbackInfo info)
    {
        IrisShaderMenu.applyQueueLive();
    }

    @Inject(method = "discardChanges", at = @At("HEAD"), remap = false)
    private void onDiscardChanges(CallbackInfo info)
    {
        IrisShaderMenu.revertQueue();
    }
}
