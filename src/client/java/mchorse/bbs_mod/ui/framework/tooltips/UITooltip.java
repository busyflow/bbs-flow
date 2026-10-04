package mchorse.bbs_mod.ui.framework.tooltips;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.Area;

public class UITooltip
{
    private static final long HOVER_DELAY_NANOS = 500_000_000L;

    private UIElement hoveredElement;
    private long hoverStarted;

    public UIElement element;
    public Area area = new Area();

    public void set(UIContext context, UIElement element)
    {
        this.element = element;

        if (element != null)
        {
            this.area.copy(element.area);
            this.area.x = context.globalX(this.area.x);
            this.area.y = context.globalY(this.area.y);
        }
    }

    public void render(ITooltip tooltip, UIContext context)
    {
        if (this.element == null || tooltip == null)
        {
            return;
        }

        tooltip.renderTooltip(context);
    }

    public void render(UIContext context)
    {
        long now = System.nanoTime();

        /* Compare the final target after all elements have rendered: set() is also
         * used to clear and replace candidates while traversing each frame. */
        if (this.hoveredElement != this.element)
        {
            this.hoveredElement = this.element;
            this.hoverStarted = now;
        }

        if (this.element != null && (this.element.tooltipImmediate || now - this.hoverStarted >= HOVER_DELAY_NANOS))
        {
            this.element.renderTooltip(context, this.area);
        }
    }
}