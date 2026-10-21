package mchorse.bbs_mod.cubic.render;

import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The drawing language of the rig's debug overlay: lines that can be hairline
 * or volumetric, solid or dashed, and its two markers — diamonds for handles
 * (goals, poles, controllers), squares for joints. Everything is emitted in the overlay's
 * drawing space through the given matrix — no Euler orientation anywhere
 * ({@code Draw.fillBoxTo}'s camera-convention angles diverge under the model
 * stack and tubes fly off the joints), thick lines and shapes are built from
 * cross-product bases and raw vertices instead.
 */
public final class DebugOverlay
{
    /** The drawing scale of a rig with no measurable bones at all. */
    private static final float FALLBACK_UNIT = 0.25F;

    private static final float EPS = 1.0e-6f;

    private DebugOverlay()
    {
    }

    /**
     * The scale an overlay draws a model at: the average REST bone length of the
     * WHOLE rig. One number per model, so every marker and wire on it comes out
     * the same size — it used to be the average segment of each chain, which made
     * a two-bone chain draw fat handles next to a five-bone one on the same model,
     * and made every handle jump the moment a chain's length changed.
     *
     * <p>Rest, never the posed skeleton: a pose-derived scale would breathe with
     * the animation, which is the same complaint one step further out. A bone's
     * length is its pivot's offset from its parent's: cubic pivots are absolute,
     * in the model's pixel space, so taken on their own they measured how far
     * a bone sits from the model's origin — handles on bones high up (hair on
     * a head) came out oversized. They come back to blocks the way the renderer
     * takes them ({@code ICubicRenderer.moveToGroupPivot}). BOBJ heads come off
     * the bind matrices, already in the overlay's space; the parent-relative
     * matrix would not do — it is in the parent's local frame, which carries the
     * armature's scale (a Mixamo rig's 0.01 inflated the unit a hundredfold).
     * Top-level bones have no parent to measure from and are left out.
     */
    public static float modelUnit(IModel model)
    {
        float total = 0F;
        int bones = 0;

        if (model instanceof Model cubic)
        {
            for (ModelGroup group : cubic.getAllGroups())
            {
                if (group.parent == null)
                {
                    continue;
                }

                float length = group.initial.translate.distance(group.parent.initial.translate) / 16F;

                if (length > EPS)
                {
                    total += length;
                    bones++;
                }
            }
        }
        else if (model instanceof BOBJModel bobj)
        {
            for (BOBJBone bone : bobj.getAllBOBJBones())
            {
                if (bone.parentBone == null)
                {
                    continue;
                }

                float length = bone.boneMat.getTranslation(new Vector3f()).distance(bone.parentBone.boneMat.getTranslation(new Vector3f()));

                if (length > EPS)
                {
                    total += length;
                    bones++;
                }
            }
        }

        return bones > 0 ? total / bones : FALLBACK_UNIT;
    }

    public static float[] rgb(int color)
    {
        return new float[] {Colors.getR(color), Colors.getG(color), Colors.getB(color)};
    }

    /** A line segment: hairline vertices when {@code thickness} is zero, a prism otherwise; split into dashes when asked. */
    public static void segment(BufferBuilder builder, Matrix4f matrix, float thickness, boolean dashed, float dash, Vector3f p1, Vector3f p2, float[] col, float a)
    {
        if (!dashed || dash <= 0F)
        {
            solid(builder, matrix, thickness, p1, p2, col, a);

            return;
        }

        int count = Math.max(1, Math.round(p1.distance(p2) / dash));

        /* An odd dash count keeps both endpoints solid. */
        if (count % 2 == 0)
        {
            count++;
        }

        for (int i = 0; i < count; i += 2)
        {
            Vector3f d1 = new Vector3f(p1).lerp(p2, i / (float) count);
            Vector3f d2 = new Vector3f(p1).lerp(p2, (i + 1) / (float) count);

            solid(builder, matrix, thickness, d1, d2, col, a);
        }
    }

    public static void line(BufferBuilder builder, Matrix4f matrix, Vector3f p1, Vector3f p2, float[] col, float a)
    {
        builder.vertex(matrix, p1.x, p1.y, p1.z).color(col[0], col[1], col[2], a).next();
        builder.vertex(matrix, p2.x, p2.y, p2.z).color(col[0], col[1], col[2], a).next();
    }

    private static void solid(BufferBuilder builder, Matrix4f matrix, float thickness, Vector3f p1, Vector3f p2, float[] col, float a)
    {
        if (thickness > 0F)
        {
            prism(builder, matrix, p1, p2, thickness, col, a);
        }
        else
        {
            line(builder, matrix, p1, p2, col, a);
        }
    }

    /** A thick line as a box built from cross-product basis vectors. */
    private static void prism(BufferBuilder builder, Matrix4f matrix, Vector3f p1, Vector3f p2, float thickness, float[] col, float a)
    {
        Vector3f d = new Vector3f(p2).sub(p1);
        float len2 = d.lengthSquared();

        if (len2 < 1e-12F)
        {
            return;
        }

        Vector3f ref = d.y * d.y > 0.98F * len2 ? new Vector3f(1F, 0F, 0F) : new Vector3f(0F, 1F, 0F);
        Vector3f u = new Vector3f(d).cross(ref).normalize().mul(thickness * 0.5F);
        Vector3f v = new Vector3f(d).cross(u).normalize().mul(thickness * 0.5F);

        Vector3f c1 = new Vector3f(p1).add(u).add(v);
        Vector3f c2 = new Vector3f(p1).sub(u).add(v);
        Vector3f c3 = new Vector3f(p1).sub(u).sub(v);
        Vector3f c4 = new Vector3f(p1).add(u).sub(v);
        Vector3f c5 = new Vector3f(p2).add(u).add(v);
        Vector3f c6 = new Vector3f(p2).sub(u).add(v);
        Vector3f c7 = new Vector3f(p2).sub(u).sub(v);
        Vector3f c8 = new Vector3f(p2).add(u).sub(v);

        quad(builder, matrix, c1, c2, c6, c5, col, a);
        quad(builder, matrix, c2, c3, c7, c6, col, a);
        quad(builder, matrix, c3, c4, c8, c7, col, a);
        quad(builder, matrix, c4, c1, c5, c8, col, a);
        quad(builder, matrix, c1, c2, c3, c4, col, a);
        quad(builder, matrix, c5, c6, c7, c8, col, a);
    }

    /** A diamond at {@code p}: a handle — a chain's goal or pole, a controller. */
    public static void diamond(BufferBuilder builder, MatrixStack stack, Vector3f p, float radius, float[] col, float a)
    {
        stack.push();
        stack.translate(p.x, p.y, p.z);
        octahedron(builder, stack, radius * 1.3F, col, a);
        stack.pop();
    }

    /** A square (a cube seen from any side) at {@code p}: a chain's joint. */
    public static void square(BufferBuilder builder, MatrixStack stack, Vector3f p, float radius, float[] col, float a)
    {
        float s = radius * 0.8F;

        stack.push();
        stack.translate(p.x, p.y, p.z);
        Draw.fillBox(builder, stack, -s, -s, -s, s, s, s, col[0], col[1], col[2], a);
        stack.pop();
    }

    /** An octahedron with apexes at {@code s} along every axis. */
    private static void octahedron(BufferBuilder builder, MatrixStack stack, float s, float[] col, float a)
    {
        Matrix4f m = stack.peek().getPositionMatrix();

        tri(builder, m, 0, s, 0, s, 0, 0, 0, 0, s, col, a);
        tri(builder, m, 0, s, 0, 0, 0, s, -s, 0, 0, col, a);
        tri(builder, m, 0, s, 0, -s, 0, 0, 0, 0, -s, col, a);
        tri(builder, m, 0, s, 0, 0, 0, -s, s, 0, 0, col, a);
        tri(builder, m, 0, -s, 0, 0, 0, s, s, 0, 0, col, a);
        tri(builder, m, 0, -s, 0, -s, 0, 0, 0, 0, s, col, a);
        tri(builder, m, 0, -s, 0, 0, 0, -s, -s, 0, 0, col, a);
        tri(builder, m, 0, -s, 0, s, 0, 0, 0, 0, -s, col, a);
    }

    private static void quad(BufferBuilder builder, Matrix4f matrix, Vector3f p1, Vector3f p2, Vector3f p3, Vector3f p4, float[] col, float a)
    {
        builder.vertex(matrix, p1.x, p1.y, p1.z).color(col[0], col[1], col[2], a).next();
        builder.vertex(matrix, p2.x, p2.y, p2.z).color(col[0], col[1], col[2], a).next();
        builder.vertex(matrix, p3.x, p3.y, p3.z).color(col[0], col[1], col[2], a).next();
        builder.vertex(matrix, p1.x, p1.y, p1.z).color(col[0], col[1], col[2], a).next();
        builder.vertex(matrix, p3.x, p3.y, p3.z).color(col[0], col[1], col[2], a).next();
        builder.vertex(matrix, p4.x, p4.y, p4.z).color(col[0], col[1], col[2], a).next();
    }

    private static void tri(BufferBuilder builder, Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float[] col, float a)
    {
        builder.vertex(matrix, x1, y1, z1).color(col[0], col[1], col[2], a).next();
        builder.vertex(matrix, x2, y2, z2).color(col[0], col[1], col[2], a).next();
        builder.vertex(matrix, x3, y3, z3).color(col[0], col[1], col[2], a).next();
    }
}
