package mchorse.bbs_mod.film.crowds;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A crowd's armour: a list of named profiles, one of which each member rolls into. A profile carries,
 * per armour slot, a weight for every item that may land there plus a weight for wearing nothing - so
 * "half the helmets diamond, half iron, the rest bare" is three weights, and a member's four slots are
 * rolled independently, which is what mixes a crowd instead of cloning one loadout across it.
 *
 * <p>All rolling is done from a caller-supplied {@link Random} so a crowd's armour is deterministic
 * against its seed - the same member gets the same kit every spawn.</p>
 */
public class CrowdArmor
{
    /** The four armour slots, in the order profiles store them. */
    public static final EquipmentSlot[] SLOTS =
    {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    public final List<Profile> profiles = new ArrayList<>();

    public boolean isEmpty()
    {
        return this.profiles.isEmpty();
    }

    /**
     * Roll one member's four armour stacks (HEAD, CHEST, LEGS, FEET), any of which may be empty.
     * Returns an all-empty set when no profile carries any weight.
     */
    public ItemStack[] roll(Random random)
    {
        ItemStack[] stacks = { ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY };
        Profile profile = this.pickProfile(random);

        if (profile == null)
        {
            return stacks;
        }

        for (int i = 0; i < SLOTS.length; i++)
        {
            stacks[i] = profile.slots[i].roll(random);
        }

        return stacks;
    }

    private Profile pickProfile(Random random)
    {
        int total = 0;

        for (Profile profile : this.profiles)
        {
            total += Math.max(0, profile.weight);
        }

        if (total <= 0)
        {
            return null;
        }

        int roll = random.nextInt(total);

        for (Profile profile : this.profiles)
        {
            roll -= Math.max(0, profile.weight);

            if (roll < 0)
            {
                return profile;
            }
        }

        return null;
    }

    public MapType toData()
    {
        MapType data = new MapType();
        ListType list = new ListType();

        for (Profile profile : this.profiles)
        {
            list.add(profile.toData());
        }

        data.put("profiles", list);

        return data;
    }

    public void fromData(BaseType data)
    {
        this.profiles.clear();

        if (data == null || !data.isMap())
        {
            return;
        }

        for (BaseType element : data.asMap().getList("profiles"))
        {
            if (element.isMap())
            {
                Profile profile = new Profile();

                profile.fromData(element.asMap());
                this.profiles.add(profile);
            }
        }
    }

    public CrowdArmor copy()
    {
        CrowdArmor copy = new CrowdArmor();

        copy.fromData(this.toData());

        return copy;
    }

    /** One named loadout: a relative weight against its siblings, and four independently-rolled slots. */
    public static class Profile
    {
        public String name = "Armor";
        public int weight = 1;
        public final Slot[] slots = { new Slot(), new Slot(), new Slot(), new Slot() };

        public MapType toData()
        {
            MapType data = new MapType();
            ListType slotList = new ListType();

            data.putString("name", this.name);
            data.putInt("weight", this.weight);

            for (Slot slot : this.slots)
            {
                slotList.add(slot.toData());
            }

            data.put("slots", slotList);

            return data;
        }

        public void fromData(MapType data)
        {
            this.name = data.getString("name", this.name);
            this.weight = data.getInt("weight", this.weight);

            ListType slotList = data.getList("slots");

            for (int i = 0; i < this.slots.length && i < slotList.size(); i++)
            {
                if (slotList.get(i).isMap())
                {
                    this.slots[i].fromData(slotList.get(i).asMap());
                }
            }
        }
    }

    /** One slot's weights: the chance of nothing, plus a weight per item id that may fill it. */
    public static class Slot
    {
        /**
         * Whether this slot dresses at all. Off means the member wears nothing here, whatever weights
         * are stored - so the item picks survive a slot being switched off and back on, rather than
         * being cleared. A disabled slot never rolls an item.
         */
        public boolean enabled = true;
        /** Whether whatever lands in this slot comes out enchanted, for the glint alone. */
        public boolean enchanted = false;
        public int emptyWeight = 1;
        /** Item id to weight; only weighted items are kept, so an empty map means "always bare". */
        public final Map<String, Integer> weights = new LinkedHashMap<>();

        public void set(String itemId, int weight)
        {
            if (weight <= 0)
            {
                this.weights.remove(itemId);
            }
            else
            {
                this.weights.put(itemId, weight);
            }
        }

        public int get(String itemId)
        {
            return this.weights.getOrDefault(itemId, 0);
        }

        public ItemStack roll(Random random)
        {
            if (!this.enabled)
            {
                return ItemStack.EMPTY;
            }

            int total = Math.max(0, this.emptyWeight);

            for (int weight : this.weights.values())
            {
                total += Math.max(0, weight);
            }

            if (total <= 0)
            {
                return ItemStack.EMPTY;
            }

            int roll = random.nextInt(total);

            roll -= Math.max(0, this.emptyWeight);

            if (roll < 0)
            {
                return ItemStack.EMPTY;
            }

            for (Map.Entry<String, Integer> entry : this.weights.entrySet())
            {
                roll -= Math.max(0, entry.getValue());

                if (roll < 0)
                {
                    return this.dress(stackFor(entry.getKey()));
                }
            }

            return ItemStack.EMPTY;
        }

        /**
         * Put the glint on a rolled piece when the slot asks for it.
         *
         * <p>A stack only glints in vanilla when it actually carries an enchantment, so this adds the
         * plainest one there is rather than inventing a marker: Unbreaking applies to every armour
         * piece and to the elytra, and a crowd member is scenery, so what it does is beside the
         * point - it is here to be seen.</p>
         */
        private ItemStack dress(ItemStack stack)
        {
            if (this.enchanted && !stack.isEmpty())
            {
                stack.addEnchantment(Enchantments.UNBREAKING, 1);
            }

            return stack;
        }

        public MapType toData()
        {
            MapType data = new MapType();
            MapType items = new MapType();

            data.putBool("enabled", this.enabled);
            data.putBool("enchanted", this.enchanted);
            data.putInt("empty", this.emptyWeight);

            for (Map.Entry<String, Integer> entry : this.weights.entrySet())
            {
                items.putInt(entry.getKey(), entry.getValue());
            }

            data.put("items", items);

            return data;
        }

        public void fromData(MapType data)
        {
            this.enabled = data.getBool("enabled", this.enabled);
            this.enchanted = data.getBool("enchanted", this.enchanted);
            this.emptyWeight = data.getInt("empty", this.emptyWeight);
            this.weights.clear();

            MapType items = data.getMap("items");

            for (String key : items.keys())
            {
                this.weights.put(key, items.getInt(key));
            }
        }
    }

    private static ItemStack stackFor(String itemId)
    {
        Identifier id = Identifier.tryParse(itemId);

        if (id == null || !Registries.ITEM.containsId(id))
        {
            return ItemStack.EMPTY;
        }

        return new ItemStack(Registries.ITEM.get(id));
    }
}
