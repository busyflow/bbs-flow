package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockEditorMenu;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GameRenderer.class, priority = 50)
public class GameRendererPreHandMixin
{
    @Unique
    private static Screen bbs$savedScreen;

    @Inject(method = "renderHand", at = @At("HEAD"))
    private void bbs$beforeRenderHand(CallbackInfo info)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (UIScreen.getCurrentMenu() instanceof UIModelBlockEditorMenu)
        {
            bbs$savedScreen = mc.currentScreen;
            mc.currentScreen = null;
        }
    }

    public static void restoreScreen()
    {
        if (bbs$savedScreen != null)
        {
            MinecraftClient.getInstance().currentScreen = bbs$savedScreen;
            bbs$savedScreen = null;
        }
    }
}
