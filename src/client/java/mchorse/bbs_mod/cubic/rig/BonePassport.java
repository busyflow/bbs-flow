package mchorse.bbs_mod.cubic.rig;

import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.utils.joml.Matrices;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A bone as it rests, before anything poses it: where it points, how long it is and how it is
 * turned. Rig steps that aim bones read it here instead of each working it out for itself — the
 * rest rotation bugs of October lived in the copies that had drifted apart.
 *
 * <p>Measures are the skeleton's own: a cubic model's pixels, as its pivots are authored, a BOBJ
 * armature's bind units. An axis is only ever read for its direction, so each step keeps the
 * zero length test it was written with; {@link #scale} turns an authored measure into model space.
 *
 * <p>The two skeletons disagree on a child that sits on the bone's own pivot: a cubic bone reports
 * the zero and leaves the step to skip it, a BOBJ bone looks further for a direction.
 */
public abstract class BonePassport
{
    private static final float EPS = 1.0e-6F;

    /** The passport of that model's skeleton, or null for a model no rig step can pose. */
    public static BonePassport of(IModel model)
    {
        if (model instanceof Model cubic)
        {
            return new Cubic(cubic);
        }

        if (model instanceof BOBJModel bobj)
        {
            return new Bobj(bobj);
        }

        return null;
    }

    /** Model units per authored unit: a cubic pixel is a sixteenth of a block; a BOBJ armature's measures are taken as they are. */
    public abstract float scale();

    /**
     * Where the bone points at rest, heading down a chain: towards {@code child}; at the chain's end
     * ({@code child} null), on along the line the chain came in on from {@code parent}; with no
     * parent either, the way the bone leads by itself — its first child, else straight down. Null
     * when the bone is missing (or a cubic chain's named parent is). Unnormalised, authored measure.
     */
    public abstract Vector3f restAxisPastTip(String bone, String child, String parent);

    /**
     * Where the bone points at rest towards {@code child}; with none, the way the bone leads by
     * itself (a BOBJ bone with no child off its head keeps to the line its parent led into it).
     * Unlike {@link #restAxisPastTip} the last bone of a chain does not carry on the chain's line —
     * the physics limits measure from this one. Null when the bone is missing. Unnormalised,
     * authored measure.
     */
    public abstract Vector3f restAxis(String bone, String child);

    /**
     * The rest length from the bone to {@code child}, in model units — what the physics simulates
     * with; with no child, to the bone's first child, a quarter block when it has none. Never
     * shorter than a hair, so a bone on its child's pivot still has a length. -1 when a bone is
     * missing.
     */
    public abstract float restLength(String bone, String child);

    /**
     * The bone's own turn at rest, relative to its parent: a cubic bone's authored rest rotation,
     * a BOBJ bone's bind rotation off its parent. A fresh copy; null when the bone is missing.
     */
    public abstract Quaternionf restRotation(String bone);

    /**
     * Where the bone sits at rest in model rest space, authored measure: a cubic pivot where the
     * renderer draws it — carried through the rest rotation of every bone above it, each turning
     * about its own pivot (a wrist under a rest-rotated elbow is drawn elsewhere than its authored
     * pivot); a BOBJ bone's bind position. A fresh copy; null when the bone is missing.
     */
    public abstract Vector3f restPosition(String bone);

    /**
     * The world rotation the bone's parent frame has at rest — what a captured parent frame reads
     * with nothing posed: a cubic bone's ancestors' rest rotations stacked root-most first; a BOBJ
     * bone's own bind rotation, which BOBJ frames fold into the parent side. A fresh copy; null
     * when the bone is missing.
     */
    public abstract Quaternionf restParentRotation(String bone);

    /**
     * Direction to rotation: the local rotation that swings a rest axis onto the world direction
     * {@code from → to}, as seen from the parent's world frame — the step a chain writer takes for
     * each bone. Normalises {@code restAxis} in place, for the twist a writer takes about it. Null
     * when either direction has no length, before or after the turn into the parent's frame.
     */
    public static Quaternionf aim(Vector3f restAxis, Vector3f from, Vector3f to, Quaternionf parentWorld)
    {
        Vector3f direction = new Vector3f(to).sub(from);

        if (restAxis.lengthSquared() < EPS * EPS || direction.lengthSquared() < EPS * EPS)
        {
            return null;
        }

        restAxis.normalize();
        direction.normalize();
        new Quaternionf(parentWorld).invert().transform(direction);

        if (direction.lengthSquared() < EPS * EPS)
        {
            return null;
        }

        return Matrices.fromToMirroredX(restAxis, direction.normalize());
    }

    private static final class Cubic extends BonePassport
    {
        private final Model model;

        private Cubic(Model model)
        {
            this.model = model;
        }

        @Override
        public float scale()
        {
            return 1F / 16F;
        }

        @Override
        public Vector3f restAxisPastTip(String bone, String child, String parent)
        {
            ModelGroup group = this.model.getGroup(bone);

            if (group == null)
            {
                return null;
            }

            ModelGroup next = child == null ? null : this.model.getGroup(child);

            if (next != null)
            {
                return new Vector3f(next.initial.translate).sub(group.initial.translate);
            }

            if (parent != null)
            {
                ModelGroup previous = this.model.getGroup(parent);

                return previous == null ? null : new Vector3f(group.initial.translate).sub(previous.initial.translate);
            }

            return leads(group);
        }

        @Override
        public Vector3f restAxis(String bone, String child)
        {
            ModelGroup group = this.model.getGroup(bone);

            if (group == null)
            {
                return null;
            }

            ModelGroup next = child == null ? null : this.model.getGroup(child);

            return next != null ? new Vector3f(next.initial.translate).sub(group.initial.translate) : leads(group);
        }

        @Override
        public float restLength(String bone, String child)
        {
            ModelGroup group = this.model.getGroup(bone);

            if (group == null)
            {
                return -1F;
            }

            if (child != null)
            {
                ModelGroup next = this.model.getGroup(child);

                return next == null ? -1F : length(group, next);
            }

            float len = 0.25F;

            if (group.children != null && !group.children.isEmpty())
            {
                len = length(group, group.children.get(0));
            }

            return len <= EPS ? EPS : len;
        }

        @Override
        public Quaternionf restRotation(String bone)
        {
            ModelGroup group = this.model.getGroup(bone);

            return group == null ? null : Matrices.toLocalRotationZYXDegrees(group.initial.rotate);
        }

        @Override
        public Vector3f restPosition(String bone)
        {
            ModelGroup group = this.model.getGroup(bone);

            if (group == null)
            {
                return null;
            }

            Vector3f position = new Vector3f(group.initial.translate);

            /* Nearest ancestor first — the renderer's matrix stack read inside out. A copy of the
             * authored pivot when nothing above carries a rest rotation (the common case). */
            for (ModelGroup parent = group.parent; parent != null; parent = parent.parent)
            {
                Vector3f rotate = parent.initial.rotate;

                if (rotate.x != 0F || rotate.y != 0F || rotate.z != 0F)
                {
                    Vector3f pivot = parent.initial.translate;

                    Matrices.toLocalRotationZYXDegrees(rotate).transform(position.sub(pivot)).add(pivot);
                }
            }

            return position;
        }

        @Override
        public Quaternionf restParentRotation(String bone)
        {
            if (this.model.getGroup(bone) == null)
            {
                return null;
            }

            Quaternionf rest = new Quaternionf();
            String id = this.model.getParentGroupKey(bone);
            int guard = 0;

            while (id != null && !id.isEmpty() && guard++ < 256)
            {
                ModelGroup group = this.model.getGroup(id);

                if (group == null)
                {
                    break;
                }

                /* World order is root-most first; walking up, each ancestor pre-multiplies. */
                rest = Matrices.toLocalRotationZYXDegrees(group.initial.rotate).mul(rest);

                String parent = this.model.getParentGroupKey(id);

                if (parent == null || parent.equals(id))
                {
                    break;
                }

                id = parent;
            }

            return rest;
        }

        private static float length(ModelGroup a, ModelGroup b)
        {
            float dx = (b.initial.translate.x - a.initial.translate.x) / 16F;
            float dy = (b.initial.translate.y - a.initial.translate.y) / 16F;
            float dz = (b.initial.translate.z - a.initial.translate.z) / 16F;
            float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

            return len <= EPS ? EPS : len;
        }

        /** The way a bone points by itself: at its first child, else straight down. */
        private static Vector3f leads(ModelGroup group)
        {
            if (group.children != null && !group.children.isEmpty())
            {
                return new Vector3f(group.children.get(0).initial.translate).sub(group.initial.translate);
            }

            return new Vector3f(0F, -1F, 0F);
        }
    }

    private static final class Bobj extends BonePassport
    {
        private final BOBJModel model;

        private Bobj(BOBJModel model)
        {
            this.model = model;
        }

        @Override
        public float scale()
        {
            return 1F;
        }

        @Override
        public Vector3f restAxisPastTip(String bone, String child, String parent)
        {
            BOBJBone b = this.model.getArmature().bones.get(bone);

            if (b == null)
            {
                return null;
            }

            BOBJBone next = child == null ? null : this.model.getArmature().bones.get(child);

            if (next != null)
            {
                Vector3f out = next.relBoneMat.getTranslation(new Vector3f());

                if (out.lengthSquared() > EPS * EPS)
                {
                    return out;
                }
            }

            /* The bone's own bind offset is the line its parent leads into it. */
            if (parent != null)
            {
                Vector3f out = b.relBoneMat.getTranslation(new Vector3f());

                if (out.lengthSquared() > EPS * EPS)
                {
                    return out;
                }
            }

            Vector3f out = this.firstChild(b);

            return out != null ? out : new Vector3f(0F, -1F, 0F);
        }

        @Override
        public Vector3f restAxis(String bone, String child)
        {
            BOBJBone b = this.model.getArmature().bones.get(bone);

            if (b == null)
            {
                return null;
            }

            BOBJBone next = child == null ? null : this.model.getArmature().bones.get(child);

            if (next != null)
            {
                Vector3f out = next.relBoneMat.getTranslation(new Vector3f());

                if (out.lengthSquared() > EPS * EPS)
                {
                    return out;
                }
            }

            Vector3f out = this.firstChild(b);

            if (out != null)
            {
                return out;
            }

            if (b.parentBone != null)
            {
                out = b.relBoneMat.getTranslation(new Vector3f());

                if (out.lengthSquared() > EPS * EPS)
                {
                    return out;
                }
            }

            return new Vector3f(0F, -1F, 0F);
        }

        @Override
        public float restLength(String bone, String child)
        {
            BOBJBone b = this.model.getArmature().bones.get(bone);

            if (b == null)
            {
                return -1F;
            }

            if (child != null)
            {
                BOBJBone next = this.model.getArmature().bones.get(child);

                if (next == null)
                {
                    return -1F;
                }

                float len = boneLength(next);

                return len <= EPS ? EPS : len;
            }

            float len = 0.25F;

            for (BOBJBone candidate : this.model.getArmature().orderedBones)
            {
                if (candidate != null && candidate.parentBone == b)
                {
                    len = boneLength(candidate);
                    break;
                }
            }

            return len <= EPS ? EPS : len;
        }

        @Override
        public Quaternionf restRotation(String bone)
        {
            BOBJBone b = this.model.getArmature().bones.get(bone);

            return b == null ? null : b.relBoneMat.getNormalizedRotation(new Quaternionf());
        }

        @Override
        public Vector3f restPosition(String bone)
        {
            BOBJBone b = this.model.getArmature().bones.get(bone);

            return b == null ? null : b.boneMat.getTranslation(new Vector3f());
        }

        @Override
        public Quaternionf restParentRotation(String bone)
        {
            BOBJBone b = this.model.getArmature().bones.get(bone);

            return b == null ? null : b.boneMat.getUnnormalizedRotation(new Quaternionf());
        }

        /** The axis to the first child off the bone's head (a child on the head itself gives none), or null. */
        private Vector3f firstChild(BOBJBone bone)
        {
            for (BOBJBone candidate : this.model.getArmature().orderedBones)
            {
                if (candidate != null && candidate.parentBone == bone)
                {
                    Vector3f out = candidate.relBoneMat.getTranslation(new Vector3f());

                    if (out.lengthSquared() > EPS * EPS)
                    {
                        return out;
                    }
                }
            }

            return null;
        }

        /**
         * Rest distance from the bone's parent to it, in model space. Not the parent-relative
         * matrix: that is in the parent's local frame, which carries the armature's scale (a
         * Mixamo rig's 0.01 made every segment a hundred times too long).
         */
        private static float boneLength(BOBJBone bone)
        {
            if (bone.parentBone == null)
            {
                return bone.boneMat.getTranslation(new Vector3f()).length();
            }

            return bone.boneMat.getTranslation(new Vector3f()).distance(bone.parentBone.boneMat.getTranslation(new Vector3f()));
        }
    }
}
