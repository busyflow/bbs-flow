package mchorse.bbs_mod.ui.framework.elements.input.keyframes.shapes;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import net.minecraft.client.render.BufferBuilder;
import org.joml.Matrix4f;

public class DiamondKeyframeShapeRenderer implements IKeyframeShapeRenderer
{
    @Override
    public IKey getLabel()
    {
        return UIKeys.KEYFRAMES_SHAPES_DIAMOND;
    }

    @Override
    public Icon getIcon()
    {
        return Icons.DIAMOND;
    }

    @Override
    public int getSdfShapeId()
    {
        return 1;
    }

    @Override
    public float getSdfHalfSize(int offset)
    {
        return offset * 1.5F;
    }

    @Override
    public void renderKeyframe(UIContext uiContext, BufferBuilder builder, Matrix4f matrix, int x, int y, int offset, int c)
    {
        if (KeyframeShapeRenderers.sdfPass)
        {
            KeyframeShapeRenderers.emitSdfQuad(builder, matrix, x, y, this, offset, c);
            return;
        }

        float fOffset = offset * 1.5F;

        builder.vertex(matrix, x, y - fOffset, 0F).color(c).next();
        builder.vertex(matrix, x - fOffset, y, 0F).color(c).next();
        builder.vertex(matrix, x, y + fOffset, 0F).color(c).next();
        builder.vertex(matrix, x + fOffset, y, 0F).color(c).next();
    }
}