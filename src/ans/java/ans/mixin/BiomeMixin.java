package ans.mixin;

import ans.ActionsAndStuffClientMod;
import ans.atmosphere.BedrockAtmosphereManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Supplies Actions &amp; Stuff SP2 biome sky, fog, water, grass, and foliage palette colors.
 */
@Mixin(Biome.class)
public class BiomeMixin {

    private BedrockAtmosphereManager.BiomeDef ans$resolveBiome() {
        if (!ActionsAndStuffClientMod.isAtmosphereReady()) return null;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) return null;
        String id = ActionsAndStuffClientMod.getBiomeId(client.world, (Biome) (Object) this);
        return ActionsAndStuffClientMod.ATMOSPHERE.resolveBiome(id);
    }

    @Inject(method = "getSkyColor()I", at = @At("HEAD"), cancellable = true)
    private void ans$getSkyColor(CallbackInfoReturnable<Integer> cir) {
        BedrockAtmosphereManager.BiomeDef def = ans$resolveBiome();
        if (def != null) cir.setReturnValue(def.skyColorRgb());
    }

    @Inject(method = "getFogColor()I", at = @At("HEAD"), cancellable = true)
    private void ans$getFogColor(CallbackInfoReturnable<Integer> cir) {
        if (!ActionsAndStuffClientMod.isAtmosphereReady()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) return;
        String id = ActionsAndStuffClientMod.getBiomeId(client.world, (Biome) (Object) this);
        var fog = ActionsAndStuffClientMod.ATMOSPHERE.resolveFog(id);
        if (fog != null) cir.setReturnValue(fog.air().rgb());
    }

    @Inject(method = "getWaterColor()I", at = @At("HEAD"), cancellable = true)
    private void ans$getWaterColor(CallbackInfoReturnable<Integer> cir) {
        BedrockAtmosphereManager.BiomeDef def = ans$resolveBiome();
        if (def != null) cir.setReturnValue(def.waterSurfaceRgb());
    }

    @Inject(method = "getWaterFogColor()I", at = @At("HEAD"), cancellable = true)
    private void ans$getWaterFogColor(CallbackInfoReturnable<Integer> cir) {
        BedrockAtmosphereManager.BiomeDef def = ans$resolveBiome();
        if (def != null) cir.setReturnValue(def.waterFogRgb());
    }

    @Inject(method = "getGrassColorAt(DD)I", at = @At("HEAD"), cancellable = true)
    private void ans$getGrassColorAt(double x, double z, CallbackInfoReturnable<Integer> cir) {
        BedrockAtmosphereManager.BiomeDef def = ans$resolveBiome();
        if (def != null) cir.setReturnValue(def.grassRgb());
    }

    @Inject(method = "getFoliageColor()I", at = @At("HEAD"), cancellable = true)
    private void ans$getFoliageColor(CallbackInfoReturnable<Integer> cir) {
        BedrockAtmosphereManager.BiomeDef def = ans$resolveBiome();
        if (def != null) cir.setReturnValue(def.foliageRgb());
    }
}
