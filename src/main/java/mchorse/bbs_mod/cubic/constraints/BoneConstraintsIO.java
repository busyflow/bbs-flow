package mchorse.bbs_mod.cubic.constraints;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.forms.forms.utils.ValueBones;
import mchorse.bbs_mod.settings.values.base.BaseValue;

/**
 * The constraints exchange format: {@code {bones: {name: {enabled, limits, min: [x,y,z], max: [x,y,z],
 * lock, stiffness}}}}. It is what constraint presets are saved as, and what forms saved before the
 * bones group stored in their {@code constraints} blob — one reader serves both. The form itself
 * now persists constraints inside {@link ValueBones}, not in this format.
 */
public final class BoneConstraintsIO
{
    private BoneConstraintsIO()
    {
    }

    /**
     * Reads the exchange format into the bones group. With {@code reset}, every bone's
     * constraint is neutralized first — a preset is a complete state, so a bone absent
     * from it ends up unconstrained (legacy form loading passes false: the group is fresh).
     */
    public static void read(MapType root, ValueBones bones, boolean reset)
    {
        if (reset)
        {
            for (BaseValue value : bones.getAll())
            {
                if (value instanceof FormBone bone && !bone.constraints.get().isDefault())
                {
                    bone.constraints.set(new BoneConstraint());
                }
            }
        }

        if (root == null || !root.has("bones", BaseType.TYPE_MAP))
        {
            return;
        }

        MapType boneMap = root.getMap("bones");

        for (String key : boneMap.keys())
        {
            if (key.isEmpty() || !boneMap.has(key, BaseType.TYPE_MAP))
            {
                continue;
            }

            MapType entry = boneMap.getMap(key);

            if (!entry.getBool("enabled", true))
            {
                continue;
            }

            BoneConstraint constraint = new BoneConstraint();

            /* The exchange format's own "enabled" gate is read above; a preset that
             * predates the per-axis switches meant all three. */
            constraint.limitX = entry.getList("limits").getBool(0, true);
            constraint.limitY = entry.getList("limits").getBool(1, true);
            constraint.limitZ = entry.getList("limits").getBool(2, true);
            constraint.minX = getFloat(entry.getList("min"), 0, BoneConstraint.DEFAULT_MIN);
            constraint.minY = getFloat(entry.getList("min"), 1, BoneConstraint.DEFAULT_MIN);
            constraint.minZ = getFloat(entry.getList("min"), 2, BoneConstraint.DEFAULT_MIN);
            constraint.maxX = getFloat(entry.getList("max"), 0, BoneConstraint.DEFAULT_MAX);
            constraint.maxY = getFloat(entry.getList("max"), 1, BoneConstraint.DEFAULT_MAX);
            constraint.maxZ = getFloat(entry.getList("max"), 2, BoneConstraint.DEFAULT_MAX);
            constraint.lockX = entry.getList("lock").getBool(0, false);
            constraint.lockY = entry.getList("lock").getBool(1, false);
            constraint.lockZ = entry.getList("lock").getBool(2, false);
            constraint.stiffnessX = getFloat(entry.getList("stiffness"), 0, 0F);
            constraint.stiffnessY = getFloat(entry.getList("stiffness"), 1, 0F);
            constraint.stiffnessZ = getFloat(entry.getList("stiffness"), 2, 0F);

            bones.getOrCreate(key).constraints.set(constraint);
        }
    }

    /** Writes every bone's limits that aren't neutral in the exchange format; an empty result means none are. */
    public static MapType write(ValueBones bones)
    {
        MapType root = new MapType();
        MapType boneMap = new MapType();

        for (BaseValue value : bones.getAll())
        {
            if (!(value instanceof FormBone bone) || bone.getId().isEmpty())
            {
                continue;
            }

            BoneConstraint c = bone.constraints.get();

            if (c.isDefault())
            {
                continue;
            }

            MapType entry = new MapType();

            entry.putBool("enabled", true);

            ListType limits = new ListType();

            limits.addBool(c.limitX);
            limits.addBool(c.limitY);
            limits.addBool(c.limitZ);
            entry.put("limits", limits);

            ListType min = new ListType();

            min.addFloat(c.minX);
            min.addFloat(c.minY);
            min.addFloat(c.minZ);

            ListType max = new ListType();

            max.addFloat(c.maxX);
            max.addFloat(c.maxY);
            max.addFloat(c.maxZ);

            entry.put("min", min);
            entry.put("max", max);

            if (c.lockX || c.lockY || c.lockZ)
            {
                ListType lock = new ListType();

                lock.addBool(c.lockX);
                lock.addBool(c.lockY);
                lock.addBool(c.lockZ);
                entry.put("lock", lock);
            }

            if (c.stiffnessX > 0F || c.stiffnessY > 0F || c.stiffnessZ > 0F)
            {
                ListType stiffness = new ListType();

                stiffness.addFloat(c.stiffnessX);
                stiffness.addFloat(c.stiffnessY);
                stiffness.addFloat(c.stiffnessZ);
                entry.put("stiffness", stiffness);
            }

            boneMap.put(bone.getId(), entry);
        }

        if (boneMap.size() == 0)
        {
            return new MapType();
        }

        root.put("bones", boneMap);

        return root;
    }

    private static float getFloat(ListType list, int index, float def)
    {
        BaseType element = list == null ? null : list.get(index);

        if (BaseType.isNumeric(element))
        {
            return element.asNumeric().floatValue();
        }

        return def;
    }
}
