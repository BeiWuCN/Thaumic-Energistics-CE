package thaumicenergistics_ce.blockentity.gachabox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * The box's four speed-card slots, in the order the cards went in, so the saved tag stays
 * positional. The array is never handed out: a caller gets a read-only view, or the slots it is
 * emptying. Only the cards live here - the brain on top is a blockstate, not an inventory item.
 */
final class GachaCards {

    private static final String TAG_CARDS = "Cards";

    private final BlockEntityGachaBox box;
    private final ItemStack[] slots = new ItemStack[GachaOdds.MAX_CARDS];

    GachaCards(BlockEntityGachaBox box) {
        this.box = box;
        Arrays.fill(this.slots, ItemStack.EMPTY);
    }

    int count() {
        int count = 0;
        for (ItemStack card : slots) {
            if (!card.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    boolean hasRoom() {
        return count() < slots.length;
    }

    /** The cards as they sit, for the tooltip's icon row: a read-only view, so nothing gets copied. */
    List<ItemStack> view() {
        return List.of(slots);
    }

    /** Puts one speed card in the first empty slot; false when the box is already full of them. */
    boolean add(ItemStack held) {
        for (int slot = 0; slot < slots.length; slot++) {
            if (slots[slot].isEmpty()) {
                slots[slot] = held.copyWithCount(1);
                box.setChanged();
                return true;
            }
        }
        return false;
    }

    /** Takes the cards back out, for the player who took the brain out of the box. */
    List<ItemStack> take() {
        List<ItemStack> taken = new ArrayList<>(slots.length);
        for (int slot = 0; slot < slots.length; slot++) {
            if (!slots[slot].isEmpty()) {
                taken.add(slots[slot]);
                slots[slot] = ItemStack.EMPTY;
            }
        }
        if (!taken.isEmpty()) {
            box.setChanged();
        }
        return taken;
    }

    void save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag saved = new ListTag();
        for (ItemStack card : slots) {
            // An empty slot has to go through the optional form: the plain save refuses to encode it,
            // and a throw here would cost the whole tag, the buffer and the bound owner with it.
            saved.add(card.saveOptional(registries));
        }
        tag.put(TAG_CARDS, saved);
    }

    void load(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag saved = tag.getList(TAG_CARDS, Tag.TAG_COMPOUND);
        for (int slot = 0; slot < slots.length; slot++) {
            slots[slot] = slot < saved.size()
                    ? ItemStack.parseOptional(registries, saved.getCompound(slot))
                    : ItemStack.EMPTY;
        }
    }
}
