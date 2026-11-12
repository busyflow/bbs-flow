package mchorse.bbs_mod.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mchorse.bbs_mod.entity.IEntityFormProvider;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.morphing.IMorphProvider;
import mchorse.bbs_mod.particles.vanilla.HeldParticles;
import mchorse.bbs_mod.particles.vanilla.IHeldParticle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the particles a form let into the world on that form's time (see
 * {@link HeldParticles}): they don't tick while it stands still, and are drawn
 * where it stands between two ticks rather than where the world does.
 */
@Mixin(ParticleManager.class)
public class ParticleManagerMixin
{
    @Inject(method = "createParticle", at = @At("RETURN"))
    private void bbs$scaleParticle(ParticleEffect parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ, CallbackInfoReturnable<Particle> cir)
    {
        Particle particle = cir.getReturnValue();

        if (particle != null)
        {
            float scale = 1.0F;
            MinecraftClient client = MinecraftClient.getInstance();

            if (client.world != null)
            {
                Entity cameraEntity = client.cameraEntity;
                if (cameraEntity != null)
                {
                    Form form = null;
                    if (cameraEntity instanceof IMorphProvider provider && provider.getMorph() != null)
                    {
                        form = provider.getMorph().getForm();
                    }
                    else if (cameraEntity instanceof IEntityFormProvider provider)
                    {
                        form = provider.getForm();
                    }

                    if (form != null)
                    {
                        float s = form.getScaleY();
                        if (Math.abs(s - 1.0F) > 0.001F && s > 0.001F)
                        {
                            double distSq = cameraEntity.squaredDistanceTo(x, y, z);
                            double radius = Math.max(2.0, 3.0 * s);
                            if (distSq <= radius * radius)
                            {
                                scale = s;
                            }
                        }
                    }
                }

                if (scale == 1.0F && client.cameraEntity != null)
                {
                    for (Entity entity : client.world.getOtherEntities(client.cameraEntity, new Box(x - 3.0, y - 3.0, z - 3.0, x + 3.0, y + 3.0, z + 3.0)))
                    {
                        Form form = null;
                        if (entity instanceof IMorphProvider provider && provider.getMorph() != null)
                        {
                            form = provider.getMorph().getForm();
                        }
                        else if (entity instanceof IEntityFormProvider provider)
                        {
                            form = provider.getForm();
                        }

                        if (form != null)
                        {
                            float s = form.getScaleY();
                            if (Math.abs(s - 1.0F) > 0.001F && s > 0.001F)
                            {
                                double distSq = entity.squaredDistanceTo(x, y, z);
                                double radius = Math.max(2.0, 3.0 * s);
                                if (distSq <= radius * radius)
                                {
                                    scale = s;
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            if (Math.abs(scale - 1.0F) > 0.001F && scale > 0.001F)
            {
                particle.scale(scale);
            }
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void bbs$advanceHeldClock(CallbackInfo info)
    {
        HeldParticles.advance();
    }

    @Inject(method = "tickParticle", at = @At("HEAD"), cancellable = true)
    private void bbs$holdStoppedParticle(Particle particle, CallbackInfo info)
    {
        HeldParticles holder = ((IHeldParticle) particle).bbs$getHolder();

        if (holder != null && holder.isStopped())
        {
            info.cancel();
        }
    }

    @WrapOperation(method = "renderParticles", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/Particle;buildGeometry(Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/client/render/Camera;F)V"))
    private void bbs$drawHeldParticle(Particle particle, VertexConsumer consumer, Camera camera, float tickDelta, Operation<Void> original)
    {
        HeldParticles holder = ((IHeldParticle) particle).bbs$getHolder();

        if (holder == null)
        {
            original.call(particle, consumer, camera, tickDelta);

            return;
        }

        /* Taken back by its form: gone at once, not when the manager clears it
         * out a tick later (two, for one let in during the same tick) */
        if (!particle.isAlive())
        {
            return;
        }

        original.call(particle, consumer, camera, holder.isStopped() ? holder.getTransition() : tickDelta);
    }
}
