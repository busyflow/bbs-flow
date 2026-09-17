package mchorse.bbs_mod.film.crowds;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.resources.Link;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The PNGs in a folder, remembered between ticks.
 *
 * <p>A texture keyframe is read every tick of playback, and listing a folder means going to the
 * asset provider each time. That is a directory walk per tick for an answer that only changes
 * when the folder does, so it is done once and kept.</p>
 */
public class CrowdTextures
{
    private static final Map<String, List<Link>> CACHE = new HashMap<>();

    private CrowdTextures()
    {}

    public static List<Link> list(Link folder, boolean recursive)
    {
        if (folder == null || folder.source.isEmpty())
        {
            return List.of();
        }

        return CACHE.computeIfAbsent(folder + "|" + recursive, (key) -> collect(folder, recursive));
    }

    /** Drop the cache so a folder edited mid-session is picked up. */
    public static void clear()
    {
        CACHE.clear();
    }

    private static List<Link> collect(Link folder, boolean recursive)
    {
        List<Link> textures = new ArrayList<>();

        try
        {
            for (Link link : BBSMod.getProvider().getLinksFromPath(folder, recursive))
            {
                if (!link.path.endsWith("/") && link.path.endsWith(".png"))
                {
                    textures.add(link);
                }
            }
        }
        catch (Exception e)
        {}

        /* Sorted so a member's texture depends on its number and the folder's contents, not on
         * whatever order the filesystem happened to hand back - otherwise the same film dresses
         * the same crowd differently on another machine. */
        textures.sort((a, b) -> a.toString().compareTo(b.toString()));

        return textures;
    }
}
