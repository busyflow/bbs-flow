package mchorse.bbs_mod.film.crowds;

import mchorse.bbs_mod.data.types.MapType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Carrying a crowd between films.
 *
 * <p>A crowd form on a replay names its crowd by tag alone; the count, the spacing, the armour and
 * the rest live on the film's crowd. So copying such a replay into another film has to bring the
 * crowd with it, and the awkward part is that tags are numbered per film - the first crowd of every
 * film is called {@code crowd_1}, so two unrelated crowds routinely share a name.</p>
 *
 * <p>This covers the arithmetic of that: what {@code Crowds} does with an imported crowd. The paste
 * itself lives in the replay list and needs a UI, but it is a thin wrapper over these.</p>
 */
public class CrowdPasteTest
{
    private static Crowds crowds()
    {
        return new Crowds("crowds");
    }

    private static Crowd populated(Crowds into, int count, double spacing)
    {
        Crowd crowd = into.addCrowd();

        crowd.count.set(count);
        crowd.spacing.set((float) spacing);

        return crowd;
    }

    @Test
    public void theFirstCrowdOfEveryFilmSharesAName()
    {
        /* The premise of the whole problem, worth pinning: two films, same tag, different crowds. */
        assertEquals("crowd_1", crowds().addCrowd().crowdTag.get());
        assertEquals("crowd_1", crowds().addCrowd().crowdTag.get());
    }

    @Test
    public void animportedCrowdKeepsItsSettings()
    {
        Crowds source = crowds();
        Crowd original = populated(source, 137, 2.5D);

        MapType copied = (MapType) original.toData();

        Crowds target = crowds();
        Crowd landed = target.addCopy(copied);

        assertEquals(137, landed.count.get(), "the count did not survive the paste");
        assertEquals(2.5F, landed.spacing.get(), 0.0001F, "the spacing did not survive the paste");
        assertEquals("crowd_1", landed.crowdTag.get(), "the tag should come across untouched");
    }

    @Test
    public void aFreeTagIsFoundWhenTheNameIsTaken()
    {
        Crowds target = crowds();

        populated(target, 10, 1D);

        /* crowd_1 is taken, so an incoming crowd_1 has to become something else rather than be
         * dropped - dropping it is what left a pasted replay wearing the wrong crowd's settings. */
        assertEquals("crowd_2", target.freeTag());
    }

    @Test
    public void twoDifferentCrowdsCanCoexistAfterAPaste()
    {
        Crowds source = crowds();
        MapType incoming = (MapType) populated(source, 500, 4D).toData();

        Crowds target = crowds();
        Crowd mine = populated(target, 12, 1D);

        /* What the paste does when the names collide and the crowds differ. */
        String free = target.freeTag();
        MapType renamed = (MapType) incoming.copy();

        renamed.putString("crowd_tag", free);

        Crowd landed = target.addCopy(renamed);

        assertEquals(2, target.getList().size(), "the imported crowd should not have replaced mine");
        assertEquals(12, mine.count.get(), "the film's own crowd was overwritten");
        assertEquals(500, landed.count.get(), "the imported crowd lost its settings");
        assertNotEquals(mine.crowdTag.get(), landed.crowdTag.get(), "both crowds answer to the same tag");

        assertNotNull(target.byTag(mine.crowdTag.get()));
        assertNotNull(target.byTag(landed.crowdTag.get()));
    }

    @Test
    public void anIdenticalCrowdIsRecognisedRatherThanDuplicated()
    {
        Crowds film = crowds();
        Crowd crowd = populated(film, 40, 3D);

        /* The same-film paste: the crowd on the clipboard IS the one already here, so the paste
         * shares it. Equality of the written form is how that is told apart from a collision. */
        assertTrue(crowd.toData().equals(crowd.toData()));

        Crowds other = crowds();
        Crowd different = populated(other, 41, 3D);

        assertTrue(!crowd.toData().equals(different.toData()),
            "two crowds differing by count must not look identical, or a paste would share the wrong one");
    }
}
