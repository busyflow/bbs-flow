package mchorse.bbs_mod.ui.film.crowds;

import mchorse.bbs_mod.film.crowds.CrowdArmor;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Dresses a crowd, kept to one screenful.
 *
 * <p>Four slot cards, two to a row. Each card carries the pieces that fit its slot as their own
 * inventory icons, leather through netherite, greyed until you click one into the mix - the icon is
 * the thing you recognise, so a wall of item names was both longer and harder to read than the
 * pictures it was describing. Beside every icon is a 0-4 ratio: zero is out, one to four is how
 * often it comes up against the rest, so clicking and dialling are the same control seen twice.</p>
 *
 * <p>A "bare" ratio at the top of each card is the share that wears nothing there, and each card has
 * its own enchanted switch, which puts the glint on whatever that slot rolls.</p>
 *
 * <p>It edits a single {@link CrowdArmor.Profile}; any extra profiles from older saves are folded
 * away on open so what shows is what rolls.</p>
 */
public class UICrowdArmorOverlayPanel extends UIOverlayPanel
{
    private static final String[] SLOT_NAMES = { "Helmet", "Chestplate", "Leggings", "Boots" };
    private static final Icon[] SLOT_ICONS = { Icons.ARMOR_HELMET, Icons.ARMOR_CHESTPLATE, Icons.ARMOR_LEGGINGS, Icons.ARMOR_BOOTS };
    /** The highest ratio a piece (or the bare share) can carry - keeps the sliders short and legible. */
    private static final int MAX_RATIO = 4;
    /** Item ids that fit each slot, in HEAD/CHEST/LEGS/FEET order, material-ordered. */
    private static final List<String>[] SLOT_ITEMS = buildSlotItems();

    /** Laid over an icon that is not in the mix, to sink it behind the ones that are. */
    private static final int DIMMED = 0xc0141414;

    private final CrowdArmor armor;
    private final Runnable onEdit;

    private final UIScrollView body;

    public UICrowdArmorOverlayPanel(CrowdArmor armor, Runnable onEdit)
    {
        super(IKey.constant("Crowd armor"));

        this.armor = armor;
        this.onEdit = onEdit;

        this.body = UI.scrollView(5, 5);
        this.body.full(this.content);
        this.content.add(this.body);

        this.rebuild();
    }

    private void edit()
    {
        if (this.onEdit != null)
        {
            this.onEdit.run();
        }
    }

    /**
     * The one profile this panel edits, collapsing any older multi-profile data down to the first so
     * the simplified UI stays truthful about what rolls.
     */
    private CrowdArmor.Profile profile()
    {
        while (this.armor.profiles.size() > 1)
        {
            this.armor.profiles.remove(this.armor.profiles.size() - 1);
        }

        if (this.armor.profiles.isEmpty())
        {
            CrowdArmor.Profile profile = new CrowdArmor.Profile();

            profile.name = "Armor";
            profile.weight = 0;
            this.armor.profiles.add(profile);
        }

        return this.armor.profiles.get(0);
    }

    private void rebuild()
    {
        this.body.removeAll();

        CrowdArmor.Profile profile = this.profile();
        boolean on = profile.weight > 0;

        UIToggle random = new UIToggle(IKey.constant("Randomize armour"), (b) ->
        {
            this.profile().weight = b.getValue() ? 1 : 0;
            this.edit();
            this.rebuild();
        });

        random.setValue(on);
        random.tooltip(IKey.constant("Off, the crowd wears nothing. On, each member rolls its slots from the pieces you pick below."));
        this.body.add(random);

        if (!on)
        {
            this.body.add(UI.label(IKey.constant("Turn on to pick what the crowd wears.")).marginTop(8));
            this.body.resize();

            return;
        }

        /* Two cards to a row: the four slots fit a normal dialog without scrolling, which a single
         * column of full-width rows never did. */
        this.body.add(UI.row(5, this.buildSlot(profile, 0), this.buildSlot(profile, 1)).marginTop(6));
        this.body.add(UI.row(5, this.buildSlot(profile, 2), this.buildSlot(profile, 3)).marginTop(4));

        this.body.resize();
    }

    private UIElement buildSlot(CrowdArmor.Profile profile, int slotIndex)
    {
        CrowdArmor.Slot slot = profile.slots[slotIndex];

        List<UIElement> rows = new ArrayList<>();

        UIToggle enabled = new UIToggle(IKey.constant("Worn"), (b) ->
        {
            slot.enabled = b.getValue();
            this.edit();
            this.rebuild();
        });

        enabled.setValue(slot.enabled);
        rows.add(enabled);

        if (slot.enabled)
        {
            UIToggle enchanted = new UIToggle(IKey.constant("Enchanted"), (b) ->
            {
                slot.enchanted = b.getValue();
                this.edit();
            });

            enchanted.setValue(slot.enchanted);
            enchanted.tooltip(IKey.constant("Put the enchantment glint on whatever this slot rolls."));
            rows.add(enchanted);

            /* The share that wears nothing here, so a crowd can be part-armoured. */
            rows.add(UI.row(4, UI.label(IKey.constant("Bare")).w(58), this.ratio(slot.emptyWeight, (v) ->
            {
                slot.emptyWeight = v;
                this.edit();
            })));

            for (String itemId : SLOT_ITEMS[slotIndex])
            {
                rows.add(UI.row(3,
                    new UIArmorItem(slot, itemId).wh(18, 18),
                    UI.label(IKey.constant(shortName(itemId))).w(40),
                    this.ratio(slot.get(itemId), (v) ->
                    {
                        slot.set(itemId, v);
                        this.edit();
                    })
                ));
            }
        }

        UISection section = new UISection(IKey.constant(SLOT_NAMES[slotIndex]));

        section.fields.add(rows.toArray(new UIElement[0]));

        return section;
    }

    /** A short 0-{@link #MAX_RATIO} integer slider used for every ratio in the panel. */
    private UITrackpad ratio(int value, java.util.function.IntConsumer consumer)
    {
        UITrackpad pad = new UITrackpad((v) -> consumer.accept(v.intValue()));

        pad.limit(0, MAX_RATIO, true);
        pad.setValue(value);
        pad.w(46);
        pad.tooltip(IKey.constant("How often this comes up against the others in the slot. 0 leaves it out."));

        return pad;
    }

    /**
     * One armour piece, drawn as its inventory icon.
     *
     * <p>Clicking drops it in or out of the mix; the ratio beside it is the same value, so neither
     * has to rebuild the card to agree with the other - the icon simply reads the weight every frame
     * and dims itself when it is zero.</p>
     */
    private class UIArmorItem extends UIElement
    {
        private final CrowdArmor.Slot slot;
        private final String itemId;
        private final ItemStack stack;

        public UIArmorItem(CrowdArmor.Slot slot, String itemId)
        {
            this.slot = slot;
            this.itemId = itemId;
            this.stack = stackFor(itemId);

            this.tooltip(IKey.constant(displayName(itemId)));
        }

        @Override
        public boolean subMouseClicked(UIContext context)
        {
            if (this.area.isInside(context))
            {
                this.slot.set(this.itemId, this.slot.get(this.itemId) > 0 ? 0 : 1);
                UICrowdArmorOverlayPanel.this.edit();
                UICrowdArmorOverlayPanel.this.rebuild();

                return true;
            }

            return super.subMouseClicked(context);
        }

        @Override
        public void render(UIContext context)
        {
            /* Flushed either side of the item: the icon goes through the game's own item renderer
             * rather than the batcher, so the batched work before it has to be on screen first, and
             * the dimming after it has to be queued behind the icon rather than under it. */
            context.batcher.getContext().draw();
            context.batcher.getContext().drawItem(this.stack, this.area.x, this.area.y);
            context.batcher.getContext().draw();

            if (this.slot.get(this.itemId) <= 0)
            {
                context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), DIMMED);
            }

            super.render(context);
        }
    }

    /**
     * "Diamond" rather than "Diamond Chestplate": the icon already says which slot it is, and the
     * card it sits in says it again.
     */
    private static String shortName(String itemId)
    {
        String path = itemId.substring(itemId.indexOf(':') + 1);

        for (String suffix : new String[] { "_helmet", "_chestplate", "_leggings", "_boots" })
        {
            path = path.replace(suffix, "");
        }

        if (path.equals("golden"))
        {
            path = "gold";
        }
        else if (path.equals("chainmail"))
        {
            path = "chain";
        }

        return path.isEmpty() ? "" : Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }

    private static String displayName(String itemId)
    {
        return stackFor(itemId).getName().getString();
    }

    private static ItemStack stackFor(String itemId)
    {
        return new ItemStack(Registries.ITEM.get(new Identifier(itemId)));
    }

    @SuppressWarnings("unchecked")
    private static List<String>[] buildSlotItems()
    {
        List<String>[] slots = new List[] { new ArrayList<String>(), new ArrayList<String>(), new ArrayList<String>(), new ArrayList<String>() };

        for (Item item : Registries.ITEM)
        {
            if (item instanceof ArmorItem armor)
            {
                int index = slotIndex(armor);

                if (index >= 0)
                {
                    slots[index].add(Registries.ITEM.getId(item).toString());
                }
            }
        }

        /* The elytra rides the chest slot but is not an ArmorItem, so it is added by hand. */
        slots[1].add(Registries.ITEM.getId(Items.ELYTRA).toString());

        Comparator<String> byMaterialThenName = Comparator
            .comparingInt((String id) -> materialRank(id))
            .thenComparing(UICrowdArmorOverlayPanel::displayName);

        for (List<String> slot : slots)
        {
            slot.sort(byMaterialThenName);
        }

        return slots;
    }

    private static int slotIndex(ArmorItem item)
    {
        switch (item.getSlotType())
        {
            case HEAD: return 0;
            case CHEST: return 1;
            case LEGS: return 2;
            case FEET: return 3;
            default: return -1;
        }
    }

    /** Tier order, so a card reads leather-to-netherite rather than alphabetical. */
    private static int materialRank(String id)
    {
        String[] tiers = { "leather", "chainmail", "iron", "golden", "diamond", "netherite", "turtle", "elytra" };

        for (int i = 0; i < tiers.length; i++)
        {
            if (id.contains(tiers[i]))
            {
                return i;
            }
        }

        return tiers.length;
    }
}
