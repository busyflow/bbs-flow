package mchorse.bbs_mod.client;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.blocks.ModelBlock;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.network.ClientNetwork;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

/**
 * Alt + right click with a model block in hand turns the block under the crosshair into a model
 * block showing that same block, so a set becomes movable one poke at a time. The swap itself is
 * the server's, see {@link ModelBlock#convert}; creative only, as the server also insists.
 *
 * <p>The click is taken at MinecraftClient.doItemUse (see MinecraftClientMixin), before the item
 * would place a new model block next to the clicked one. While Alt is held every click is taken,
 * even one that converts nothing: poking along a row, a miss must not drop a stray model block or
 * open the dashboard on a block that already is one.</p>
 */
public class ModelBlockConversion
{
    private static final String TOOLTIP = "block.bbs.model.tooltip.convert";

    public static void register()
    {
        ItemTooltipCallback.EVENT.register((stack, context, lines) ->
        {
            if (stack.isOf(BBSMod.MODEL_BLOCK_ITEM))
            {
                lines.add(Text.translatable(TOOLTIP).formatted(Formatting.GRAY));
            }
        });
    }

    /** True when the click was the conversion's and the item must not be used. */
    public static boolean onUse()
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity player = mc.player;

        if (player == null || mc.currentScreen != null || !player.isCreative() || !Window.isAltPressed())
        {
            return false;
        }

        Hand hand = getHand(player);

        if (hand == null)
        {
            return false;
        }

        if (mc.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK)
        {
            BlockPos pos = hit.getBlockPos();

            if (ModelBlock.canConvert(mc.world.getBlockState(pos)))
            {
                ClientNetwork.sendConvertModelBlock(pos);
                player.swingHand(hand);
            }
        }

        return true;
    }

    private static Hand getHand(PlayerEntity player)
    {
        if (player.getMainHandStack().isOf(BBSMod.MODEL_BLOCK_ITEM))
        {
            return Hand.MAIN_HAND;
        }

        return player.getOffHandStack().isOf(BBSMod.MODEL_BLOCK_ITEM) ? Hand.OFF_HAND : null;
    }
}
