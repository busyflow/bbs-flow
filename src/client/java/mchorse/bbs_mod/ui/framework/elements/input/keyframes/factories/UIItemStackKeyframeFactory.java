package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIItemStack;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UITrackValue;
import net.minecraft.item.ItemStack;

public class UIItemStackKeyframeFactory extends UIKeyframeFactory<ItemStack>
{
    private UIItemStack editor;

    public UIItemStackKeyframeFactory(UITrackValue<ItemStack> track, UIKeyframes editor)
    {
        super(track, editor);

        this.editor = new UIItemStack(this::setValue);
        this.editor.setStack(track.getValue());

        net.minecraft.entity.EquipmentSlot armorSlot = getArmorSlot(track.sheet.id);
        if (armorSlot != null)
        {
            this.editor.armorSlot(armorSlot);
        }

        this.scroll.add(this.editor);
    }

    private static net.minecraft.entity.EquipmentSlot getArmorSlot(String id)
    {
        if (id == null)
        {
            return null;
        }

        String tail = id;
        int slash = tail.lastIndexOf('/');
        if (slash >= 0)
        {
            tail = tail.substring(slash + 1);
        }

        return switch (tail)
        {
            case "armor_head", "item_head" -> net.minecraft.entity.EquipmentSlot.HEAD;
            case "armor_chest", "item_chest" -> net.minecraft.entity.EquipmentSlot.CHEST;
            case "armor_legs", "item_legs" -> net.minecraft.entity.EquipmentSlot.LEGS;
            case "armor_feet", "item_feet" -> net.minecraft.entity.EquipmentSlot.FEET;
            default -> null;
        };
    }
    @Override
    public void update()
    {
        this.editor.setStack(this.getDisplayValue());
    }

}