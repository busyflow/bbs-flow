package mchorse.bbs_mod.cubic.rig;

import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.utils.RenderFrame;

/**
 * Which form's rig handles show without the debug overlay: the IK goals, poles and controllers of
 * the form open on the rig tab, for as long as the tab is drawn (its splines' points are the screen
 * overlay's, see SplineOverlay). The tab says so every frame it draws, so a closed editor needs
 * nothing to take it back.
 */
public final class RigHandles
{
    private static ModelForm form;
    private static long frame = Long.MIN_VALUE;

    private RigHandles()
    {}

    /** The rig tab is drawn for this form in this frame. */
    public static void show(ModelForm form)
    {
        RigHandles.form = form;
        RigHandles.frame = RenderFrame.getFrame();
    }

    /** The rig tab stopped showing this form. */
    public static void hide(ModelForm form)
    {
        if (RigHandles.form == form)
        {
            RigHandles.form = null;
        }
    }

    /**
     * Whether the form's handles show: the tab drew it in this frame or the one before (the world
     * renders before the interface does).
     */
    public static boolean shown(ModelForm form)
    {
        return form != null && form == RigHandles.form && RenderFrame.getFrame() - frame <= 1;
    }
}
