package mchorse.bbs_mod.forms.forms.utils;

import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix4f;

public class Anchor implements IMapSerializable
{
    public static final String NO_ATTACHMENT = "";

    /**
     * Stable id of the replay this anchor hangs off ({@link #NO_ATTACHMENT} = none). An id, not a
     * list index: the target must survive replays being reordered or removed around it.
     */
    public String replay = NO_ATTACHMENT;
    public String attachment = "";
    /** Optional path following; absent in legacy anchors. Progress is a percent of world arc length. */
    public boolean spline;
    public float progress;
    public boolean horizontal;

    /**
     * Which components of the target's frame the anchored form rides — the same three the body
     * parts use ({@code BodyPart.inheritPosition} and friends), so one idea reads the same way
     * wherever something hangs off something else. All three on (the default) is the plain
     * behaviour: the target's matrix is taken whole. A component that is not inherited comes from
     * the frame the form would have had with no anchor at all.
     *
     * <p>These replace the older {@code translate} / {@code scale} pair, whose two flags meant
     * opposite things ({@code translate} = take ONLY the position, {@code scale} = take everything
     * BUT the scale) and could not express the other combinations at all. Files written by those
     * versions are converted on read, see {@link #fromData}.</p>
     */
    public boolean inheritPosition = true;
    public boolean inheritRotation = true;
    public boolean inheritScale = true;

    /**
     * How much of the target's motion the anchored form's bone physics feels, 0..1. At 1 (the default)
     * the physics lives in the world like an unanchored form's: a car driving off drags a passenger's hair
     * behind. Below it the chains ride the target's frame along with the rest of that share, so the hair
     * hangs still in the moving car and only swings to what the passenger does inside it.
     */
    public float physics = 1F;

    public final Transform transform = new Transform();

    /* Interpolation data */
    public Anchor previous;
    public float x;

    /** Runtime solver-state blend, kept separate from the keyframe's own target transition. */
    public transient Anchor blendSource;
    public transient float blendWeight = 1F;

    public Anchor()
    {}

    public Anchor(String replay, String attachment, boolean inheritPosition, boolean inheritRotation, boolean inheritScale)
    {
        this.replay = replay;
        this.attachment = attachment;
        this.inheritPosition = inheritPosition;
        this.inheritRotation = inheritRotation;
        this.inheritScale = inheritScale;
    }

    public boolean hasTarget()
    {
        return !this.replay.isEmpty();
    }

    public boolean inheritsWholeTarget()
    {
        return this.inheritPosition && this.inheritRotation && this.inheritScale;
    }

    /**
     * The resolved target matrix with the components this anchor doesn't inherit taken from
     * {@code fallback} — the matrix the form would have had unanchored. Returns the matrix itself
     * when there is nothing to take out.
     */
    public Matrix4f filterMatrix(Matrix4f matrix, Matrix4f fallback)
    {
        if (matrix == null || fallback == null || this.inheritsWholeTarget())
        {
            return matrix;
        }

        return Matrices.compose(
            this.inheritPosition ? matrix : fallback,
            this.inheritRotation ? matrix : fallback,
            this.inheritScale ? matrix : fallback
        );
    }

    public boolean isFadeIn()
    {
        return this.previous != null && this.hasTarget() && !this.previous.hasTarget();
    }

    public boolean isFadeOut()
    {
        return this.previous != null && !this.hasTarget() && this.previous.hasTarget();
    }

    public boolean hasSameTarget(Anchor anchor)
    {
        return anchor != null
            && this.replay.equals(anchor.replay)
            && this.attachment.equals(anchor.attachment)
            && this.spline == anchor.spline
            && this.horizontal == anchor.horizontal
            && this.inheritPosition == anchor.inheritPosition
            && this.inheritRotation == anchor.inheritRotation
            && this.inheritScale == anchor.inheritScale;
    }

    public Anchor copy()
    {
        Anchor anchor = new Anchor(this.replay, this.attachment, this.inheritPosition, this.inheritRotation, this.inheritScale);

        anchor.spline = this.spline;
        anchor.progress = this.progress;
        anchor.horizontal = this.horizontal;
        anchor.physics = this.physics;
        anchor.transform.copy(this.transform);

        return anchor;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (super.equals(obj))
        {
            return true;
        }

        if (obj instanceof Anchor anchor)
        {
            return this.hasSameTarget(anchor) && this.progress == anchor.progress
                && this.physics == anchor.physics && this.transform.equals(anchor.transform);
        }

        return false;
    }

    /** The saved settings, so equal anchors in different copies of a film read the same. */
    @Override
    public String toString()
    {
        return "Anchor" + this.toData();
    }

    @Override
    public void fromData(MapType data)
    {
        this.replay = data.getString("actor");
        this.attachment = data.getString("attachment");
        this.spline = data.getBool("spline", false);
        this.progress = data.getFloat("progress", 0F);
        this.horizontal = data.getBool("horizontal", false);
        this.physics = data.getFloat("physics", 1F);

        if (data.has("inheritPosition") || data.has("inheritRotation") || data.has("inheritScale"))
        {
            this.inheritPosition = data.getBool("inheritPosition", true);
            this.inheritRotation = data.getBool("inheritRotation", true);
            this.inheritScale = data.getBool("inheritScale", true);
        }
        else
        {
            /* Anchors written before the flags were split up. "translate" took only the position,
             * dropping both the rotation and the scale; "scale" dropped the scale alone and was
             * moot next to it. The position was always inherited, so it stays on. */
            boolean translate = data.getBool("translate", false);
            boolean scale = data.getBool("scale", false);

            this.inheritPosition = true;
            this.inheritRotation = !translate;
            this.inheritScale = !translate && !scale;
        }

        if (data.has("transform"))
        {
            this.transform.fromData(data.getMap("transform"));
        }
        else
        {
            this.transform.identity();
        }
    }

    @Override
    public void toData(MapType data)
    {
        data.putString("actor", this.replay);
        data.putString("attachment", this.attachment);
        if (this.spline)
        {
            data.putBool("spline", true);
            data.putFloat("progress", this.progress);
            data.putBool("horizontal", this.horizontal);
        }
        data.putBool("inheritPosition", this.inheritPosition);
        data.putBool("inheritRotation", this.inheritRotation);
        data.putBool("inheritScale", this.inheritScale);

        if (this.physics != 1F)
        {
            data.putFloat("physics", this.physics);
        }

        if (!this.transform.isDefault())
        {
            data.put("transform", this.transform.toData());
        }
    }
}
