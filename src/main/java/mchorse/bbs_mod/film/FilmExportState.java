package mchorse.bbs_mod.film;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is presently exporting a film to video.
 *
 * <p>Exporting happens on the client - it is frames off the screen - but some of what the server
 * does has to differ between a take and a shot being built, the crowd's population most of all.
 * The client says when it starts and stops, and this is where that is kept.</p>
 *
 * <p>Held per player and asked as "is anyone", because the crowd is spawned once for a playback
 * rather than once per viewer. A player who leaves mid-export is dropped, so a lost connection
 * cannot leave the server believing an export is still running.</p>
 */
public final class FilmExportState
{
    private static final Set<UUID> EXPORTING = ConcurrentHashMap.newKeySet();

    private FilmExportState()
    {
    }

    public static void set(UUID player, boolean exporting)
    {
        if (exporting)
        {
            EXPORTING.add(player);
        }
        else
        {
            EXPORTING.remove(player);
        }
    }

    public static void clear(UUID player)
    {
        EXPORTING.remove(player);
    }

    public static boolean isAnyExporting()
    {
        return !EXPORTING.isEmpty();
    }

    /** Whether this player is the one currently preparing or recording an export. */
    public static boolean isExporting(UUID player)
    {
        return player != null && EXPORTING.contains(player);
    }
}
