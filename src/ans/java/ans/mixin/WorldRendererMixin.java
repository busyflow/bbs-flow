package ans.mixin;

import ans.ActionsAndStuffClientMod;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders Bedrock particles spawned by Actions &amp; Stuff animations and controllers.
 */
@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    @Inject(
        method = "render(Lnet/minecraft/client/util/math/MatrixStack;FJZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;)V",
        at = @At("TAIL")
    )
    private void ans$renderParticles(MatrixStack matrices, float tickDelta, long limitTime,
                                       boolean renderBlockOutline, Camera camera,
                                       GameRenderer gameRenderer,
                                       LightmapTextureManager lightmapTextureManager,
                                       Matrix4f projectionMatrix, CallbackInfo ci) {
        if (ActionsAndStuffClientMod.isReady()) {
            ActionsAndStuffClientMod.PARTICLES.render(matrices, camera);
        }
    }
}
