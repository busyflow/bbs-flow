package mchorse.bbs_mod.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.controller.ICameraController;
import mchorse.bbs_mod.camera.controller.PlayCameraController;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.CreativeStatusBars;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin
{
    @Inject(method = "render", at = @At(value = "HEAD"), cancellable = true)
    public void render(DrawContext drawContext, float tickDelta, CallbackInfo info)
    {
        ICameraController current = BBSModClient.getCameraController().getCurrent();

        if (current instanceof PlayCameraController)
        {
            BBSRendering.onRenderBeforeScreen();

            info.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    public void onRenderEnd(CallbackInfo info)
    {
        BBSRendering.onRenderBeforeScreen();
    }

    /**
     * Drop the hearts when only the hunger bar was asked for.
     *
     * <p>Wrapped at the call rather than inside the drawing, so the bar is simply never asked
     * for and everything that follows still lays out where it expects to.</p>
     */
    @WrapOperation(
        method = "renderStatusBars",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/hud/InGameHud;renderHealthBar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/entity/player/PlayerEntity;IIIIFIIIZ)V"
        )
    )
    private void bbs$hideHeartsInCreative(InGameHud hud, DrawContext context, PlayerEntity player,
        int x, int y, int lines, int regeneratingHeart, float maxHealth, int lastHealth,
        int health, int absorption, boolean blinking, Operation<Void> original)
    {
        if (!CreativeStatusBars.hidesHearts())
        {
            original.call(hud, context, player, x, y, lines, regeneratingHeart, maxHealth,
                lastHealth, health, absorption, blinking);
        }
    }

    /**
     * Drop the hunger bar when only the hearts were asked for.
     *
     * <p>Picked out by where it reads from the icon sheet: the food row starts at 27, and the
     * armour, air and heart rows are all elsewhere in it. There is no method of its own to wrap -
     * the bar is drawn inline - so the texture row is what distinguishes it.</p>
     */
    @WrapOperation(
        method = "renderStatusBars",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V"
        )
    )
    private void bbs$hideHungerInCreative(DrawContext context, Identifier texture, int x, int y,
        int u, int v, int width, int height, Operation<Void> original)
    {
        if (v == FOOD_ICON_ROW && CreativeStatusBars.hidesHunger())
        {
            return;
        }

        original.call(context, texture, x, y, u, v, width, height);
    }

    /** The row of icons.png the hunger bar is drawn from. */
    private static final int FOOD_ICON_ROW = 27;
}