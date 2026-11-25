package mchorse.bbs_mod.ui.film.clips.widgets;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class UIBitToggle extends UIElement
{
    public static final IKey PLUS = IKey.constant(" + ");
    private int value;
    public List<Bit> bits = new ArrayList<>();
    public Consumer<Integer> callback;

    public UIBitToggle(Consumer<Integer> callback)
    {
        super();

        this.callback = callback;

        this.h(UIConstants.CONTROL_HEIGHT);
    }

    public UIBitToggle all()
    {
        return this.point().angles();
    }

    public UIBitToggle point()
    {
        this.bits.add(new Bit(UIKeys.GENERAL_X, Colors.RED, Icons.X));
        this.bits.add(new Bit(UIKeys.GENERAL_Y, Colors.GREEN, Icons.Y));
        this.bits.add(new Bit(UIKeys.GENERAL_Z, Colors.BLUE, Icons.Z));

        return this;
    }

    public UIBitToggle angles()
    {
        this.bits.add(new Bit(UIKeys.CAMERA_PANELS_YAW, Colors.YELLOW, Icons.HORIZONTAL));
        this.bits.add(new Bit(UIKeys.CAMERA_PANELS_PITCH, Colors.CYAN, Icons.VERTICAL));
        this.bits.add(new Bit(UIKeys.CAMERA_PANELS_ROLL, Colors.MAGENTA, Icons.REFRESH));
        this.bits.add(new Bit(UIKeys.CAMERA_PANELS_FOV, Colors.WHITE & Colors.RGB, Icons.FRUSTUM));

        return this;
    }

    public int getValue()
    {
        return this.value;
    }

    public void setValue(int value)
    {
        this.value = value;

        this.updateTooltip();
    }

    private void updateTooltip()
    {
        List<IKey> keys = new ArrayList<>();

        for (int i = 0; i < this.bits.size(); i++)
        {
            Bit bit = this.bits.get(i);

            if (((this.value >> i) & 0b1) == 1)
            {
                keys.add(bit.label);
                keys.add(PLUS);
            }
        }

        if (keys.isEmpty())
        {
            this.removeTooltip();
        }
        else
        {
            keys.remove(keys.size() - 1);

            this.tooltip(IKey.comp(keys));
        }
    }

    private int getHoveredIndex(UIContext context)
    {
        if (!this.area.isInside(context.mouseX, context.mouseY) || this.bits.isEmpty() || this.area.w <= 0)
        {
            return -1;
        }

        int size = this.bits.size();

        return Math.min(size - 1, Math.max(0, (context.mouseX - this.area.x) * size / this.area.w));
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.area.isInside(context.mouseX, context.mouseY) && context.mouseButton == 0)
        {
            int index = this.getHoveredIndex(context);

            if (index < 0)
            {
                return false;
            }

            this.value ^= 1 << index;

            if (this.callback != null)
            {
                this.callback.accept(this.value);
            }

            this.updateTooltip();

            return true;
        }

        return super.subMouseClicked(context);
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        int size = this.bits.size();

        if (size == 0)
        {
            return;
        }

        int hovered = this.getHoveredIndex(context);

        for (int i = 0; i < size; i++)
        {
            int x1 = this.area.x + Math.round((float) i * this.area.w / size);
            int x2 = i == size - 1 ? this.area.ex() : this.area.x + Math.round((float) (i + 1) * this.area.w / size) - 1;
            boolean isSelected = ((this.value >> i) & 0x1) == 1;
            boolean isHover = hovered == i;
            Bit bit = this.bits.get(i);

            int fill = isSelected
                ? Colors.mulRGB(bit.color, isHover ? 0.36F : 0.26F)
                : (isHover ? Colors.mulRGB(bit.color, 0.14F) : 0xFF1A1A1E);
            int iconColor = isSelected
                ? bit.color
                : (isHover ? Colors.mulRGB(bit.color, 0.65F) : 0xFF5E5E66);

            context.batcher.surfaceBox(x1, this.area.y, x2, this.area.ey(), fill, false, false);

            if (bit.icon != null)
            {
                context.batcher.icon(bit.icon, iconColor, (x1 + x2) * 0.5F, this.area.my(), 0.5F, 0.5F);
            }
        }

        if (hovered >= 0)
        {
            FontRenderer font = context.batcher.getFont();
            Bit bit = this.bits.get(hovered);
            String label = bit.label.get();

            context.batcher.textCard(label, this.area.mx(font.getWidth(label)), this.area.my(font.getHeight()));
        }
    }

    public static class Bit
    {
        public IKey label;
        public int color;
        public Icon icon;

        public Bit(IKey label, int color, Icon icon)
        {
            this.label = label;
            this.color = Colors.A100 | color;
            this.icon = icon;
        }
    }
}