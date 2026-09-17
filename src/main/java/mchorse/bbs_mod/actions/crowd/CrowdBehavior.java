package mchorse.bbs_mod.actions.crowd;

import mchorse.bbs_mod.actions.types.crowd.CrowdBehaviorMode;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import net.minecraft.util.math.MathHelper;

/**
 * One keyframed slice of "what is the crowd doing right now": a mode plus how fast and how jumpy.
 *
 * <p>A stepped channel - the value holds from its keyframe until the next one, and a mode change is
 * a hard cut rather than a blend, because there is no halfway between standing still and running.
 * The members are moved live by {@link mchorse.bbs_mod.actions.types.crowd.CrowdBehaviorActionClip}
 * from wherever they already are, so switching mode continues from the current positions instead of
 * snapping anyone back to spawn.</p>
 */
public class CrowdBehavior
{
    /** The behaviours offered on the channel, in menu order. Each maps onto a full behaviour mode. */
    public enum Kind
    {
        STAND_STILL("Stand still", CrowdBehaviorMode.HOLD, false, 0F),
        WANDER("Wander", CrowdBehaviorMode.WANDER, false, 4.3F),
        RUN_AROUND("Run around", CrowdBehaviorMode.WANDER, true, 5.6F),
        FREAK_OUT("Freak out", CrowdBehaviorMode.PANIC, true, 6.5F),
        WALK("Walk (Motion Path)", CrowdBehaviorMode.HOLD, false, 4.3F);

        public final String title;
        /** The heavy behaviour this drives; the clip already implements all of them per tick. */
        public final CrowdBehaviorMode mode;
        /** Whether members sprint (also drives the sprint animation and particles). */
        public final boolean sprint;
        /** Default blocks-per-second when the keyframe leaves speed at 0 (auto). */
        public final float defaultSpeed;

        Kind(String title, CrowdBehaviorMode mode, boolean sprint, float defaultSpeed)
        {
            this.title = title;
            this.mode = mode;
            this.sprint = sprint;
            this.defaultSpeed = defaultSpeed;
        }

        public static Kind get(int index)
        {
            Kind[] values = values();

            return index < 0 || index >= values.length ? STAND_STILL : values[index];
        }
    }

    /** Which behaviour, as a {@link Kind} ordinal. */
    public int kind;

    /** Blocks per second; 0 means use the kind's default. */
    public float speed;

    /** Jumps per member per second while moving; 0 is never. Freak out forces its own if left at 0. */
    public float jumpRate;

    public CrowdBehavior()
    {}

    public CrowdBehavior(int kind, float speed, float jumpRate)
    {
        this.kind = kind;
        this.speed = speed;
        this.jumpRate = jumpRate;
    }

    public Kind getKind()
    {
        return Kind.get(this.kind);
    }

    /** The speed to actually drive at: the keyframe's own, or the kind's default when left at 0. */
    public float effectiveSpeed()
    {
        return this.speed > 0F ? this.speed : this.getKind().defaultSpeed;
    }

    public CrowdBehavior copy()
    {
        return new CrowdBehavior(this.kind, this.speed, this.jumpRate);
    }

    public MapType toData()
    {
        MapType data = new MapType();

        data.putInt("kind", this.kind);
        data.putFloat("speed", this.speed);
        data.putFloat("jump_rate", this.jumpRate);

        return data;
    }

    public static CrowdBehavior fromData(BaseType data)
    {
        CrowdBehavior value = new CrowdBehavior();

        if (data == null || !data.isMap())
        {
            return value;
        }

        MapType map = data.asMap();

        value.kind = MathHelper.clamp(map.getInt("kind"), 0, Kind.values().length - 1);
        value.speed = Math.max(0F, map.getFloat("speed"));
        value.jumpRate = Math.max(0F, map.getFloat("jump_rate"));

        return value;
    }
}
