package thaumicenergistics_ce.blockentity.gachabox;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 箱子的四个速度卡槽，按放入顺序排，存档标记因此是位置性的。
 * 数组从不外发：调用方拿只读视图，或它正在清空的槽位。
 * 这里只有卡片。上面的脑是方块状态，不是物品栏物品。
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

    /** 卡片当前的样子，给 tooltip 的图标行：只读视图，不复制东西。 */
    List<ItemStack> view() {
        return List.of(slots);
    }

    /** 把一张速度卡放进第一个空槽；箱子已装满卡片则返回 false。 */
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

    void save(ValueOutput output) {
        // 每个槽位都写，包括空的，让它们通过的是可选编解码器：
        // 普通物品编解码器拒绝零数量，抛异常会赔掉整个标签。
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
