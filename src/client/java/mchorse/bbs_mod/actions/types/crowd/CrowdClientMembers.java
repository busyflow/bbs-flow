package mchorse.bbs_mod.actions.types.crowd;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

/**
 * Which entities on this client are crowd members.
 *
 * <p>The server knows by a command tag, but command tags are never sent anywhere, and the client
 * is where the crowd is drawn - and where vanilla works out a body's facing for itself, from
 * movement, rather than from anything the server said. So the ids come over once when a crowd is
 * spawned, which is the only moment the answer changes, and cost nothing per tick thereafter.</p>
 */
public final class CrowdClientMembers
{
    private static final IntOpenHashSet MEMBERS = new IntOpenHashSet();

    private CrowdClientMembers()
    {
    }

    /**
     * Add a clip's crowd. Added rather than replaced, because a film may run several crowds and
     * each announces only its own; the set is emptied when the film stops, so nothing outlives
     * the shot it belonged to.
     */
    public static void add(int[] ids)
    {
        for (int id : ids)
        {
            MEMBERS.add(id);
        }
    }

    public static void clear()
    {
        MEMBERS.clear();
    }

    public static boolean isMember(int id)
    {
        return !MEMBERS.isEmpty() && MEMBERS.contains(id);
    }
}
