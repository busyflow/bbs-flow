package ans.mixin;

import ans.ActionsAndStuffClientMod;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies Actions &amp; Stuff SP2 keyframed zenith sky and cloud colors across time-of-day and biomes.
 */
@Mixin(ClientWorld.class)
public class ClientWorldMixin {

    @Inject(method = "getSkyColor(Lnet/minecraft/util/math/Vec3d;F)Lnet/minecraft/util/math/Vec3d;", at = @At("HEAD"), cancellable = true)
    private void ans$getSkyColor(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        if (!ActionsAndStuffClientMod.isAtmosphereReady()) return;
        ClientWorld world = (ClientWorld) (Object) this;
        BlockPos pos = BlockPos.ofFloored(cameraPos);
        String biomeId = ActionsAndStuffClientMod.getBiomeId(world, world.getBiome(pos).value());
        float skyAngle = world.getSkyAngle(tickDelta);
        int rgb = ActionsAndStuffClientMod.ATMOSPHERE.getSkyZenithRgb(biomeId, skyAngle);

        double r = ((rgb >> 16) & 0xFF) / 255.0;
        double g = ((rgb >> 8) & 0xFF) / 255.0;
        double b = (rgb & 0xFF) / 255.0;

        float rain = world.getRainGradient(tickDelta);
        if (rain > 0f) {
            double lum = (r * 0.30 + g * 0.59 + b * 0.11) * 0.6;
            r = r * (1.0 - rain * 0.75) + lum * rain * 0.75;
            g = g * (1.0 - rain * 0.75) + lum * rain * 0.75;
            b = b * (1.0 - rain * 0.75) + (lum + 0.05) * rain * 0.75;
        }
        float thunder = world.getThunderGradient(tickDelta);
        if (thunder > 0f) {
            double lum = (r * 0.30 + g * 0.59 + b * 0.11) * 0.25;
            r = r * (1.0 - thunder * 0.80) + lum * thunder * 0.80;
            g = g * (1.0 - thunder * 0.80) + lum * thunder * 0.80;
            b = b * (1.0 - thunder * 0.80) + lum * thunder * 0.80;
        }
        int lightning = world.getLightningTicksLeft();
        if (lightning > 0) {
            float flash = Math.min(1f, (lightning - tickDelta) * 0.45f);
            if (flash > 0f) {
                r = r * (1.0 - flash) + 0.85 * flash;
                g = g * (1.0 - flash) + 0.85 * flash;
                b = b * (1.0 - flash) + 0.95 * flash;
            }
        }
        cir.setReturnValue(new Vec3d(r, g, b));
    }

    @Inject(method = "getCloudsColor(F)Lnet/minecraft/util/math/Vec3d;", at = @At("HEAD"), cancellable = true)
    private void ans$getCloudsColor(float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        if (!ActionsAndStuffClientMod.isAtmosphereReady()) return;
        ClientWorld world = (ClientWorld) (Object) this;
        String biomeId = ActionsAndStuffClientMod.getCurrentBiomeId();
        int rgb = ActionsAndStuffClientMod.ATMOSPHERE.getCloudsRgb(biomeId, world.getSkyAngle(tickDelta));
        double r = ((rgb >> 16) & 0xFF) / 255.0;
        double g = ((rgb >> 8) & 0xFF) / 255.0;
        double b = (rgb & 0xFF) / 255.0;
        cir.setReturnValue(new Vec3d(r, g, b));
    }
}
