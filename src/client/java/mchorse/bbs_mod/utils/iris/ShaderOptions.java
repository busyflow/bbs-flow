package mchorse.bbs_mod.utils.iris;

import mchorse.bbs_mod.BBSModClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * The editor's way to the shader pack Iris has loaded: which pack is on, its menu and its options.
 * Free of Iris types: it reaches Iris through {@link IrisShaderMenu} and {@link IrisShaderPacks}
 * only once {@link #IRIS} says Iris is there, so the UI can use it with Iris missing.
 */
public final class ShaderOptions
{
    public static final boolean IRIS = FabricLoader.getInstance().isModLoaded("iris");

    /**
     * Counts the pack's loads, switching it off, and its pipeline builds: after any of them the
     * pack may have other options, and other ones may be animated
     */
    private static volatile int revision;

    private ShaderOptions()
    {}

    public static int revision()
    {
        return revision;
    }

    /**
     * What the loaded pack has may have changed: a load started or finished, its pipeline was
     * built, or Iris' menu applied options without a reload
     */
    public static void changed()
    {
        revision += 1;
    }

    /** The loaded pack's menu, or null with Iris missing or no pack on */
    public static ShaderMenu menu()
    {
        return IRIS ? IrisShaderMenu.buildShaderMenu() : null;
    }

    /** The loaded pack's own translations in the game's language */
    public static Map<String, String> lang()
    {
        return IRIS ? IrisShaderMenu.getShadersRawLanguageMap(BBSModClient.getLanguageKey()) : Collections.emptyMap();
    }

    /**
     * Sets an animatable option without a reload: its uniform takes the value at once, the loaded
     * pack has it as its value, and the pack's settings file has it for the next load.
     */
    public static void setLive(String optionId, String value) throws IOException
    {
        if (IRIS)
        {
            IrisShaderMenu.setLive(Collections.singletonMap(optionId, value));
        }
    }

    /** Sets the options and reloads the pack once to apply them */
    public static void apply(Map<String, String> values) throws IOException
    {
        if (IRIS)
        {
            IrisShaderMenu.apply(values);
        }
    }

    /**
     * Runs a pack reload once input handling is over, at the start of the next frame. Clicks and
     * keys arrive while GLFW polls the window's events, and the game holds its keep-alive replies
     * back while that lasts: a pack compiling in there for longer than the server waits for one
     * (15–30 s) dropped the player, even from a singleplayer world.
     */
    public static void reload(Reload reload, Runnable failed)
    {
        MinecraftClient.getInstance().send(() ->
        {
            try
            {
                reload.run();
            }
            catch (Exception e)
            {
                e.printStackTrace();
                failed.run();
            }
        });
    }

    /** Name of the pack that's on, or an empty string */
    public static String currentPack()
    {
        return IRIS ? IrisShaderPacks.current() : "";
    }

    public interface Reload
    {
        void run() throws Exception;
    }
}
