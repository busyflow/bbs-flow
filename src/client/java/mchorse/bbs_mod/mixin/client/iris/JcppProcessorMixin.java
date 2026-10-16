package mchorse.bbs_mod.mixin.client.iris;

import com.llamalad7.mixinextras.sugar.Local;
import mchorse.bbs_mod.utils.iris.IrisShaderCurves;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(JcppProcessor.class)
public class JcppProcessorMixin
{
    @ModifyVariable(method = "glslPreprocessSource", at = @At("HEAD"), ordinal = 0, argsOnly = true, remap = false)
    private static String returnClean(String source, @Local(argsOnly = true) Iterable<StringPair> environmentDefines)
    {
        return IrisShaderCurves.processSource(source, environmentDefines);
    }
}
