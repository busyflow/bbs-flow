package mchorse.bbs_mod.ui.framework.elements.input.keyframes.shapes;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import net.minecraft.client.render.BufferBuilder;
import org.joml.Matrix4f;

public class SquareKeyframeShapeRenderer implements IKeyframeShapeRenderer
{
    @Override
    public IKey getLabel()
    {
        return UIKeys.KEYFRAMES_SHAPES_SQUARE;
    }

    @Override
    public Icon getIcon()
    {
        return Icons.SQUARE;
    }

    @Override
    public int getSdfShapeId()
    {
        return 0;
    }

    @Override
    public float getSdfHalfSize(int offset)
    {
        return offset;
    }

    @Override
    public void renderKeyframe(UIContext uiContext, BufferBuilder builder, Matrix4f matrix4f, int x, int y, int offset, int c)
    {
        if (KeyframeShapeRenderers.sdfPass)
        {
            KeyframeShapeRenderers.emitSdfQuad(builder, matrix4f, x, y, this, offset, c);
            return;
        }

        uiContext.batcher.fillRect(builder, matrix4f, x - offset, y - offset, offset * 2, offset * 2, c, c, c, c);
    }
}
