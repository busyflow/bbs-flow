package mchorse.bbs_mod.film;

import mchorse.bbs_mod.network.ClientNetwork;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Client half of {@link mchorse.bbs_mod.actions.ExportPlayerGuard}: holds the
 * local player pinned where they stood when an export began, clears movement
 * input and velocity, and tells the server to switch them into Spectator mode
 * with invulnerability for the duration of the export.
 */
public final class ExportPlayerLock
{
    private static int lockCount;
    private static boolean hasSavedPos;
    private static double savedX;
    private static double savedY;
    private static double savedZ;
    private static boolean savedAllowFlying;
    private static boolean savedFlying;

    private ExportPlayerLock()
    {}

    public static void acquire()
    {
        if (lockCount++ == 0)
        {
            MinecraftClient mc = MinecraftClient.getInstance();
            ClientPlayerEntity player = mc.player;

            if (player != null)
            {
                savedX = player.getX();
                savedY = player.getY();
                savedZ = player.getZ();
                savedAllowFlying = player.getAbilities().allowFlying;
                savedFlying = player.getAbilities().flying;
                hasSavedPos = true;

                freezeClientPlayer(player);
            }
            else
            {
                hasSavedPos = false;
            }

            if (ClientNetwork.isIsBBSModOnServer())
            {
                ClientNetwork.sendExportPlayerState(true);
            }
        }
    }

    public static void release()
    {
        if (lockCount <= 0)
        {
            lockCount = 0;

            return;
        }

        if (--lockCount == 0)
        {
            if (ClientNetwork.isIsBBSModOnServer())
            {
                ClientNetwork.sendExportPlayerState(false);
            }

            MinecraftClient mc = MinecraftClient.getInstance();
            ClientPlayerEntity player = mc.player;

            if (player != null && hasSavedPos)
            {
                pinPosition(player);
                player.getAbilities().allowFlying = savedAllowFlying;
                player.getAbilities().flying = savedFlying;
            }

            hasSavedPos = false;
        }
    }

    public static void tick()
    {
        if (lockCount <= 0)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;

        if (player != null)
        {
            freezeClientPlayer(player);
        }
    }

    public static boolean isLocked()
    {
        return lockCount > 0;
    }

    public static void reset()
    {
        lockCount = 0;
        hasSavedPos = false;
    }

    private static void freezeClientPlayer(ClientPlayerEntity player)
    {
        player.setVelocity(0D, 0D, 0D);
        player.fallDistance = 0F;
        player.getAbilities().flying = true;

        if (player.input != null)
        {
            player.input.movementForward = 0F;
            player.input.movementSideways = 0F;
            player.input.pressingForward = false;
            player.input.pressingBack = false;
            player.input.pressingLeft = false;
            player.input.pressingRight = false;
            player.input.jumping = false;
            player.input.sneaking = false;
        }

        if (hasSavedPos)
        {
            pinPosition(player);
        }
    }

    private static void pinPosition(ClientPlayerEntity player)
    {
        player.setVelocity(0D, 0D, 0D);
        player.fallDistance = 0F;
        player.setPosition(savedX, savedY, savedZ);
        player.prevX = savedX;
        player.prevY = savedY;
        player.prevZ = savedZ;
        player.lastRenderX = savedX;
        player.lastRenderY = savedY;
        player.lastRenderZ = savedZ;
    }
}
