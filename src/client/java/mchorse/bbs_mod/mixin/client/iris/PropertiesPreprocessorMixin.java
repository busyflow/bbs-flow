package mchorse.bbs_mod.mixin.client.iris;

import mchorse.bbs_mod.utils.iris.IrisShaderCurves;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;
import net.irisshaders.iris.shaderpack.preprocessor.PropertiesPreprocessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Options a properties file of the pack reads (programs, buffers, block ids) can't become uniforms */
@Mixin(PropertiesPreprocessor.class)
public class PropertiesPreprocessorMixin
{
    @Inject(method = "preprocessSource(Ljava/lang/String;Lnet/irisshaders/iris/shaderpack/option/ShaderPackOptions;Ljava/lang/Iterable;)Ljava/lang/String;", at = @At("HEAD"), remap = false)
    private static void onPreprocessSource(String source, ShaderPackOptions options, Iterable<StringPair> environmentDefines, CallbackInfoReturnable<String> info)
    {
        IrisShaderCurves.processProperties(source);
    }
}
