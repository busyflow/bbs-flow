package mchorse.bbs_mod.actions.crowd;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.List;

/**
 * Decides which crowd members jump on a given tick.
 *
 * <p>Only the decision. The jump itself is a real one - the member is given upward velocity and
 * gravity brings it back - which is how the crowd behaviour clip has always done it. Placing
 * members along an authored arc instead meant owning their vertical position outright, and
 * getting that even slightly wrong put them under the floor.</p>
 */
public final class CrowdJumpEvaluator
{
    /* Salts, so a member's choice and its per-tick roll come from the same generator without
     * being the same number. */
    private static final int CHOICE_SALT = 0x5A91;
    private static final int ROLL_SALT = 0x9E37;
    private static final int POWER_SALT = 0x7C15;

    /* A jump added on top of a route, rather than handed to the physics. */
    private static final double WALKING_HEIGHT = 0.9D;
    private static final double WALKING_DURATION = 12D;
    private static final double WALKING_MAX_GAP = WALKING_DURATION * 12D;

    private CrowdJumpEvaluator()
    {}

    public static Frame frame(Replay replay, float filmTick)
    {
        if (replay == null || replay.keyframes.crowdJump.isEmpty())
        {
            return null;
        }

        float tick = localTick(replay, filmTick);
        CrowdJump jump = replay.keyframes.crowdJump.interpolate(tick);

        if (jump == null || jump.amount <= 0F)
        {
            return null;
        }

        return new Frame(replay.getId().hashCode(), tick, anchor(replay, tick),
            jump.amount, jump.rate, jump.random);
    }

    /**
     * The tick of the keyframe in effect, which a single jump is counted from.
     *
     * <p>Only matters at rate 0, where a member jumps once: the jump belongs to the keyframe that
     * asked for it, so dropping a keyframe on the timeline is one jump there.</p>
     */
    private static float anchor(Replay replay, float tick)
    {
        List<Keyframe<CrowdJump>> list = replay.keyframes.crowdJump.getKeyframes();
        float anchor = list.get(0).getTick();

        for (Keyframe<CrowdJump> keyframe : list)
        {
            if (keyframe.getTick() > tick)
            {
                break;
            }

            anchor = keyframe.getTick();
        }

        return anchor;
    }

    private static float localTick(Replay replay, float filmTick)
    {
        int loop = replay.looping.get();

        if (loop <= 0)
        {
            return filmTick;
        }

        float wrapped = filmTick % loop;

        return wrapped < 0F ? wrapped + loop : wrapped;
    }

    public record Frame(int replayHash, float tick, float anchor, float amount, float rate,
                        boolean random)
    {
        /**
         * How hard this member pushes off, as a multiple of a normal jump.
         *
         * <p>Applied to the velocity rather than to a height, so the member still lands on
         * whatever the floor turns out to be.</p>
         */
        public double power(int memberIndex)
        {
            return this.random
                ? 0.85D + randomFor(this.replayHash, memberIndex, POWER_SALT) * 0.3D
                : 1D;
        }

        /**
         * How far off the ground this member is, for a crowd whose position a route already owns.
         *
         * <p>A real jump cannot survive a route that writes the position every tick, so where
         * both are keyframed the jump is a height added to the route's own ground rather than a
         * push. Worked out from the tick alone: nothing is remembered and nothing is read back
         * off the member, so it cannot accumulate however long the shot runs.</p>
         */
        public double walkingHeight(int memberIndex)
        {
            if (randomFor(this.replayHash, memberIndex, CHOICE_SALT) >= this.amount)
            {
                return 0D;
            }

            double duration = WALKING_DURATION * (this.random
                ? 0.8D + randomFor(this.replayHash, memberIndex, POWER_SALT) * 0.5D
                : 1D);
            double scale = this.random ? 0.7D + randomFor(this.replayHash, memberIndex, POWER_SALT) * 0.6D : 1D;
            double phase = randomFor(this.replayHash, memberIndex, ROLL_SALT) * duration;
            double since = this.tick - this.anchor + phase;
            double elapsed;

            if (this.rate <= 0F)
            {
                elapsed = since - phase * 2D;
            }
            else
            {
                double gap = WALKING_MAX_GAP * (1D - this.rate) / this.rate;

                elapsed = Math.floorMod((long) Math.floor(since), (long) Math.max(1D, duration + gap));
            }

            if (elapsed < 0D || elapsed >= duration)
            {
                return 0D;
            }

            double wave = Math.sin(Math.PI * (elapsed / duration));

            return WALKING_HEIGHT * scale * wave * wave;
        }

        /** Whether this member should leave the ground on this tick. */
        public boolean jumps(int memberIndex)
        {
            /* Who jumps is a fixed draw against the share wanted, not a roll per tick. The same
             * members jump throughout, and raising the share adds to them rather than picking a
             * different crowd. */
            if (randomFor(this.replayHash, memberIndex, CHOICE_SALT) >= this.amount)
            {
                return false;
            }

            if (this.rate <= 0F)
            {
                /* One jump each, spread over a few ticks so the crowd does not leave the ground
                 * in perfect unison. */
                double spread = 1D + randomFor(this.replayHash, memberIndex, ROLL_SALT) * 5D;

                return (int) this.tick == (int) (this.anchor + spread);
            }

            /* Otherwise a chance each tick they are stood on the ground. Cubed so the low end is
             * an occasional hop rather than a near-constant one, while 1 stays exactly 1 - back
             * up the tick after landing. */
            double chance = this.rate * this.rate * this.rate;

            return chance >= 1D || randomFor(this.replayHash, memberIndex, (int) this.tick) < chance;
        }
    }

    private static double randomFor(int replayHash, int memberIndex, int salt)
    {
        long value = replayHash * 0x9E3779B97F4A7C15L;

        value ^= (long) memberIndex * 0xBF58476D1CE4E5B9L;
        value ^= (long) salt * 0x94D049BB133111EBL;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;

        return (value >>> 11) * 0x1.0p-53;
    }
}
