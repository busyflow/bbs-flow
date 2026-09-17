package mchorse.bbs_mod.actions.types.crowd;

import mchorse.bbs_mod.network.ClientNetwork;

/**
 * Client half of the crowd export warm-up handshake.
 *
 * <p>The ready marker is sent after the server has spawned and announced every initial member.
 * Network packets on the play connection are ordered, so processing it also means the client has
 * processed all entity spawn packets it is meant to track. One more uncaptured render is required
 * after that, giving the forms, models and textures a complete preview/world render before the
 * recorder can capture its first frame.</p>
 */
public final class CrowdExportPreload
{
    private static String filmId;
    private static boolean waiting;
    private static boolean serverFinished;
    private static int settleRenders;
    private static long waitStartedAt;
    private static final long TIMEOUT_MS = 5000L;

    private CrowdExportPreload()
    {}

    public static void begin(String id)
    {
        filmId = id;
        waiting = id != null && ClientNetwork.isIsBBSModOnServer();
        serverFinished = !waiting;
        settleRenders = 0;
        waitStartedAt = System.currentTimeMillis();

        if (waiting)
        {
            /* Readiness must describe only this take, not ids retained from an earlier film. */
            CrowdClientMembers.clear();
        }
    }

    public static void serverFinished(String id)
    {
        if (waiting && filmId != null && filmId.equals(id))
        {
            serverFinished = true;
            settleRenders = 1;
        }
    }

    public static boolean isReady(String id)
    {
        if (!waiting || filmId == null || !filmId.equals(id))
        {
            return true;
        }

        if (System.currentTimeMillis() - waitStartedAt > TIMEOUT_MS)
        {
            /* Timed out waiting for server preload packet — unblock so export never hangs indefinitely */
            waiting = false;
            return true;
        }

        if (!serverFinished)
        {
            return false;
        }

        if (settleRenders > 0)
        {
            settleRenders -= 1;

            return false;
        }

        return true;
    }

    public static void finish(String id)
    {
        if (filmId == null || filmId.equals(id))
        {
            filmId = null;
            waiting = false;
            serverFinished = false;
            settleRenders = 0;
            waitStartedAt = 0L;
        }
    }
}
