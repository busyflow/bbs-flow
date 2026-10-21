package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.rig.RigClaim;
import mchorse.bbs_mod.cubic.rig.RigClaim.Hold;
import mchorse.bbs_mod.cubic.rig.RigClaim.Part;
import mchorse.bbs_mod.cubic.rig.RigOwners;
import mchorse.bbs_mod.cubic.rig.RigStep;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ModelIKRuntime
{
    private ModelIKRuntime()
    {
    }

    public static void apply(ModelInstance instance, Map<String, Vector3f> controllerTargets, Map<String, Vector3f> poleTargets)
    {
        if (instance == null || instance.model == null || !(instance.form instanceof ModelForm form))
        {
            return;
        }

        IModel model = instance.model;
        ModelIKCache.Compiled compiled = ModelIKCache.compile(model, form);

        if (compiled == null)
        {
            return;
        }

        List<ModelIKCache.CompiledChain> chains = compiled.chains();

        if (chains == null || chains.isEmpty())
        {
            return;
        }

        /* The chains' animatable scalars, read live from the bone properties: the form's static
         * setting, or the film's `ik` track when one is driving the bone (the property's runtime
         * value). One read per chain per solve — no override map and nothing to clear. */
        Map<String, IKControl> controls = new HashMap<>();

        for (ModelIKCache.CompiledChain chain : chains)
        {
            controls.put(chain.tip(), form.ik.get().get(chain.tip()));
        }

        ModelIKApplier.apply(model, chains, compiled.bones(), controllerTargets, poleTargets, form.ikTargetWeights, form.poleTargetWeights, controls);
    }

    /**
     * Whether pointing the chain that ends at {@code tip} (spanning {@code
     * chainLength} bones up) at {@code target} is cyclic — the target is one of
     * the chain's own bones, so the solve's variables would move the very point
     * it chases. Such a chain does not compile; the panel uses this to mark the
     * pick loudly.
     */
    public static boolean isCyclicTarget(IModel model, String tip, int chainLength, String target)
    {
        if (model == null || tip == null || tip.isEmpty() || target == null || target.isEmpty())
        {
            return false;
        }

        return ModelIKCache.chainIdsFor(model, tip, chainLength).contains(target);
    }

    /**
     * Whether the chain ending at {@code tip} has the classic two-bone shape:
     * exactly two directed bones after the auto-tail drop. A classic-marked
     * chain of any other shape solves on the core — the panel uses this to mark
     * the toggle so the fallback never surprises silently.
     */
    public static boolean isClassicShape(IModel model, String tip, int chainLength, boolean tipRotation)
    {
        if (model == null || tip == null || tip.isEmpty())
        {
            return false;
        }

        List<String> ids = ModelIKCache.chainIdsFor(model, tip, chainLength);
        int size = ids.size();

        if (tipRotation && ModelIKApplier.autoTailId(model, ids) != null)
        {
            size--;
        }

        return size == 3;
    }

    /**
     * The bones the chain ending at {@code tip} spans, root to tip — the panel's
     * overlap check (overlapping chains merge into one core tree, which kicks a
     * classic chain off its analytic path).
     */
    public static List<String> chainBones(IModel model, String tip, int chainLength)
    {
        if (model == null || tip == null || tip.isEmpty())
        {
            return Collections.emptyList();
        }

        return ModelIKCache.chainIdsFor(model, tip, chainLength);
    }

    /**
     * Opens the IK solve dump for the duration of one transform gesture, so the
     * log holds exactly the drag being investigated — the transform editor calls
     * this when a drag begins and ends. A no-op unless the applier's debug flag
     * is compiled on, so the call sites cost nothing in a normal build.
     */
    public static void logGesture(boolean active)
    {
        ModelIKApplier.setLogging(active);
    }

    /**
     * The IK step's hand on the form's bones (see {@link RigOwners}): every chain the form sets up,
     * its links and the tip it lives on, its target and, with the pole on, its pole. A chain acts
     * when it compiles, is switched on and has a weight — the scalars read live, so a film track that
     * switches a chain off for the current tick frees its bones. It sets the rotation of every bone
     * of it but the tip, which rides the chain on its own rotation unless the chain solves that too.
     */
    public static void claim(IModel model, ModelForm form, RigOwners owners)
    {
        ModelIKCache.Compiled compiled = ModelIKCache.compile(model, form);
        Set<String> compiling = new HashSet<>();

        if (compiled != null && compiled.chains() != null)
        {
            for (ModelIKCache.CompiledChain chain : compiled.chains())
            {
                compiling.add(chain.tip());
            }
        }

        for (BaseValue value : form.bones.getAll())
        {
            if (!(value instanceof FormBone bone) || !bone.hasChain())
            {
                continue;
            }

            String tip = bone.getId();
            IKControl control = bone.ik.get();
            List<String> chain = ModelIKCache.chainIdsFor(model, tip, bone.ikChainLength.get());
            boolean on = control.enabled;
            boolean live = on && control.weight > 0F && compiling.contains(tip);

            for (String id : chain)
            {
                boolean home = id.equals(tip);
                Hold hold = home && !bone.ikTipRotation.get() ? Hold.FOLLOWS : Hold.DRIVES;

                owners.add(new RigClaim(RigStep.IK, id, tip, home ? Part.HOME : Part.LINK, hold, chain, on, live));
            }

            owners.reads(RigStep.IK, bone.ikTarget.get(), tip, Part.TARGET, chain, on, live);

            if (control.pole)
            {
                owners.reads(RigStep.IK, bone.ikPoleTarget.get(), tip, Part.POLE, chain, on, live);
            }
        }
    }

    /**
     * The chains the form's config compiles to on this model — the bones each one spans, root
     * to tip, keyed by the tip bone that names it, in the config's order. For tools that act on
     * a chain as a whole (the bake) without re-deriving the topology the solver uses.
     */
    public static Map<String, List<String>> getChains(IModel model, ModelForm form)
    {
        ModelIKCache.Compiled compiled = ModelIKCache.compile(model, form);

        if (compiled == null || compiled.chains() == null || compiled.chains().isEmpty())
        {
            return Collections.emptyMap();
        }

        Map<String, List<String>> chains = new LinkedHashMap<>();

        for (ModelIKCache.CompiledChain chain : compiled.chains())
        {
            chains.put(chain.tip(), List.copyOf(chain.chainRootToEffector()));
        }

        return chains;
    }

    public static List<String> getControllers(ModelInstance instance)
    {
        return targetsOf(instance, false);
    }

    /** The pole-target bones of all chains that have one — the film keys a pole anchor sheet off each. */
    public static List<String> getPoleControllers(ModelInstance instance)
    {
        return targetsOf(instance, true);
    }

    private static List<String> targetsOf(ModelInstance instance, boolean pole)
    {
        if (instance == null || instance.model == null || !(instance.form instanceof ModelForm form))
        {
            return Collections.emptyList();
        }

        ModelIKCache.Compiled compiled = ModelIKCache.compile(instance.model, form);

        if (compiled == null || compiled.chains() == null || compiled.chains().isEmpty())
        {
            return Collections.emptyList();
        }

        Set<String> unique = new LinkedHashSet<>();

        for (ModelIKCache.CompiledChain chain : compiled.chains())
        {
            String target = pole ? chain.poleTarget() : chain.target();

            if (target != null && !target.isEmpty())
            {
                unique.add(target);
            }
        }

        return unique.isEmpty() ? Collections.emptyList() : new ArrayList<>(unique);
    }
}
