package mchorse.bbs_mod.mixin;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.actions.types.AttackActionClip;
import mchorse.bbs_mod.actions.types.item.ReleaseUseItemActionClip;
import mchorse.bbs_mod.entity.IEntityFormProvider;
import mchorse.bbs_mod.entity.ILimbAnimatorExtension;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.morphing.IMorphProvider;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMixin
{
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
            if (Math.abs(scale - 1F) > 0.001F && scale > 0.001F)
            {
                float targetSpeed = Math.min(posDelta * 4.0F, 1.0F);
                LivingEntity living = (LivingEntity) (Object) this;
                if (living.limbAnimator instanceof LimbAnimatorAccessor accessor)
                {
                    float prevSpeed = accessor.getSpeed();
                    float newSpeed = prevSpeed + (targetSpeed - prevSpeed) * 0.4F;
                    float step = newSpeed / scale;
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

    /* @Inject(method = "swingHand(Lnet/minecraft/util/Hand;Z)V", at = @At("HEAD"), cancellable = true)
    public void onSwingHand(Hand hand, boolean fromServerPlayer, CallbackInfo info)
    {
        info.cancel();
    } */
}