package mchorse.bbs_mod.ui.film.utils.shader;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.UINumericInput;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.resizers.layout.LabelRowResizer;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * One curve of the world in its section of a curve clip: its name, and on the right the value the
 * world has now (a swatch for a colour). The value is only shown: the world is changed by a curve,
 * which right click makes for the clip, starting from that value. While a curve sets it at the
 * playhead, the name turns accent, the way an option row marks it.
 */
public class UIWorldCurveRow extends UIElement
{
    /** The values are short numbers, so the name gets the rest of the row */
    private static final int VALUE_WIDTH = 40;
    private static final int SWATCH = 10;

    private final UIShaderPanel panel;

    final WorldCurve curve;

    public UIWorldCurveRow(UIShaderPanel panel, WorldCurve curve)
    {
        this.panel = panel;
        this.curve = curve;

        UIElement name = new UIShaderRowName(curve.label::get, () -> panel.isCurved(curve.id) ? Colors.A100 | BBSSettings.primaryColor.get() : Colors.WHITE, null);
        UIElement value = new Value();

        name.tooltip(curve.label);
        value.h(UIConstants.CONTROL_HEIGHT);
        this.context((menu) -> panel.curveActions(menu, curve.id, true));

        LabelRowResizer.apply(this, VALUE_WIDTH, true);
        this.add(name, value);
    }

    /** The world's value, right-aligned the way a control's would sit */
    private class Value extends UIElement
    {
        @Override
        public void render(UIContext context)
        {
            WorldCurve curve = UIWorldCurveRow.this.curve;
            int right = this.area.ex() - 4;

            if (curve.color != null)
            {
                int sx = right - SWATCH;
                int sy = this.area.my() - SWATCH / 2;

                context.batcher.box(sx - 1, sy - 1, right + 1, sy + SWATCH + 1, Colors.A50);
                context.batcher.box(sx, sy, right, sy + SWATCH, Colors.A100 | curve.color.getAsInt());
            }
            else
            {
                FontRenderer font = context.batcher.getFont();
                Double value = curve.value.get();
                String text = value == null ? "-" : UINumericInput.format(value);

                context.batcher.text(text, right - font.getWidth(text), this.area.my(font.getHeight()), Colors.GRAY, true);
            }

            super.render(context);
        }
    }
}
