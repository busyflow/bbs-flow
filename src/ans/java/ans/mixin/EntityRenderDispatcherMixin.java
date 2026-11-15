package ans.mixin;

import ans.ActionsAndStuffClientMod;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.resource.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts entity rendering to draw dynamic Bedrock-animated entities and triggers pack reloads.
 */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

    @Inject(
        method = "render(Lnet/minecraft/entity/Entity;DDDFFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private <E extends Entity> void ans$renderEntity(E entity, double x, double y, double z,
                                                     float yaw, float tickDelta,
                                                     MatrixStack matrices,
                                                     VertexConsumerProvider vertexConsumers,
                                                     int light, CallbackInfo ci) {
        matrices.push();
        matrices.translate(x, y, z);
        boolean rendered = ActionsAndStuffClientMod.tryRenderEntity(
            entity, x, y, z, yaw, tickDelta, matrices, vertexConsumers, light
        );
        matrices.pop();
        if (rendered) {
            ci.cancel();
        }
    }

    @Inject(method = "reload(Lnet/minecraft/resource/ResourceManager;)V", at = @At("TAIL"))
    private void ans$onResourceReload(ResourceManager manager, CallbackInfo ci) {
        ActionsAndStuffClientMod.onReload(manager);
    }
}
