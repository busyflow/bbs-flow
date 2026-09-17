package mchorse.bbs_mod.ui.film.crowds;

import mchorse.bbs_mod.film.crowds.Crowd;

/**
 * The crowd currently being edited, for the viewport overlays to draw.
 *
 * <p>Its own holder because the world rendering that wants it runs a long way from the panel
 * that sets it, and only one crowd is ever being edited at a time.</p>
 */
public class CrowdSelection
{
    private static Crowd selected;

    private CrowdSelection()
    {}

    public static Crowd get()
    {
        return selected;
    }

    public static void set(Crowd crowd)
    {
        selected = crowd;
    }
}
