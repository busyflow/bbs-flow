package mchorse.bbs_mod.cubic.constraints;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.RigBone;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.rig.RigClaim.Hold;
import mchorse.bbs_mod.cubic.rig.RigOwners;
import mchorse.bbs_mod.cubic.rig.RigStep;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.forms.renderers.utils.RenderFrame;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.MathUtils;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ModelConstraintsRuntime
{
    private ModelConstraintsRuntime()
    {
    }

    public static void apply(ModelInstance instance)
    {
        if (instance == null || instance.model == null)
        {
            return;
        }

        Map<String, BoneConstraint> bones = getBones(instance);

        if (bones.isEmpty())
        {
            return;
        }

        applyToBones(instance.model, bones);
    }

    /**
     * The limits' hand on the form's bones (see {@link RigOwners}): every bone with limits set up,
     * its rotation clamped last. They act while an axis is switched on, read live as the film keys
     * them; set up with every axis off they sit on the bone and do nothing.
     */
    public static void claim(IModel model, ModelForm form, RigOwners owners)
    {
        for (BaseValue value : form.bones.getAll())
        {
            if (!(value instanceof FormBone bone) || bone.constraints.get().isDefault())
            {
                continue;
            }

            String id = bone.getId();
            boolean active = bone.constraints.get().isActive();

            owners.chain(RigStep.LIMITS, id, id, List.of(id), Hold.CLAMPS, active, active);
        }
    }

    /* One-slot per-frame memo - the walk used to run twice per form per frame (physics asks
     * once, the constraints stage once). Track overrides land before renders, so within a
     * frame the map cannot change; the epoch re-reads it next frame. */
    private static ModelInstance lastInstance;
    private static Object lastForm;
    private static long lastEpoch;
    private static Map<String, BoneConstraint> lastBones;

    /**
     * The form's enabled constraints by bone name, read fresh from the bone properties each
     * frame (a track's runtime override on the property is picked up for free that way).
     */
    public static Map<String, BoneConstraint> getBones(ModelInstance instance)
    {
        if (!(instance != null && instance.form instanceof ModelForm form))
        {
            return Collections.emptyMap();
        }

        if (RenderFrame.isEnabled() && lastInstance == instance && lastForm == form && lastEpoch == RenderFrame.getEpoch())
        {
            return lastBones;
        }

        Map<String, BoneConstraint> bones = collectBones(form);

        lastInstance = instance;
        lastForm = form;
        lastEpoch = RenderFrame.getEpoch();
        lastBones = bones;

        return bones;
    }

    private static Map<String, BoneConstraint> collectBones(ModelForm form)
    {
        Map<String, BoneConstraint> bones = null;

        for (BaseValue value : form.bones.getAll())
        {
            if (value instanceof FormBone bone)
            {
                BoneConstraint constraint = bone.constraints.get();

                if (constraint.isActive())
                {
                    if (bones == null)
                    {
                        bones = new HashMap<>();
                    }

                    bones.put(bone.getId(), constraint);
                }
            }
        }

        return bones == null ? Collections.emptyMap() : bones;
    }

    /**
     * Holds a bone's EVALUATED rotation (the constraint-stack result so far — FK, IK, physics),
     * not its FK channels: the evaluated rotation is decomposed to the euler branch nearest the FK
     * channels (a per-frame-stable reference, no frame-to-frame stranding), clamped per axis, and
     * written back to {@code orient}. The channels stay read-only FK truth, and an IK/physics
     * result survives the limit instead of being discarded for the clamped FK pose (limits used to
     * null {@code orient}, visually destroying the solve on a constrained chain bone). Works on
     * quaternion-mode bones too — the clamp reads the evaluated rotation, never a stale euler.
     */
    /**
     * Holds every limited bone of the rig: a locked axis goes back to where the keys put it, a
     * limited one into its limits. The limits are authored in DEGREES whatever the skeleton stores
     * its channels in, which is what {@link RigBone#fromDegrees()} is for; the decomposition goes
     * against the bone's own channels so a clamp lands on angles the animator would recognise.
     */
    private static void applyToBones(IModel model, Map<String, BoneConstraint> bones)
    {
        for (RigBone bone : model.getRigBones())
        {
            if (bone == null)
            {
                continue;
            }

            BoneConstraint c = bones.get(bone.getBoneName());

            if (c == null || !c.isActive())
            {
                continue;
            }

            Vector3f euler = bone.toCompatibleEuler(new Vector3f());

            lock(euler, c, bone.getBoneTransform().rotate);
            clamp(euler, c, bone.fromDegrees());

            bone.setOrient(bone.orientFromEuler(euler));
        }
    }

    /** Puts a locked axis back on the bone's own channel: the value its keys give it. */
    private static void lock(Vector3f euler, BoneConstraint c, Vector3f channels)
    {
        if (c.lockX)
        {
            euler.x = channels.x;
        }

        if (c.lockY)
        {
            euler.y = channels.y;
        }

        if (c.lockZ)
        {
            euler.z = channels.z;
        }
    }

    /**
     * Clamps euler angles to the constraint's limits, {@code scale} converting the
     * degree limits to the angles' unit. An axis whose switch is off is left alone
     * — free, not pinned to whatever its unused min/max happen to say.
     */
    private static void clamp(Vector3f euler, BoneConstraint c, float scale)
    {
        if (c.limitX)
        {
            euler.x = clampAxis(euler.x, c.minX * scale, c.maxX * scale);
        }

        if (c.limitY)
        {
            euler.y = clampAxis(euler.y, c.minY * scale, c.maxY * scale);
        }

        if (c.limitZ)
        {
            euler.z = clampAxis(euler.z, c.minZ * scale, c.maxZ * scale);
        }
    }

    private static float clampAxis(float value, float min, float max)
    {
        if (min > max)
        {
            float t = min;
            min = max;
            max = t;
        }

        return MathUtils.clamp(value, min, max);
    }
}
