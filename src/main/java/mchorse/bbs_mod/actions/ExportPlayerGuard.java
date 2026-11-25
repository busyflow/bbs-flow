package mchorse.bbs_mod.actions;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Holds a player frozen in place, invulnerable and in Spectator mode while
 * a video export runs, then restores their position, mode, flight and
 * invulnerability when the export ends or the player leaves.
 */
public final class ExportPlayerGuard
{
    private static final Map<UUID, GuardState> GUARDED = new HashMap<>();

    private ExportPlayerGuard()
    {}

    public static void acquire(ServerPlayerEntity player, GameMode followSavedMode)
    {
        if (player == null)
        {
            return;
        }

        GuardState existing = GUARDED.get(player.getUuid());

        if (existing != null)
        {
            existing.count += 1;

            return;
        }

        GameMode mode = followSavedMode != null ? followSavedMode : player.interactionManager.getGameMode();
        GuardState state = new GuardState(
            player.getServerWorld().getRegistryKey(),
            player.getX(),
            player.getY(),
            player.getZ(),
            player.getYaw(),
            player.getPitch(),
            mode,
            player.isInvulnerable(),
            player.getAbilities().allowFlying,
            player.getAbilities().flying
        );

        GUARDED.put(player.getUuid(), state);
        applyLock(player, state);
    }

    public static void release(ServerPlayerEntity player, boolean force)
    {
        if (player == null)
        {
            return;
        }

        GuardState state = GUARDED.get(player.getUuid());

        if (state == null)
        {
            return;
        }

        if (!force && --state.count > 0)
        {
            return;
        }

        GUARDED.remove(player.getUuid());

        player.setVelocity(Vec3d.ZERO);
        player.velocityModified = true;
        player.fallDistance = 0F;
        player.setInvulnerable(state.invulnerable);
        player.changeGameMode(state.gameMode);
        player.getAbilities().allowFlying = state.allowFlying;
        player.getAbilities().flying = state.flying;
        player.sendAbilitiesUpdate();

        if (player.getServerWorld().getRegistryKey().equals(state.dimension))
        {
            if (player.networkHandler != null && !player.isDisconnected())
            {
                player.networkHandler.requestTeleport(state.x, state.y, state.z, state.yaw, state.pitch);
            }
            else
            {
                player.refreshPositionAndAngles(state.x, state.y, state.z, state.yaw, state.pitch);
            }
        }
    }

    public static void tick(MinecraftServer server)
    {
        if (GUARDED.isEmpty() || server == null)
        {
            return;
        }

        for (Map.Entry<UUID, GuardState> entry : GUARDED.entrySet())
        {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());

            if (player == null)
            {
                continue;
            }

            GuardState state = entry.getValue();

            applyLock(player, state);
        }
    }

    private static void applyLock(ServerPlayerEntity player, GuardState state)
    {
        if (!player.isInvulnerable())
        {
            player.setInvulnerable(true);
        }

        if (player.interactionManager.getGameMode() != GameMode.SPECTATOR)
        {
            player.changeGameMode(GameMode.SPECTATOR);
        }

        boolean abilitiesChanged = !player.getAbilities().allowFlying || !player.getAbilities().flying;

        player.getAbilities().allowFlying = true;
        player.getAbilities().flying = true;

        if (abilitiesChanged)
        {
            player.sendAbilitiesUpdate();
        }

        player.fallDistance = 0F;
        player.setVelocity(Vec3d.ZERO);
        player.velocityModified = true;

        if (player.getServerWorld().getRegistryKey().equals(state.dimension)
            && player.squaredDistanceTo(state.x, state.y, state.z) > 1.0e-5D
            && player.networkHandler != null
            && !player.isDisconnected())
        {
            player.networkHandler.requestTeleport(state.x, state.y, state.z, state.yaw, state.pitch);
        }
    }

    public static boolean isGuarded(ServerPlayerEntity player)
    {
        return player != null && GUARDED.containsKey(player.getUuid());
    }

    public static void reset()
    {
        GUARDED.clear();
    }

    private static final class GuardState
    {
        int count = 1;
        final RegistryKey<World> dimension;
        final double x;
        final double y;
        final double z;
        final float yaw;
        final float pitch;
        final GameMode gameMode;
        final boolean invulnerable;
        final boolean allowFlying;
        final boolean flying;

        GuardState(RegistryKey<World> dimension, double x, double y, double z, float yaw, float pitch, GameMode gameMode, boolean invulnerable, boolean allowFlying, boolean flying)
        {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.gameMode = gameMode;
            this.invulnerable = invulnerable;
            this.allowFlying = allowFlying;
            this.flying = flying;
        }
    }
}
