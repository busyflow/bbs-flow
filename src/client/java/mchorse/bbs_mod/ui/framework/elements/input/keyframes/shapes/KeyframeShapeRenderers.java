package mchorse.bbs_mod.ui.framework.elements.input.keyframes.shapes;

import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.keyframes.KeyframeShape;
import net.minecraft.client.render.BufferBuilder;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

public class KeyframeShapeRenderers
{
    public static final Map<KeyframeShape, IKeyframeShapeRenderer> SHAPES = new HashMap<>();

    /** True while batching keyframe shapes into a POSITION_TEXTURE_COLOR GPU SDF pass. */
    public static boolean sdfPass;

    static
    {
        SHAPES.put(KeyframeShape.SQUARE, new SquareKeyframeShapeRenderer());
        SHAPES.put(KeyframeShape.TRIANGLE, new TriangleKeyframeShapeRenderer());
        SHAPES.put(KeyframeShape.DIAMOND, new DiamondKeyframeShapeRenderer());
        SHAPES.put(KeyframeShape.TRI_STAR, new StarsKeyframeShapeRenderer(3, Icons.TRI_STAR, UIKeys.KEYFRAMES_SHAPES_TRI_STAR));
        SHAPES.put(KeyframeShape.FOUR_STAR, new StarsKeyframeShapeRenderer(4, Icons.FOUR_STAR, UIKeys.KEYFRAMES_SHAPES_FOUR_STAR));
        SHAPES.put(KeyframeShape.FIVE_STAR, new StarsKeyframeShapeRenderer(5, Icons.FIVE_STAR, UIKeys.KEYFRAMES_SHAPES_FIVE_STAR));
        SHAPES.put(KeyframeShape.SIX_STAR, new StarsKeyframeShapeRenderer(6, Icons.SIX_STAR, UIKeys.KEYFRAMES_SHAPES_SIX_STAR));
        SHAPES.put(KeyframeShape.CIRCLE, new CircleKeyframeShapeRenderer());
    }

    public static void emitSdfQuad(BufferBuilder builder, Matrix4f matrix, int x, int y, IKeyframeShapeRenderer shape, int offset, int c)
    {
        float half = Math.max(1F, shape.getSdfHalfSize(offset));
        float pad = 1.5F;
        float r = half + pad;
        float uvExt = r / half;
        float uShift = shape.getSdfShapeId() * 10F;

        builder.vertex(matrix, x - r, y + r, 0F).texture(uShift - uvExt, uvExt).color(c).next();
        builder.vertex(matrix, x + r, y + r, 0F).texture(uShift + uvExt, uvExt).color(c).next();
        builder.vertex(matrix, x + r, y - r, 0F).texture(uShift + uvExt, -uvExt).color(c).next();
        builder.vertex(matrix, x - r, y - r, 0F).texture(uShift - uvExt, -uvExt).color(c).next();
    }
}
