package mchorse.bbs_mod.film.crowds;

import mchorse.bbs_mod.actions.types.crowd.CrowdSpawnActionClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.utils.clips.Clip;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the crowd spawn clips of an older film into crowds the film owns.
 *
 * <p>Run when a film is loaded, so opening an old one is the whole of the upgrade - there is
 * nothing for anyone to do by hand and no film that works differently from the rest afterwards.
 * The clip class stays registered so those films still deserialize; this is what empties it.</p>
 *
 * <p>Every member's identity is derived from the crowd's tag and seed, both of which are carried
 * over unchanged, so a converted crowd stands exactly where it did before.</p>
 */
public class CrowdMigration
{
    private CrowdMigration()
    {}

    /**
     * @return whether anything was converted, so the caller can save the film back.
     */
    public static boolean migrate(Film film)
    {
        if (film == null || !film.crowds.getList().isEmpty())
        {
            return false;
        }

        List<Replay> replays = film.replays.getList();
        boolean migrated = false;

        for (int i = 0; i < replays.size(); i++)
        {
            Replay replay = replays.get(i);
            List<Clip> found = new ArrayList<>();

            for (Clip clip : replay.actions.get())
            {
                if (clip instanceof CrowdSpawnActionClip)
                {
                    found.add(clip);
                }
            }

            for (Clip clip : found)
            {
                convert(film, (CrowdSpawnActionClip) clip, i);

                /* The clip has been turned into a crowd; leaving it on the timeline would spawn
                 * the same crowd a second time, from the same tags, and each would be removing
                 * the other's members. */
                replay.actions.remove(clip);
                migrated = true;
            }
        }

        return migrated;
    }

    private static void convert(Film film, CrowdSpawnActionClip clip, int replayIndex)
    {
        Crowd crowd = film.crowds.addCrowd();

        /* Field ids were kept identical when the clip became a crowd, so the settings copy
         * across whole. Keys the crowd does not have - the clip's own layer and title - are
         * simply not read. */
        crowd.fromData(clip.toData());

        crowd.name.set(clip.title.get());
        crowd.enabled.set(clip.enabled.get());

        /* The clip was an instant: it fired at its tick and the crowd stood there until the
         * playback ended. A crowd is a stretch now, running from the moment it appeared to the
         * end of the film, which is the same thing said as a property rather than an event. */
        crowd.start.set(clip.tick.get());

        /* The old centre came from whichever replay's timeline the clip happened to sit on. */
        crowd.anchor.set(replayIndex);
    }
}
