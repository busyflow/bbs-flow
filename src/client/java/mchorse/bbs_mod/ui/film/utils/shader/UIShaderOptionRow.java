package mchorse.bbs_mod.ui.film.utils.shader;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.clips.misc.CurveClip;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UINumericInput;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.resizers.layout.LabelRowResizer;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.iris.ShaderMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * One option of the shader panel: the name the pack's translations give it, and on the right a
 * control that suits it, the way Iris picks one.
 *
 * <ul>
 *     <li>on/off &mdash; a switch;</li>
 *     <li>an option the pack lists under {@code sliders=} &mdash; a slider over the values it
 *     allows;</li>
 *     <li>any other list of values &mdash; a button that steps through them (left click forward,
 *     right click back).</li>
 * </ul>
 *
 * <p>While a film curve sets the option at the playhead, the name turns accent, and the control
 * shows the curve's value and is locked. A change that waits for the pack to reload turns the name
 * orange. Right click on the row offers a reset to the pack's default, and for an option
 * that can be animated, a curve for it in the clip (or taking the clip's curve out).</p>
 */
public class UIShaderOptionRow extends UIElement
{
    /** Width of a control; narrower than a usual value field, so the names of the pack's options fit */
    static final int CONTROL_WIDTH = 64;

    private static final int TOGGLE_WIDTH = 24;

    private final UIShaderPanel panel;
    private final ShaderMenu.Cell cell;
    private final String optionId;
    private final String label;
    private final List<String> values;

    private UIToggle toggle;
    private UISliderTrackpad slider;
    private UICirculate circulate;

    /**
     * @param where for a search hit, the screen the option lives on; null otherwise
     */
    public UIShaderOptionRow(UIShaderPanel panel, ShaderMenu.Cell cell, String where)
    {
        this.panel = panel;
        this.cell = cell;
        this.optionId = cell.optionId;
        this.label = panel.getBrowser().optionLabel(cell.optionId);
        this.values = new ArrayList<>(cell.allowedValues);

        UIElement control;
        int width = CONTROL_WIDTH;

        if (cell.booleanOption)
        {
            control = this.toggle = new UIToggle(IKey.EMPTY, (b) -> panel.set(this.optionId, Boolean.toString(b.getValue())));
            width = TOGGLE_WIDTH;
        }
        else if (cell.slider && this.values.size() > 1)
        {
            this.slider = new ValueSlider((v) -> this.pick((int) Math.round(v)));
            this.slider.limit(0, this.values.size() - 1, true);
            this.slider.snap(1);
            this.slider.forcedLabel(this::valueText);
            control = this.slider;
        }
        else
        {
            this.circulate = new UICirculate((b) -> this.pick(b.getValue()));

            for (String value : this.values)
            {
                this.circulate.addLabel(IKey.constant(this.valueLabel(value)));
            }

            if (this.values.isEmpty())
            {
                this.circulate.addLabel(this::valueText);
                this.circulate.setEnabled(false);
            }

            control = this.circulate;
        }

        UIShaderRowName name = new UIShaderRowName(() -> this.label, this::nameColor, where);

        name.tooltip((IKey) this::tooltip);
        this.context((menu) ->
        {
            if (this.panel.curveValue(this.optionId) == null && this.cell.defaultValue != null && !this.cell.defaultValue.equals(this.panel.value(this.optionId)))
            {
                menu.action(Icons.REFRESH, UIKeys.GENERAL_RESET, () -> this.panel.set(this.optionId, this.cell.defaultValue));
            }

            this.panel.curveActions(menu, CurveClip.SHADER_CURVES_PREFIX + this.optionId, this.panel.isCurvable(this.optionId));
        });

        LabelRowResizer.apply(this, width, true);
        this.add(name, control);
        this.valueBinding(this::update);
    }

    private void pick(int index)
    {
        if (index >= 0 && index < this.values.size())
        {
            this.panel.set(this.optionId, this.values.get(index));
        }
    }

    /** Puts the control on the option's value, or on the curve's while one sets it. */
    private void update()
    {
        Double curve = this.panel.curveValue(this.optionId);
        String value = this.panel.value(this.optionId);
        int index = curve == null ? this.values.indexOf(value) : this.nearest(curve);

        if (this.toggle != null)
        {
            this.toggle.setEnabled(curve == null);
            this.toggle.setValue(curve == null ? "true".equals(value) : curve >= 0.5D);
        }
        else if (this.slider != null)
        {
            this.slider.setEnabled(curve == null);

            if (!this.slider.isUserEditing() && index >= 0)
            {
                this.slider.setValue(index);
            }
        }
        else if (this.circulate != null && !this.values.isEmpty())
        {
            this.circulate.setEnabled(curve == null);

            if (index >= 0)
            {
                this.circulate.setValue(index);
            }

            if (curve != null || index < 0)
            {
                this.circulate.label = IKey.constant(this.valueText());
            }
        }
    }

    /** The name is accent while a curve sets the option, orange while a change waits for a reload */
    private int nameColor()
    {
        if (this.panel.curveValue(this.optionId) != null)
        {
            return Colors.A100 | BBSSettings.primaryColor.get();
        }

        return this.panel.isPending(this.optionId) ? Colors.A100 | Colors.ORANGE : Colors.WHITE;
    }

    /** The allowed value closest to a curve's, for the control to stand on */
    private int nearest(double curve)
    {
        int nearest = -1;
        double distance = Double.MAX_VALUE;

        for (int i = 0; i < this.values.size(); i++)
        {
            try
            {
                double d = Math.abs(Double.parseDouble(this.values.get(i)) - curve);

                if (d < distance)
                {
                    distance = d;
                    nearest = i;
                }
            }
            catch (NumberFormatException e)
            {}
        }

        return nearest;
    }

    private String valueLabel(String value)
    {
        return this.panel.getBrowser().valueLabel(this.optionId, value, this.cell.booleanOption);
    }

    /** What the control reads: the curve's value while one sets the option, the option's otherwise */
    private String valueText()
    {
        Double curve = this.panel.curveValue(this.optionId);

        if (curve == null)
        {
            return this.valueLabel(this.panel.value(this.optionId));
        }

        return this.cell.booleanOption ? this.valueLabel(Boolean.toString(curve >= 0.5D)) : UINumericInput.format(curve);
    }

    private String tooltip()
    {
        StringBuilder builder = new StringBuilder(this.label).append(" (").append(this.optionId).append(")");
        String comment = this.panel.getBrowser().optionComment(this.optionId);

        if (!comment.isEmpty())
        {
            builder.append("\n").append(comment);
        }

        if (this.panel.curveValue(this.optionId) != null)
        {
            builder.append("\n").append(UIKeys.FILM_SHADER_CURVE.get());
        }
        else if (!this.panel.isCurvable(this.optionId))
        {
            builder.append("\n").append(UIKeys.FILM_SHADER_RELOAD.get());
        }

        return builder.toString();
    }

    /**
     * A slider over the positions of the values an option allows. It moves by position, so there
     * is no number to type in: the middle button that would bring up the text field does nothing.
     */
    private static class ValueSlider extends UISliderTrackpad
    {
        public ValueSlider(Consumer<Double> callback)
        {
            super(callback);
        }

        @Override
        public boolean subMouseClicked(UIContext context)
        {
            if (context.mouseButton == 2)
            {
                return this.area.isInside(context);
            }

            return super.subMouseClicked(context);
        }
    }
}
