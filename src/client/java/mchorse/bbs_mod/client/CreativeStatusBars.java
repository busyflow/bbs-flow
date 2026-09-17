package mchorse.bbs_mod.client;

import mchorse.bbs_mod.BBSSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.world.GameMode;

/**
 * Whether the hearts and hunger bar are being shown in creative on purpose.
 *
 * <p>Vanilla hides both outside survival, which is right for playing and wrong for filming: a shot
 * of a character who is supposed to be in danger reads as a character who is not, and the usual
 * way round it is to switch to survival and then be in survival.</p>
 *
 * <p>Only ever adds bars. In survival the game is already drawing them and none of this is
 * consulted, so nothing here can take away a bar that is meant to be there.</p>
 */
public class CreativeStatusBars
{
    private CreativeStatusBars()
    {}

    /** Whether either bar has been asked for in a mode that would not normally show them. */
    public static boolean isForced()
    {
        return isCreative() && (BBSSettings.creativeShowHearts.get() || BBSSettings.creativeShowHunger.get());
    }

    public static boolean hidesHearts()
    {
        return isForced() && !BBSSettings.creativeShowHearts.get();
    }

    public static boolean hidesHunger()
    {
        return isForced() && !BBSSettings.creativeShowHunger.get();
    }

    /**
     * The experience bar, which vanilla gates on its own answer rather than on the status bars.
     *
     * <p>Kept separate here for the same reason: asking for the bar and the level number is not
     * asking for hearts, and a shot that wants one rarely wants the other.</p>
     */
    public static boolean showsExperience()
    {
        return isCreative() && BBSSettings.creativeShowXpBar.get();
    }

    /**
     * Creative only, deliberately not spectator: a spectator has no body to be hurt or fed, and
     * the bars would be reporting on someone who is not there.
     */
    private static boolean isCreative()
    {
        ClientPlayerInteractionManager manager = MinecraftClient.getInstance().interactionManager;

        return manager != null && manager.getCurrentGameMode() == GameMode.CREATIVE;
    }
}
