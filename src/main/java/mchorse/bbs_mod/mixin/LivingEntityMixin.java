package mchorse.bbs_mod.mixin;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.actions.types.AttackActionClip;
import mchorse.bbs_mod.actions.types.item.ReleaseUseItemActionClip;
import mchorse.bbs_mod.entity.IEntityFormProvider;
import mchorse.bbs_mod.entity.ILimbAnimatorExtension;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.morphing.IMorphProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin
{
    @Shadow protected abstract float turnHead(float bodyYaw, float headTurnSpeed);

    @Inject(method = "applyDamage", at = @At("HEAD"))
    public void onApplyDamage(DamageSource source, float amount, CallbackInfo info)
    {
        Entity attacker = source.getAttacker();

        if (!source.isIndirect() && attacker != null && attacker.getClass() == ServerPlayerEntity.class)
        {
            BBSMod.getActions().addAction((ServerPlayerEntity) attacker, () ->
            {
                AttackActionClip clip = new AttackActionClip();

                clip.damage.set(amount);

                return clip;
            });
        }
    }

    /**
     * The exact vanilla moment a drawn bow fires or a trident launches:
     * stopUsingItem() calls onStoppedUsing() with the remaining use ticks, so
     * this is where the release is recorded - with the very charge the take
     * had. The getClass() check keeps the playback fake player out.
     */
    @Inject(method = "stopUsingItem", at = @At("HEAD"))
    public void onStopUsingItem(CallbackInfo info)
    {
        if ((Object) this instanceof ServerPlayerEntity player && player.getClass() == ServerPlayerEntity.class)
        {
            ItemStack active = player.getActiveItem();

            if (active.isEmpty())
            {
                return;
            }

            boolean mainHand = player.getActiveHand() == Hand.MAIN_HAND;
            int charge = active.getMaxUseTime() - player.getItemUseTimeLeft();
            ItemStack stack = active.copy();
            ItemStack recordedProjectile = player.getProjectileType(active).copy();

            /* Creative players shoot without ammo and vanilla substitutes a
             * plain arrow, but the fake player has no creative mode - so the
             * substitute is baked into the clip instead */
            if (recordedProjectile.isEmpty() && stack.getItem() instanceof RangedWeaponItem && player.getAbilities().creativeMode)
            {
                recordedProjectile = new ItemStack(Items.ARROW);
            }

            ItemStack projectile = recordedProjectile;

            /* TridentItem.onStoppedUsing's own fork, evaluated here because it
             * asks the WORLD, not the item: a riptide trident released in water
             * or rain launches its owner and is never thrown. The playback fake
             * player always stands dry, so the answer has to be recorded. */
            boolean riptide = charge >= 10 && EnchantmentHelper.getRiptide(active) > 0 && player.isTouchingWaterOrRain();

            BBSMod.getActions().addAction(player, () ->
            {
                ReleaseUseItemActionClip clip = new ReleaseUseItemActionClip();

                clip.itemStack.set(stack);
                clip.hand.set(mainHand);
                clip.charge.set(charge);
                clip.projectile.set(projectile);
                clip.riptide.set(riptide);

                return clip;
            });
        }
    }

    @Inject(method = "updateLimbs(F)V", at = @At("HEAD"), cancellable = true)
    public void onUpdateLimbs(float posDelta, CallbackInfo ci)
    {
        Form form = null;
        if ((Object) this instanceof IMorphProvider morphProvider && morphProvider.getMorph() != null)
        {
            form = morphProvider.getMorph().getForm();
        }
        else if ((Object) this instanceof IEntityFormProvider formProvider)
        {
            form = formProvider.getForm();
        }

        if (form != null)
        {
            float scale = form.getScaleY();
            float swingSpeed = form.getSwingSpeed();
            if ((Math.abs(scale - 1F) > 0.001F && scale > 0.001F) || Math.abs(swingSpeed - 1F) > 0.001F)
            {
                float targetSpeed = Math.min(posDelta * 4.0F, 1.0F);
                LivingEntity living = (LivingEntity) (Object) this;
                if (living.limbAnimator instanceof LimbAnimatorAccessor accessor)
                {
                    float prevSpeed = accessor.getSpeed();
                    float newSpeed = prevSpeed + (targetSpeed - prevSpeed) * 0.4F;
                    float divisor = (float) Math.pow(scale, 0.75);
                    float step = (newSpeed / divisor) * swingSpeed;
                    accessor.setPrevSpeed(prevSpeed);
                    accessor.setSpeed(newSpeed);
                    accessor.setPos(accessor.getPos() + step);
                    if (living.limbAnimator instanceof ILimbAnimatorExtension ext)
                    {
                        ext.bbs$setStep(step);
                    }
                    ci.cancel();
                }
            }
        }
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;turnHead(FF)F"))
    private float onTurnHead(LivingEntity entity, float bodyYaw, float f)
    {
        Form form = null;
        if ((Object) this instanceof IMorphProvider morphProvider && morphProvider.getMorph() != null)
        {
            form = morphProvider.getMorph().getForm();
        }
        else if ((Object) this instanceof IEntityFormProvider formProvider)
        {
            form = formProvider.getForm();
        }

        if (form != null)
        {
            float scale = form.getScaleY();
            if (Math.abs(scale - 1F) > 0.001F && scale > 0.001F)
            {
                double dx = entity.getX() - entity.prevX;
                double dz = entity.getZ() - entity.prevZ;
                float distSq = (float) (dx * dx + dz * dz);
                float threshold = 0.0025F * scale * scale;

                if (distSq > threshold)
                {
                    f = ((float) Math.sqrt(distSq) / scale) * 3.0F;
                    float movementYaw = (float) MathHelper.atan2(dz, dx) * 57.295776F - 90.0F;
                    float diff = MathHelper.abs(MathHelper.wrapDegrees(entity.getYaw()) - movementYaw);

                    if (95.0F < diff && diff < 265.0F)
                    {
                        bodyYaw = movementYaw - 180.0F;
                    }
                    else
                    {
                        bodyYaw = movementYaw;
                    }
                }
                if (entity.handSwingProgress > 0.0F)
                {
                    bodyYaw = entity.getYaw();
                }
            }
        }

        return this.turnHead(bodyYaw, f);
    }

    @Inject(method = "getJumpVelocity", at = @At("RETURN"), cancellable = true)
    public void onGetJumpVelocity(CallbackInfoReturnable<Float> cir)
    {
        Form form = null;
        if ((Object) this instanceof IMorphProvider morphProvider && morphProvider.getMorph() != null)
        {
            form = morphProvider.getMorph().getForm();
        }
        else if ((Object) this instanceof IEntityFormProvider formProvider)
        {
            form = formProvider.getForm();
        }

        if (form != null)
        {
            float scale = form.getScaleY();
            if (Math.abs(scale - 1F) > 0.001F && scale > 0.001F)
            {
                /* Froude similarity: real gravity, jump height × s, air time × √s */
                cir.setReturnValue(cir.getReturnValue() * (float) Math.sqrt(scale));
            }
        }
    }

    /**
     * Body scale of a scaled form (1 when unscaled). Movement follows Froude (dynamic) similarity,
     * the way real big and small animals move under the same gravity: lengths × s, velocities × √s,
     * times × √s. A giant walks faster in blocks but its strides are slow and heavy.
     */
    @Unique
    private float bbs$getBodyScale()
    {
        Form form = null;
        if ((Object) this instanceof IMorphProvider morphProvider && morphProvider.getMorph() != null)
        {
            form = morphProvider.getMorph().getForm();
        }
        else if ((Object) this instanceof IEntityFormProvider formProvider)
        {
            form = formProvider.getForm();
        }

        if (form == null)
        {
            return 1F;
        }

        float scale = form.getScaleY();

        return Math.abs(scale - 1F) > 0.001F && scale > 0.001F ? scale : 1F;
    }

    /* Step-up height, so a big body walks over blocks and a tiny one is stopped by slabs */
    @Inject(method = "getStepHeight", at = @At("RETURN"), cancellable = true)
    private void bbs$scaleStepHeight(CallbackInfoReturnable<Float> cir)
    {
        float scale = this.bbs$getBodyScale();
        if (scale != 1F) cir.setReturnValue(cir.getReturnValue() * scale);
    }

    /* Horizontal movement speed scaling so giants move with appropriate weight rather than gliding quickly */
    @Inject(method = "getMovementSpeed()F", at = @At("RETURN"), cancellable = true)
    private void bbs$scaleMovementSpeed(CallbackInfoReturnable<Float> cir)
    {
        float scale = this.bbs$getBodyScale();
        if (scale > 1.001F)
        {
            float speedFactor = (float) (1.0 / Math.pow(scale, 0.25));
            cir.setReturnValue(cir.getReturnValue() * speedFactor);
        }
    }
}