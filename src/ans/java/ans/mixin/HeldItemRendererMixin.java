package ans.mixin;

import ans.ActionsAndStuffClientMod;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces first-person and third-person held items with dynamic Bedrock attachable animations.
 */
@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {

    @Inject(
        method = "renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void ans$renderFirstPersonItem(AbstractClientPlayerEntity player, float tickDelta, float pitch,
                                             Hand hand, float swingProgress, ItemStack item, float equipProgress,
                                             MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                             int light, CallbackInfo ci) {
        if (ActionsAndStuffClientMod.tryRenderFirstPersonItem(
            player, tickDelta, pitch, hand, swingProgress, item, equipProgress, matrices, vertexConsumers, light
        )) {
            ci.cancel();
        }
    }

    @Inject(
        method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void ans$renderItem(LivingEntity entity, ItemStack stack, ModelTransformationMode renderMode,
                                boolean leftHanded, MatrixStack matrices,
                                VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (ActionsAndStuffClientMod.tryRenderItemByMode(
            entity, stack, renderMode, leftHanded, matrices, vertexConsumers, light
        )) {
            ci.cancel();
        }
    }
}
