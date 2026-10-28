package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.ui.framework.elements.utils.UITimelineCanvas;

/**
 * An editor or panel capable of supplying and synchronizing its active horizontal timeline viewport.
 */
public interface ITimelineProvider
{
    UITimelineCanvas getActiveTimeline();

    default void applyTimelineView(double min, double max)
    {
        if (Double.isNaN(min) || Double.isNaN(max) || min >= max)
        {
            return;
        }

        UITimelineCanvas timeline = this.getActiveTimeline();

        if (timeline != null && timeline.getXAxis() != null)
        {
            timeline.getXAxis().stopZoom();
            timeline.getXAxis().view(min, max);
        }
    }
}
