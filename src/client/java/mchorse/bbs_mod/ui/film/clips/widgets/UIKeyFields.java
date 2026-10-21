package mchorse.bbs_mod.ui.film.clips.widgets;

import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;

/**
 * A spot in a clip's panel for the picked key's fields while the clip's keys are open under the
 * clips: as tall as the fields, and not there at all without them.
 */
public class UIKeyFields extends UIElement
{
    private int height;

    public UIKeyFields()
    {
        this.setVisible(false);
    }

    /** Follows the fields put in it; true when its height changed and the panel has to lay out again. */
    public boolean fit()
    {
        int height = 0;

        for (UIKeyframeFactory factory : this.getChildren(UIKeyframeFactory.class))
        {
            height = Math.max(height, factory.scroll.scroll.scrollSize);
        }

        if (height == this.height)
        {
            return false;
        }

        this.height = height;
        this.h(height);
        this.setVisible(height > 0);

        return true;
    }
}
