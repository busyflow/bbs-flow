package mchorse.bbs_mod.utils;

import mchorse.bbs_mod.forms.entities.IEntity;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Method;

/**
 * Soft-linked bridge between BBS Flow and the standalone Realistic Biomes 1.3 Fabric mod.
 * Safe when Realistic Biomes is not installed (all methods degrade to no-ops).
 */
public final class RealisticBiomesBridge
{
    private static boolean initialized;
    private static boolean available;

    private static Method isEnabledMethod;
    private static Method toggleEnabledMethod;
    private static Method openSettingsMethod;
    private static Method setClipGroundFogOpacityMethod;
    private static Method beginReplayLightsMethod;
    private static Method reportReplayHeldLightMethod;

    private RealisticBiomesBridge()
    {}

    private static void init()
    {
        if (initialized)
        {
            return;
        }

        initialized = true;

        if (!FabricLoader.getInstance().isModLoaded("realistic_biomes"))
        {
            return;
        }

        try
        {
            Class<?> clientCls = Class.forName("com.busyflow.rb.RealisticBiomesClient");
            isEnabledMethod = clientCls.getMethod("isEnabled");
            toggleEnabledMethod = clientCls.getMethod("toggleEnabled");
            openSettingsMethod = clientCls.getMethod("openSettingsScreen");
            try
            {
                setClipGroundFogOpacityMethod = clientCls.getMethod("setClipGroundFogOpacity", float.class);
            }
            catch (NoSuchMethodException ignored)
            {}

            Class<?> lightCls = Class.forName("com.busyflow.rb.light.DynamicLightManager");
            beginReplayLightsMethod = lightCls.getMethod("beginReplayLights");
            reportReplayHeldLightMethod = lightCls.getMethod(
                "reportReplayHeldLight", double.class, double.class, double.class, ItemStack.class, ItemStack.class
            );

            available = true;
        }
        catch (Throwable ignored)
        {
            available = false;
        }
    }

    public static boolean isLoaded()
    {
        init();
        return available;
    }

    public static boolean isEnabled()
    {
        init();
        if (!available || isEnabledMethod == null)
        {
            return false;
        }

        try
        {
            return Boolean.TRUE.equals(isEnabledMethod.invoke(null));
        }
        catch (Throwable ignored)
        {
            return false;
        }
    }

    public static void toggleEnabled()
    {
        init();
        if (!available || toggleEnabledMethod == null)
        {
            return;
        }

        try
        {
            toggleEnabledMethod.invoke(null);
        }
        catch (Throwable ignored)
        {}
    }

    public static void openSettings()
    {
        init();
        if (!available || openSettingsMethod == null)
        {
            return;
        }

        try
        {
            openSettingsMethod.invoke(null);
        }
        catch (Throwable ignored)
        {}
    }

    public static void setClipGroundFogOpacity(float opacity)
    {
        init();
        if (!available || setClipGroundFogOpacityMethod == null)
        {
            return;
        }

        try
        {
            setClipGroundFogOpacityMethod.invoke(null, opacity);
        }
        catch (Throwable ignored)
        {}
    }

    public static void beginReplayLights()
    {
        init();
        if (!available || beginReplayLightsMethod == null)
        {
            return;
        }

        try
        {
            beginReplayLightsMethod.invoke(null);
        }
        catch (Throwable ignored)
        {}
    }

    public static void reportReplayHeldLight(IEntity entity)
    {
        if (!available || reportReplayHeldLightMethod == null || entity == null)
        {
            return;
        }

        ItemStack main = entity.getEquipmentStack(EquipmentSlot.MAINHAND);
        ItemStack off = entity.getEquipmentStack(EquipmentSlot.OFFHAND);

        if ((main == null || main.isEmpty()) && (off == null || off.isEmpty()))
        {
            return;
        }

        try
        {
            reportReplayHeldLightMethod.invoke(null, entity.getX(), entity.getY() + 1.4D, entity.getZ(), main, off);
        }
        catch (Throwable ignored)
        {}
    }
}
