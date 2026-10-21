package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.utils.UIUtils;
import net.minecraft.client.MinecraftClient;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Keying a replay by hand at the cursor, as opposed to shooting a take. The insert key writes
 * where the actor stands and looks, the same whatever tab is open or whatever the timeline
 * shows, so pressing it never depends on state the animator can't see.
 *
 * <p>Split out of {@link UIFilmController} as a companion of its own: this is the manual half
 * of recording and answers to the editor's state, not to the take's.
 */
public class FilmKeyframeInsertion
{
    private static final List<String> TRANSFORM = Arrays.asList(ReplayKeyframes.GROUP_POSITION, ReplayKeyframes.GROUP_ROTATION);

    /** The stick pair each mouse mode past looking around drives, in mode order. */
    private static final String[] STICKS = {
        ReplayKeyframes.GROUP_LEFT_STICK,
        ReplayKeyframes.GROUP_RIGHT_STICK,
        ReplayKeyframes.GROUP_TRIGGERS,
        ReplayKeyframes.GROUP_EXTRA1,
        ReplayKeyframes.GROUP_EXTRA2
    };

    private final UIFilmController controller;

    public FilmKeyframeInsertion(UIFilmController controller)
    {
        this.controller = controller;
    }

    /** Key the actor's position and rotation at the cursor. */
    public void insertFrame()
    {
        this.record(TRANSFORM);
    }

    /**
     * Key what the mouse drives right now, for the automatic insertion while puppeteering:
     * looking around keys position and rotation, a stick mode keys its stick pair.
     */
    public void insertDrivenFrame()
    {
        int mode = this.controller.mouse.getMode();

        this.record(mode == 0 ? TRANSFORM : Collections.singletonList(STICKS[mode - 1]));
    }

    private void record(List<String> groups)
    {
        Replay replay = this.controller.getReplay();

        if (replay == null)
        {
            return;
        }

        BaseValue.edit(replay.keyframes, (keyframes) ->
        {
            keyframes.record(this.controller.getTick(), this.controller.getCurrentEntity(), groups);
        });
    }

    /**
     * Insert position and rotation keyframes at the current tick from the live
     * Minecraft player's world transform - a quick way to "teleport" the replay
     * to where the player is standing (and facing). Only the transform channels
     * are touched (unlike full player recording), so no stance/velocity noise is
     * added to the replay.
     */
    public void insertPlayerFrame()
    {
        Replay replay = this.controller.getReplay();

        if (replay == null || MinecraftClient.getInstance().player == null)
        {
            return;
        }

        Morph morph = Morph.getMorph(MinecraftClient.getInstance().player);

        if (morph == null)
        {
            return;
        }

        IEntity player = morph.entity;
        int tick = this.controller.getTick();

        BaseValue.edit(replay.keyframes, (keyframes) ->
        {
            keyframes.x.insert(tick, player.getX());
            keyframes.y.insert(tick, player.getY());
            keyframes.z.insert(tick, player.getZ());

            keyframes.yaw.insert(tick, (double) player.getYaw());
            keyframes.pitch.insert(tick, (double) player.getPitch());
            keyframes.headYaw.insert(tick, (double) player.getHeadYaw());
            keyframes.bodyYaw.insert(tick, (double) player.getBodyYaw());
        });

        UIUtils.playClick();
    }
}
