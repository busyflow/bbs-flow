package mchorse.bbs_mod.ui.onboarding;

import mchorse.bbs_mod.l10n.keys.IKey;

import java.util.List;

/**
 * Legacy stub kept for backward compatibility with addons (e.g. refreshedui).
 */
public record TourChapter(String id, int priority, List<Step> steps)
{
    public record Step(String anchor, IKey title, IKey text)
    {}
}
