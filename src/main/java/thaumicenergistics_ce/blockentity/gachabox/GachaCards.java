package thaumicenergistics_ce.blockentity.gachabox;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

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

    List<ItemStack> view() {
        return List.of(slots);
    }

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

    void save(ValueOutput output) {
        output.store(TAG_CARDS, Codec.list(ItemStack.OPTIONAL_CODEC), List.of(slots));
    }

    void load(ValueInput input) {
        List<ItemStack> saved = input.read(TAG_CARDS, Codec.list(ItemStack.OPTIONAL_CODEC))
                .orElse(List.of());
        for (int slot = 0; slot < slots.length; slot++) {
            slots[slot] = slot < saved.size() ? saved.get(slot) : ItemStack.EMPTY;
        }
    }
}
