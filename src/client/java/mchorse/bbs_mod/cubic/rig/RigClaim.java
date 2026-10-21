package mchorse.bbs_mod.cubic.rig;

import java.util.List;

/**
 * One step's hand on one bone, as the step itself reports it (see {@link RigOwners}).
 *
 * @param bone the bone it is on
 * @param owner the setting the claim comes from: the IK chain's tip, the physics chain's root, the
 *     spline's id, the shaking or limited bone itself
 * @param chain the setting's bones, root to tip (just the bone for the shake and the limits)
 * @param on the setting is switched on; switched off it still sits on the bone and does nothing
 * @param live it acts on the pose right now: switched on, with a weight, and solving
 */
public record RigClaim(RigStep step, String bone, String owner, Part part, Hold hold, List<String> chain, boolean on, boolean live)
{
    /** What the bone is to the setting. */
    public enum Part
    {
        /** The bone the setting lives on: an IK or spline chain's tip, a physics chain's root, a shaking or limited bone. */
        HOME,
        /** Another bone of the chain. */
        LINK,
        /** What the chain reaches for, or what a physics chain's end is pinned to. */
        TARGET,
        /** What an IK chain bends toward. */
        POLE
    }

    /** How the step treats the bone. */
    public enum Hold
    {
        /** Sets its rotation outright (IK, spline): an FK rotation has no say there. */
        DRIVES,
        /** Moves it, but the pose's rotation stays the base: physics swings it, an IK tip without tip rotation rides its chain. */
        FOLLOWS,
        /** Adds to the pose (shake). */
        ADDS,
        /** Clamps its rotation (limits). */
        CLAMPS,
        /** Only reads it: a target, a pole. */
        READS
    }

    /** A bone of the chain itself, not one the chain only looks at. */
    public boolean inChain()
    {
        return this.part == Part.HOME || this.part == Part.LINK;
    }
}
