package mchorse.bbs_mod.cubic.data.model;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.MathUtils;

/**
 * A weld: one cube's face glued onto another cube's face, so a joint between two bones keeps sealed
 * when it bends instead of opening the usual box-model gap. It holds the cubes themselves, so a cube
 * moved, re-ordered or carried into another group keeps its welds; the model's file names them by
 * their group and their place in it ({@link Model#toData}).
 *
 * <p>The two cubes play no roles of their own: which one the seam leans to is decided by the bone
 * hierarchy ({@link #parentShare}), not by which side is the source.</p>
 */
public class ModelWeld
{
    public ModelCube sourceCube;
    public CubeFace sourceFace;
    public ModelCube targetCube;
    public CubeFace targetFace;

    /** Largest bend (degrees) the seam keeps following; past it the shear eases to a hold, so extreme poses don't blow it out. */
    public float maxAngle = 120F;

    /**
     * How far the bend spreads from the seam, as a fraction (0..1) of each welded cube's length along
     * the bone axis. Small values keep the deformation in a thin band right at the joint, the rest of
     * the cube rigid; near 1 it spreads across the whole cube the way a plain shear does.
     */
    public float seamFalloff = 0.35F;

    /**
     * The PARENT bone's share (0..1) of the joint's deformation. 0.5 puts the seam on the bisector —
     * both cubes crease equally; 0 keeps the parent rigid (an elbow: the upper arm stays hard, the
     * forearm takes the whole fold); 1 is the mirror case. The other bone takes the remainder, so the
     * joint stays sealed.
     */
    public float parentShare = 0.5F;

    /** Whether the seam also spreads twist — a turn about the bone axis, as a wrist turns — across the band. */
    public boolean twist;

    /** Whether the two sides share shading normals along the seam, so the crease reads as a rounded edge under light. */
    public boolean smooth;

    public ModelWeld(ModelCube sourceCube, CubeFace sourceFace, ModelCube targetCube, CubeFace targetFace)
    {
        this.sourceCube = sourceCube;
        this.sourceFace = sourceFace;
        this.targetCube = targetCube;
        this.targetFace = targetFace;
    }

    /** The settings, into a weld's entry in the file; the cubes it joins are the model's to write. */
    public void settingsToData(MapType data)
    {
        data.putFloat("max_angle", this.maxAngle);
        data.putFloat("seam_falloff", this.seamFalloff);
        data.putFloat("parent_share", this.parentShare);
        data.putBool("twist", this.twist);
        data.putBool("smooth", this.smooth);
    }

    /** The settings, out of a weld's entry in the file; a missing one keeps its default. */
    public void settingsFromData(MapType data)
    {
        this.maxAngle = data.getFloat("max_angle", this.maxAngle);
        this.seamFalloff = MathUtils.clamp(data.getFloat("seam_falloff", this.seamFalloff), 0F, 1F);
        this.parentShare = MathUtils.clamp(data.getFloat("parent_share", this.parentShare), 0F, 1F);
        this.twist = data.getBool("twist", this.twist);
        this.smooth = data.getBool("smooth", this.smooth);
    }
}
