package mchorse.bbs_mod.cubic.rig;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.rig.RigClaim.Hold;
import mchorse.bbs_mod.cubic.rig.RigClaim.Part;
import mchorse.bbs_mod.forms.forms.ModelForm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Who has a hand on which bone of a form: the claims of the rig's steps, each step reporting its own
 * (see {@link RigStep#claim}). Built when asked, from the form as it stands right now with the film's
 * live values in it, so the gizmo, the panels and the spline's admission read one answer instead of
 * each asking the steps it happens to know about.
 */
public final class RigOwners
{
    private final List<RigClaim> claims = new ArrayList<>();
    private final Map<String, List<RigClaim>> bones = new HashMap<>();

    /** Every step's claims on the form's bones. */
    public static RigOwners of(IModel model, ModelForm form)
    {
        return of(model, form, RigStep.ALL);
    }

    /** These steps' claims on the form's bones. */
    public static RigOwners of(IModel model, ModelForm form, Set<RigStep> steps)
    {
        RigOwners owners = new RigOwners();

        if (model != null && form != null)
        {
            for (RigStep step : steps)
            {
                step.claim(model, form, owners);
            }
        }

        return owners;
    }

    /** A step's report of its hand on one bone. */
    public void add(RigClaim claim)
    {
        if (claim == null || claim.bone() == null || claim.bone().isEmpty())
        {
            return;
        }

        this.claims.add(claim);
        this.bones.computeIfAbsent(claim.bone(), (k) -> new ArrayList<>()).add(claim);
    }

    /** A chain, held the same way along it: the bone it is named after is its home, the rest are links. */
    public void chain(RigStep step, String owner, String home, List<String> chain, Hold hold, boolean on, boolean live)
    {
        for (String bone : chain)
        {
            this.add(new RigClaim(step, bone, owner, bone.equals(home) ? Part.HOME : Part.LINK, hold, chain, on, live));
        }
    }

    /** A bone the setting only looks at: a target or a pole. */
    public void reads(RigStep step, String bone, String owner, Part part, List<String> chain, boolean on, boolean live)
    {
        this.add(new RigClaim(step, bone, owner, part, Hold.READS, chain, on, live));
    }

    /** Every claim, the steps in the frame's order and each step's in its own. */
    public List<RigClaim> getClaims()
    {
        return this.claims;
    }

    /** The claims on a bone. */
    public List<RigClaim> on(String bone)
    {
        List<RigClaim> claims = this.bones.get(bone);

        return claims == null ? Collections.emptyList() : claims;
    }

    /**
     * The step that sets the bone's rotation right now — the last one in the frame's order, its
     * result is what is drawn — or {@code null} when the pose's own rotation shows.
     */
    public RigStep rotationDriver(String bone)
    {
        RigStep driver = null;

        for (RigClaim claim : this.on(bone))
        {
            if (claim.live() && claim.hold() == Hold.DRIVES)
            {
                driver = claim.step();
            }
        }

        return driver;
    }

    /** Whether a solve sets the bone's rotation, so an FK rotation of it has no honest effect. */
    public boolean locksRotation(String bone)
    {
        return this.rotationDriver(bone) != null;
    }

    /** Whether one of the step's settings acting right now runs through the bone (the bone is in it, not looked at). */
    public boolean holds(RigStep step, String bone)
    {
        for (RigClaim claim : this.on(bone))
        {
            if (claim.step() == step && claim.live() && claim.inChain())
            {
                return true;
            }
        }

        return false;
    }
}
