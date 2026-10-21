package mchorse.bbs_mod.cubic.rig;

import mchorse.bbs_mod.cubic.constraints.BoneConstraintsIO;
import mchorse.bbs_mod.cubic.ik.BoneIKIO;
import mchorse.bbs_mod.cubic.physics.BonePhysicsIO;
import mchorse.bbs_mod.cubic.shake.ShakeControl;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.base.BaseValue;

import java.util.EnumSet;
import java.util.Set;

/**
 * The rig of a model form as a preset: what each step has set up, in the form's own format — the
 * bones' values of the step under {@code bones}, and the step's values on the form under their own
 * names ({@link RigStep#boneValues}, {@link RigStep#formValues}). A preset holds the steps the form
 * had something of; applying it replaces those steps whole and leaves the rest of the rig alone.
 *
 * <p>The presets the separate tabs saved before the rig tab hold one step each in that tab's own
 * format; the preset list wraps them as {@code {legacy: step, data}} (see {@link #legacy}).</p>
 */
public final class RigPresets
{
    private static final String BONES = "bones";
    private static final String LEGACY = "legacy";
    private static final String LEGACY_DATA = "data";

    private RigPresets()
    {}

    /** The form's rig: every step that has something set up. */
    public static MapType write(ModelForm form)
    {
        MapType preset = new MapType();
        MapType bones = new MapType();
        Set<RigStep> steps = EnumSet.noneOf(RigStep.class);

        for (RigStep step : RigStep.SYSTEMS)
        {
            if (step.isUsed(form) || setOnBones(form, step))
            {
                steps.add(step);
            }
        }

        for (BaseValue value : form.bones.getAll())
        {
            if (!(value instanceof FormBone bone))
            {
                continue;
            }

            MapType data = new MapType();

            for (RigStep step : steps)
            {
                for (String id : step.boneValues)
                {
                    BaseValue field = bone.get(id);

                    if (field != null && !field.isDefault())
                    {
                        data.put(id, field.toData());
                    }
                }
            }

            if (!data.isEmpty())
            {
                bones.put(bone.getId(), data);
            }
        }

        if (!bones.isEmpty())
        {
            preset.put(BONES, bones);
        }

        for (RigStep step : steps)
        {
            for (String id : step.formValues)
            {
                preset.put(id, form.get(id).toData());
            }
        }

        return preset;
    }

    /** Whether some bone has a value of the step set — a controller is the IK's even with no chain. */
    private static boolean setOnBones(ModelForm form, RigStep step)
    {
        for (BaseValue value : form.bones.getAll())
        {
            if (value instanceof FormBone bone)
            {
                for (String id : step.boneValues)
                {
                    BaseValue field = bone.get(id);

                    if (field != null && !field.isDefault())
                    {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /** One step's preset saved by the tab it used to have, in that tab's format. */
    public static MapType legacy(RigStep step, MapType data)
    {
        MapType preset = new MapType();

        preset.putString(LEGACY, step.id);
        preset.put(LEGACY_DATA, data);

        return preset;
    }

    /** The steps a preset replaces. */
    public static Set<RigStep> steps(MapType preset)
    {
        Set<RigStep> steps = EnumSet.noneOf(RigStep.class);

        if (preset.has(LEGACY))
        {
            RigStep step = RigStep.byId(preset.getString(LEGACY));

            if (step != null)
            {
                steps.add(step);
            }

            return steps;
        }

        MapType bones = preset.getMap(BONES);

        for (RigStep step : RigStep.SYSTEMS)
        {
            boolean held = step.formValues.stream().anyMatch(preset::has);

            for (String bone : bones.keys())
            {
                held |= step.boneValues.stream().anyMatch(bones.getMap(bone)::has);
            }

            if (held)
            {
                steps.add(step);
            }
        }

        return steps;
    }

    /**
     * Applies a preset to the form as one edit, for one undo: the steps it holds are replaced whole,
     * the others stay as they are. An empty preset is the reset: the whole rig goes.
     */
    public static void apply(ModelForm form, MapType preset)
    {
        BaseValue.edit(form, IValueListener.FLAG_UNMERGEABLE, (f) ->
        {
            if (preset.isEmpty())
            {
                for (RigStep step : RigStep.SYSTEMS)
                {
                    clear(form, step);
                }
            }
            else if (preset.has(LEGACY))
            {
                applyLegacy(form, RigStep.byId(preset.getString(LEGACY)), preset.getMap(LEGACY_DATA));
            }
            else
            {
                for (RigStep step : steps(preset))
                {
                    clear(form, step);
                    read(form, step, preset);
                }
            }
        });
    }

    /** The step gone from the form: its bones' values and its own back to their defaults. */
    private static void clear(ModelForm form, RigStep step)
    {
        for (BaseValue value : form.bones.getAll())
        {
            if (value instanceof FormBone bone)
            {
                for (String id : step.boneValues)
                {
                    BaseValue field = bone.get(id);

                    if (field != null)
                    {
                        field.reset();
                    }
                }
            }
        }

        for (String id : step.formValues)
        {
            form.get(id).reset();
        }
    }

    private static void read(ModelForm form, RigStep step, MapType preset)
    {
        MapType bones = preset.getMap(BONES);

        for (String name : bones.keys())
        {
            MapType data = bones.getMap(name);

            for (String id : step.boneValues)
            {
                if (data.has(id))
                {
                    form.bones.getOrCreate(name).get(id).fromData(data.get(id));
                }
            }
        }

        for (String id : step.formValues)
        {
            if (preset.has(id))
            {
                form.get(id).fromData(preset.get(id));
            }
        }
    }

    /** A preset of the tab a step used to have: read the way that tab read it, replacing the step whole. */
    private static void applyLegacy(ModelForm form, RigStep step, MapType data)
    {
        if (step == RigStep.IK)
        {
            BoneIKIO.read(data, form.bones, true);
        }
        else if (step == RigStep.PHYSICS)
        {
            BonePhysicsIO.read(data, form.bones, form.wind, true);
        }
        else if (step == RigStep.LIMITS)
        {
            BoneConstraintsIO.read(data, form.bones, true);
        }
        else if (step == RigStep.SPLINE)
        {
            form.splines.fromData(data.has("splines", BaseType.TYPE_LIST) ? data.getList("splines") : new ListType());
        }
        else if (step == RigStep.SHAKE)
        {
            clear(form, RigStep.SHAKE);

            MapType bones = data.getMap(BONES);

            for (String id : bones.keys())
            {
                if (id.isEmpty() || !bones.has(id, BaseType.TYPE_MAP))
                {
                    continue;
                }

                ShakeControl control = new ShakeControl();

                control.fromData(bones.getMap(id));
                form.bones.getOrCreate(id).shake.set(true);
                form.shake.getOriginalValue().controls.put(id, control);
            }
        }
    }
}
