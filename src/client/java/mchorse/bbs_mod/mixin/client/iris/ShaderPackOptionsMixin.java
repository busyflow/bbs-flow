package mchorse.bbs_mod.mixin.client.iris;

import mchorse.bbs_mod.utils.iris.IrisShaderCurves;
import net.irisshaders.iris.shaderpack.option.OptionSet;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The pack's options, as Iris found them, before any program source is read */
@Mixin(ShaderPackOptions.class)
public class ShaderPackOptionsMixin
{
    @Shadow(remap = false) @Final private OptionSet optionSet;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void onInit(CallbackInfo info)
    {
        IrisShaderCurves.setOptions(this.optionSet);
    }
}
