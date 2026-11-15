package ans.mixin;

import ans.ActionsAndStuffClientMod;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents vanilla two-tone spawn egg tinting from recoloring Actions &amp; Stuff custom 3D spawn egg sprites.
 */
@Mixin(ItemColors.class)
public class ItemColorsMixin {

    @Inject(method = "getColor(Lnet/minecraft/item/ItemStack;I)I", at = @At("HEAD"), cancellable = true)
    private void ans$getColor(ItemStack stack, int tintIndex, CallbackInfoReturnable<Integer> cir) {
        if (!ActionsAndStuffClientMod.isReady() || stack == null || !(stack.getItem() instanceof SpawnEggItem)) {
            return;
        }
        Identifier id = Registries.ITEM.getId(stack.getItem());
        if (id != null && ActionsAndStuffClientMod.PACK.hasTexture("textures/item/" + id.getPath() + ".png")) {
            cir.setReturnValue(-1);
        }
    }
}
