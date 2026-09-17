package mchorse.bbs_mod.actions.types;

import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.Map;

public class DamageActionClip extends ActionClip
{
    /**
     * Who is on stage this tick.
     *
     * <p>An action clip is handed its own actor and nothing else, so a clip that wants to
     * act on <em>another</em> replay's actor has no way to find it. The player rebuilds this
     * every applied tick. Crowd fighting reads it to resolve "fight this replay" into the
     * living entity currently performing that replay.</p>
     *
     * <p>It lives here rather than on the crowd clip to match upstream, so the two stay
     * diffable when crowd features are merged again.</p>
     */
    public static List<Replay> replayContext;
    public static Map<String, LivingEntity> actorContext;
    public static ServerPlayerEntity recordingPlayer;
    public static int recordingReplay = -1;

    public final ValueFloat damage = new ValueFloat("damage", 0F);

    public DamageActionClip()
    {
        super();

        this.add(this.damage);
    }

    public static void setPlaybackContext(List<Replay> replays, Map<String, LivingEntity> actors,
        ServerPlayerEntity player, int exception)
    {
        replayContext = replays;
        actorContext = actors;
        recordingPlayer = player;
        recordingReplay = exception;
    }

    @Override
    public void applyAction(LivingEntity actor, SuperFakePlayer player, Film film, Replay replay, int tick)
    {
        float damage = this.damage.get();

        if (damage <= 0F)
        {
            return;
        }

        this.applyPositionRotation(player, replay, tick);

        if (actor != null)
        {
            actor.damage(player.getWorld().getDamageSources().mobAttack(player), damage);
        }
    }

    @Override
    protected Clip create()
    {
        return new DamageActionClip();
    }
}