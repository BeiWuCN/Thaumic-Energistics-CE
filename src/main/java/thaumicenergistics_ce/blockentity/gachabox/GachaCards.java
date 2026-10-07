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
 * 箱子的四个速度卡片槽位，按卡片放入的顺序排列，所以保存的 tag 保持
 * 位置性。数组从不交出去：调用方拿到的是只读视图，或者它正在清空的槽位。
 * 只有卡片住在这里——上面的脑是方块状态，不是物品栏物品。
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

    /** 卡片当前的样子，用于 tooltip 的图标行：只读视图，所以不会复制任何东西。 */
    List<ItemStack> view() {
        return List.of(slots);
    }

    /** 把一张速度卡片放进第一个空槽位；箱子已经装满卡片时返回 false。 */
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

    /** 把卡片取出来，给那个从箱子里取走脑的玩家。 */
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
            // 空槽位必须走可选形式：普通的保存拒绝编码它，
            // 而这里抛异常会让整个 tag 一起赔进去，连同缓冲和绑定的所有者。
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
