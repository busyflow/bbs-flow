package mchorse.bbs_mod.actions.types.crowd;

/**
 * Behaviours a crowd can be driven with.
 *
 * <p>Only {@link #FOLLOW} and {@link #HOLD} are offered in the editor - see
 * {@link #PRESETS}. The rest stay defined so films saved with them keep playing back the way
 * they were authored.</p>
 */
public enum CrowdBehaviorMode
{
    FOLLOW("Follow target"),
    DISPERSE("Disperse"),
    CHEER("Cheer"),
    SAD_WALK("Sad walk"),
    TALK("Talk"),
    HOLD("Stand and look"),
    WANDER("Wander"),
    WANDER_LOOK("Wander + look"),
    IDLE_CROWD("Idle crowd"),
    GATHER("Gather"),
    WATCH("Watch event"),
    FLEE("Flee"),
    MARKET("Marketplace"),
    MEETING("Meeting circle"),
    PANIC("Crowd panic"),
    GUARD_PATROL("Guard patrol"),
    WORKERS("Workers"),
    FIGHT("Crowd fight");

    /** The behaviours the editor lets you pick, in menu order. */
    public static final CrowdBehaviorMode[] PRESETS = {HOLD, FOLLOW};

    public final String title;

    CrowdBehaviorMode(String title)
    {
        this.title = title;
    }

    public static CrowdBehaviorMode get(int index)
    {
        CrowdBehaviorMode[] values = values();

        if (index < 0 || index >= values.length)
        {
            return FOLLOW;
        }

        return values[index];
    }
}
