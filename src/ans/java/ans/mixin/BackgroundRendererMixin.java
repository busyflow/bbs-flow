package ans.mixin;

import ans.ActionsAndStuffClientMod;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.CameraSubmersionType;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies Actions &amp; Stuff horizon/fog colors and biome fog start/end distances.
 */
@Mixin(BackgroundRenderer.class)
public class BackgroundRendererMixin {

    @Shadow
    private static float red;

    @Shadow
    private static float green;

    @Shadow
    private static float blue;

    @Inject(
        method = "render(Lnet/minecraft/client/render/Camera;FLnet/minecraft/client/world/ClientWorld;IF)V",
        at = @At("TAIL")
    )
    private static void ans$renderFogColor(Camera camera, float tickDelta, ClientWorld world,
                                             int viewDistance, float skyDarkness, CallbackInfo ci) {
        if (!ActionsAndStuffClientMod.isAtmosphereReady() || world == null || camera == null) return;
        CameraSubmersionType sub = camera.getSubmersionType();
        if (sub == CameraSubmersionType.LAVA || sub == CameraSubmersionType.POWDER_SNOW) {
            return;
        }
        String biomeId = ActionsAndStuffClientMod.getBiomeId(world, world.getBiome(camera.getBlockPos()).value());
        float skyAngle = world.getSkyAngle(tickDelta);
        boolean underwater = sub == CameraSubmersionType.WATER;
        boolean raining = !underwater && world.getRainGradient(tickDelta) > 0.2f;
        int rgb = ActionsAndStuffClientMod.ATMOSPHERE.getFogRgb(biomeId, skyAngle, underwater, raining);

        float nr = ((rgb >> 16) & 0xFF) / 255f;
        float ng = ((rgb >> 8) & 0xFF) / 255f;
        float nb = (rgb & 0xFF) / 255f;
        float dim = Math.max(0.05f, 1.0f - skyDarkness);
        red = nr * dim;
        green = ng * dim;
        blue = nb * dim;
        RenderSystem.clearColor(red, green, blue, 0.0f);
    }

    @Inject(
        method = "applyFog(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/BackgroundRenderer$FogType;FZF)V",
        at = @At("TAIL")
    )
    private static void ans$applyFogDistances(Camera camera, BackgroundRenderer.FogType fogType,
                                                float viewDistance, boolean thickFog, float tickDelta,
                                                CallbackInfo ci) {
        if (!ActionsAndStuffClientMod.isAtmosphereReady() || camera == null) return;
        if (fogType != BackgroundRenderer.FogType.FOG_TERRAIN) return;
        CameraSubmersionType sub = camera.getSubmersionType();
        if (sub == CameraSubmersionType.LAVA || sub == CameraSubmersionType.POWDER_SNOW) {
            return;
        }
        String biomeId = ActionsAndStuffClientMod.getCurrentBiomeId();
        boolean underwater = sub == CameraSubmersionType.WATER;
        boolean raining = !underwater && net.minecraft.client.MinecraftClient.getInstance().world != null
            && net.minecraft.client.MinecraftClient.getInstance().world.getRainGradient(tickDelta) > 0.2f;

        float start = ActionsAndStuffClientMod.ATMOSPHERE.getFogStartBlocks(biomeId, underwater, raining, viewDistance);
        float end = ActionsAndStuffClientMod.ATMOSPHERE.getFogEndBlocks(biomeId, underwater, raining, viewDistance);
        if (Float.isFinite(start) && Float.isFinite(end) && end > start && end >= 8f) {
            RenderSystem.setShaderFogStart(start);
            RenderSystem.setShaderFogEnd(end);
        }
    }
}
