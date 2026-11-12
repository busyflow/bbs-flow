package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.controller.CameraController;
import mchorse.bbs_mod.entity.IEntityFormProvider;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.morphing.IMorphProvider;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin
{
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow protected abstract void setPos(double x, double y, double z);
    @Shadow private Entity focusedEntity;

    @ModifyArg(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;clipToSpace(D)D"), index = 0)
    private double onClipToSpace(double desiredCameraDistance)
    {
        Entity focused = this.focusedEntity;
        Form form = null;

        if (focused instanceof IMorphProvider provider && provider.getMorph() != null)
        {
            form = provider.getMorph().getForm();
        }
        else if (focused instanceof IEntityFormProvider provider)
        {
            form = provider.getForm();
        }

        if (form != null)
        {
            float scale = form.getScaleY();

            if (Math.abs(scale - 1F) > 0.001F && scale > 0.001F)
            {
                return desiredCameraDistance * scale;
            }
        }

        return desiredCameraDistance;
    }

    @Inject(method = "update", at = @At(value = "RETURN"))
    public void onUpdate(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci)
    {
        CameraController controller = BBSModClient.getCameraController();

        if (controller.getCurrent() != null)
        {
            Vector3d position = controller.getPosition();
            float yaw = controller.getYaw();
            float pitch = controller.getPitch();

            this.setPos(position.x, position.y, position.z);
            this.setRotation(yaw, pitch);
        }
    }
}