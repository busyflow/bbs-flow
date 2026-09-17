package mchorse.bbs_mod.actions.crowd;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import net.minecraft.util.math.MathHelper;

/**
 * How many of a crowd are jumping, and how often they do it.
 *
 * <p>Two separate questions, and they were one number before. {@link #amount} picks who: at 0.55,
 * the same 55% of members jump and the rest stand and watch. {@link #rate} decides what those
 * members do with the time - once and done at 0, straight back up on landing at 1.</p>
 */
public class CrowdJump
{
    /** Roughly how long a vanilla jump keeps a mob off the ground, for reading the old rate. */
    private static final int DURATION = 12;

    /**
     * The share of the crowd that jumps at all, as a fraction.
     *
     * <p>Who is chosen is fixed for a given crowd rather than re-rolled, so raising this adds
     * jumpers to the ones already going instead of swapping the crowd around.</p>
     *
     * <p>Whole crowd by default: a keyframe dropped on the timeline is asking for a jump, so it
     * jumps everyone rather than nobody. At 0 the channel says nothing and {@code frame} returns
     * null, which is indistinguishable from a fresh keyframe doing nothing at all.</p>
     */
    public float amount = 1F;

    /**
     * How often a jumper jumps. 0 is once. 1 is again the moment they land.
     *
     * <p>The gap between jumps, not the height of them - a member who jumps rarely jumps exactly
     * as high as one who never stops.</p>
     */
    public float rate = 0.5F;

    /**
     * Vary how high each member jumps.
     *
     * <p>Off, every jump is the vanilla one and they all reach the same height. On, a member's
     * push off the ground is scaled a little either way, so the crowd is not one shape repeating.
     * The jump is still real either way - the scale goes into the velocity, not into a height
     * anyone is placed at.</p>
     */
    public boolean random = true;

    public CrowdJump()
    {}

    public CrowdJump(float amount, float rate, boolean random)
    {
        this.amount = amount;
        this.rate = rate;
        this.random = random;
    }

    public CrowdJump copy()
    {
        return new CrowdJump(this.amount, this.rate, this.random);
    }

    public MapType toData()
    {
        MapType data = new MapType();

        data.putFloat("amount", this.amount);
        data.putFloat("rate", this.rate);
        data.putBool("random", this.random);

        return data;
    }

    public static CrowdJump fromData(BaseType data)
    {
        CrowdJump value = new CrowdJump();

        if (data == null)
        {
            return value;
        }

        /* Older films stored a bare rate in jumps per member per second. Read it back as the
         * share of the crowd that rate kept in the air, which is what it was really producing. */
        if (data.isNumeric())
        {
            value.amount = (float) dutyForLegacyRate(data.asNumeric().doubleValue());

            return value;
        }

        if (!data.isMap())
        {
            return value;
        }

        MapType map = data.asMap();

        /* "intensity" was the brief spelling of this between the legacy rate and the split into
         * who-jumps and how-often. It meant the same thing amount does. */
        float amount = map.has("amount") ? map.getFloat("amount") : map.getFloat("intensity");

        value.amount = MathHelper.clamp(amount, 0F, 1F);
        value.rate = MathHelper.clamp(map.getFloat("rate", 0.5F), 0F, 1F);
        value.random = !map.has("random") || map.getBool("random");

        return value;
    }

    /** The old rate's chance per tick, turned into the share of time a member spent airborne. */
    private static double dutyForLegacyRate(double rate)
    {
        double chance = rate <= 0D ? 0D : 1D - Math.exp(-Math.min(10D, rate) / 20D);

        return chance <= 0D ? 0D : chance * DURATION / (chance * DURATION + 1D);
    }
}
