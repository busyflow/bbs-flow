package mchorse.bbs_mod.film.crowds;

import mchorse.bbs_mod.actions.types.crowd.CrowdUtils;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Makes the world agree with what the film says about its crowds.
 *
 * <p>Every tick of a playback the question asked of each crowd is "should this be standing here
 * right now?". If it should and is not, it is spawned; if it should not and is, it is removed.
 * Nothing has to fire at the right moment and nothing can be missed, which is the whole point of
 * moving crowds off clips - a spawn that did not happen used to leave an empty shot and no
 * error, and the only symptom was looking at the scene and seeing nobody.</p>
 *
 * <p>The cost of asking has to stay near zero, because it is asked twenty times a second against
 * crowds of five figures. So the answer is remembered rather than measured: this holds what it
 * spawned and the {@link Crowd#signature()} it spawned it from, and does nothing at all unless
 * one of those disagrees with the film. Counting live members to check would mean walking every
 * entity in the world each tick, which costs far more than the bug it would catch.</p>
 */
public class CrowdReconciler
{
    /** Crowd id to the signature its members were spawned from. */
    private final Map<String, Integer> spawned = new HashMap<>();

    /**
     * Bring the world in line with the film at this tick.
     *
     * @param tick the film tick being played
     */
    public void reconcile(ServerWorld world, Film film, int tick)
    {
        if (world == null || film == null)
        {
            return;
        }

        Set<String> seen = new HashSet<>();

        for (Crowd crowd : film.crowds.getList())
        {
            String id = crowd.getId();

            seen.add(id);

            boolean shouldExist = crowd.existsAt(tick) && this.driven(film, crowd);
            Integer live = this.spawned.get(id);

            if (!shouldExist)
            {
                if (live != null)
                {
                    this.despawn(world, film, crowd, id);
                }

                continue;
            }

            int signature = this.hasPaintKeyframes(film, crowd) ? crowd.signatureWithoutCells() : crowd.signature();

            if (live != null && live == signature)
            {
                continue;
            }

            /* Edited while standing there - the members are in the wrong places or are the wrong
             * thing entirely, so they are replaced rather than adjusted. Placement is
             * deterministic from the settings, so there is nothing an adjustment could preserve
             * that a respawn does not reproduce. */
            if (live != null)
            {
                this.despawn(world, film, crowd, id);
            }

            CrowdSpawner.spawn(world, film, crowd, this.center(film, crowd, tick), tick);
            this.spawned.put(id, signature);
        }

        /* A crowd deleted from the film mid-playback leaves members behind that nothing owns
         * any more, and nothing else will ever remove them - the film no longer mentions them. */
        if (this.spawned.size() != seen.size())
        {
            this.spawned.keySet().removeIf((id) ->
            {
                if (seen.contains(id))
                {
                    return false;
                }

                CrowdUtils.removeCrowd(world, film, id);

                return true;
            });
        }
    }

    /**
     * Whether a crowd should stand, given the replays that drive it.
     *
     * <p>A crowd form on a replay is how a crowd is shown and animated; disabling that replay is
     * the user asking for the crowd to be gone, not just unanimated - a hundred thousand members
     * still rendered behind a disabled form is exactly the frame rate they turned it off to get
     * back. So a driven crowd stands only while at least one of its forms is enabled.</p>
     *
     * <p>A crowd no form points at does not stand either. It used to fall back to its own
     * {@code enabled} flag, on the grounds that a crowd never shown through a form has no
     * form-disable to honour - but the only crowds that are ever in that state are the ones
     * {@link CrowdMigration} converts out of old spawn clips, and those have no replay row, so
     * nothing in the editor can see them, select them or switch them off. The result was a film
     * with every replay disabled and a crowd still standing in the shot with no way to reach it.
     * Requiring a form means what is on the timeline is the whole truth about what spawns.</p>
     *
     * <p>Nothing is destroyed by this: the crowd keeps its settings, and pointing any crowd form
     * at it brings it back.</p>
     */
    private boolean driven(Film film, Crowd crowd)
    {
        String tag = crowd.crowdTag.get();

        for (Replay replay : film.replays.getList())
        {
            if (replay.form.get() instanceof mchorse.bbs_mod.forms.forms.CrowdForm form
                && form.crowd.get().equals(tag)
                && replay.enabled.get())
            {
                return true;
            }
        }

        return false;
    }

    private boolean hasPaintKeyframes(Film film, Crowd crowd)
    {
        if (film == null || crowd == null)
        {
            return false;
        }

        String tag = crowd.crowdTag.get();

        for (Replay replay : film.replays.getList())
        {
            if (replay.form.get() instanceof mchorse.bbs_mod.forms.forms.CrowdForm form
                && form.crowd.get().equals(tag)
                && replay.enabled.get()
                && !replay.keyframes.crowdPaint.isEmpty())
            {
                return true;
            }
        }

        return false;
    }

    private void despawn(ServerWorld world, Film film, Crowd crowd, String id)
    {
        CrowdUtils.removeCrowd(world, film, CrowdUtils.crowdTag(crowd.crowdTag.get()));
        this.spawned.remove(id);
    }

    /**
     * Where the crowd is centred, taken from its anchor replay at the tick the crowd begins.
     *
     * <p>At the crowd's start rather than now, so that scrubbing to the middle of a crowd's
     * window puts it where it was placed rather than around wherever the anchor has walked to
     * since. Painted crowds ignore this entirely - their points are absolute world space.</p>
     */
    private Vec3d center(Film film, Crowd crowd, int tick)
    {
        Replay replay = CrowdUtils.getReplay(film, crowd.anchor.get());

        if (replay == null)
        {
            return Vec3d.ZERO;
        }

        return CrowdUtils.replayPosition(replay, crowd.start.get());
    }

    /** Playback is over; the film's crowds should no longer be standing anywhere. */
    public void clear(ServerWorld world, Film film)
    {
        if (world != null && film != null)
        {
            for (Crowd crowd : film.crowds.getList())
            {
                CrowdUtils.removeCrowd(world, film, CrowdUtils.crowdTag(crowd.crowdTag.get()));
            }
        }

        this.spawned.clear();
    }

    /**
     * Forget what is standing there without removing it.
     *
     * <p>For when the members are known to be gone already - the whole film's crowds discarded
     * at once, say - so the next reconcile spawns rather than believing they are still up.</p>
     */
    public void forget()
    {
        this.spawned.clear();
    }
}
