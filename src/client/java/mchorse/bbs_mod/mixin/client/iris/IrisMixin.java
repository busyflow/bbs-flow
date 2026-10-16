package mchorse.bbs_mod.mixin.client.iris;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mchorse.bbs_mod.utils.iris.IrisShaderCurves;
import mchorse.bbs_mod.utils.iris.QueueMap;
import mchorse.bbs_mod.utils.iris.ShaderOptions;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(Iris.class)
public class IrisMixin
{
    @Inject(method = "getShaderPackOptionQueue", at = @At("RETURN"), cancellable = true, remap = false)
    private static void onGetShaderPackOptionQueue(CallbackInfoReturnable<Map<String, String>> info)
    {
        Map<String, String> returnValue = info.getReturnValue() == null ? null : new QueueMap<>(info.getReturnValue());

        info.setReturnValue(returnValue);
    }

    @Inject(method = "loadExternalShaderpack", at = @At("HEAD"), remap = false)
    private static void onLoadExternalShaderpack(String name, CallbackInfoReturnable<Boolean> info)
    {
        IrisShaderCurves.reset(name);
    }

    @Inject(method = "setShadersDisabled", at = @At("HEAD"), remap = false)
    private static void onLoadExternalShaderpack(CallbackInfo info)
    {
        IrisShaderCurves.reset(null);
    }

    @Inject(method = "loadExternalShaderpack", at = @At(value = "RETURN", ordinal = 9), remap = false)
    private static void onTrueReturnLoadExternalShaderPack(CallbackInfoReturnable<Boolean> info)
    {
        IrisShaderCurves.finishLoading();
    }

    /**
     * A pack that doesn't build with its options as uniforms is reloaded going less far. Building
     * reads the programs of a dimension with programs of its own for the first time, and they can
     * turn more options into uniforms, so the editor's shader panel looks again either way.
     */
    @WrapOperation(method = "createPipeline", at = @At(value = "NEW", target = "(Lnet/irisshaders/iris/shaderpack/programs/ProgramSet;)Lnet/irisshaders/iris/pipeline/IrisRenderingPipeline;"), remap = false)
    private static IrisRenderingPipeline onCreatePipeline(ProgramSet programs, Operation<IrisRenderingPipeline> original)
    {
        try
        {
            return original.call(programs);
        }
        catch (RuntimeException e)
        {
            IrisShaderCurves.onPipelineFailed();

            throw e;
        }
        finally
        {
            ShaderOptions.changed();
        }
    }
}
