package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;

/**
 * Puts one of the film's crowds on a replay's timeline so it can be keyframed.
 *
 * <p>The form does not hold the crowd. The film does - who they are, how many, and where they
 * stand are settings of the crowd itself, kept in step with the world by the reconciler, which
 * is what stops a crowd from silently failing to appear. This only names which crowd a replay
 * drives.</p>
 *
 * <p>What it buys is the reason it exists: a replay carrying this form is given the crowd
 * keyframe channels - where they walk, what they look at, when they jump, and what they wear -
 * so those are authored on the timeline with the same curves and the same editor as everything
 * else, instead of a second keyframe system built for crowds alone.</p>
 *
 * <p>Deliberately thin. An earlier version of this form carried every spawn and behaviour
 * setting itself, which made it the only place a crowd could be described and capped a crowd at
 * two thousand members. Existence stays with the film and animation stays here; collapsing the
 * two back together is what put crowds on clips and lost them.</p>
 */
public class CrowdForm extends Form
{
    /**
     * The tag of the film's crowd this replay drives. Empty drives nothing.
     *
     * <p>Stored as the tag rather than an index so that reordering the film's crowds does not
     * silently repoint a replay at a different crowd. Nothing picks it: a replay given a crowd
     * form is given its own crowd, and keeps that one.</p>
     */
    public final ValueString crowd = new ValueString("crowd", "");

    /**
     * Whether the crowd's own behaviour clips still apply where a channel has no keyframes.
     *
     * <p>On, the keyframes are an override: a stretch of timeline with no walk keyframes leaves
     * the behaviour clip steering. Off, the keyframes are the whole story and the crowd stands
     * still wherever they say nothing.</p>
     */
    public final ValueBoolean keepBehavior = new ValueBoolean("keep_behavior", true);

    public CrowdForm()
    {
        super();

        this.add(this.crowd);
        this.add(this.keepBehavior);
    }

    @Override
    protected String getDefaultDisplayName()
    {
        String tag = this.crowd.get();

        return tag == null || tag.isEmpty() ? "Crowd" : tag;
    }
}
