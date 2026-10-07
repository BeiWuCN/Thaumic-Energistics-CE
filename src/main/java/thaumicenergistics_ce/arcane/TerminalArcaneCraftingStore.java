package thaumicenergistics_ce.arcane;

import appeng.api.inventories.InternalInventory;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneCraftingStore;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

/**
 * 终端自己的三个容器，以奥术合成所用存储的形式交给 Thaumaturge。
 * 与 Thaumaturge 的工作台一样，网格本身就是支付：只有网格中已经放有
 * 材料时配方才匹配，所以再向网络收一份就等于索要玩家已放置物品的
 * 第二份副本。{@link #consume} 会运行两次，一次模拟一次真实，
 * 只有第二次才真正取走物品，这正是被拒绝的合成不付出任何代价的原因。
 * 网格属于终端而不属于网络，保存的是玩家此刻正在摆放的内容。
 */
public final class TerminalArcaneCraftingStore implements IArcaneCraftingStore {

    private final InternalInventory grid;
    private final InternalInventory crystals;
    private final InternalInventory wand;
    private final Player player;

    public TerminalArcaneCraftingStore(
            InternalInventory grid, InternalInventory crystals, InternalInventory wand, Player player) {
        this.grid = grid;
        this.crystals = crystals;
        this.wand = wand;
        this.player = player;
    }

    /**
     * 检查网格是否仍保有合成所匹配的内容、晶体是否够付，然后对两者一并扣费。
     * @param consumption 一次合成消耗掉的内容
     * @param simulate    为 true 时只做检查
     * @return 容器是否仍然匹配，以及在非模拟时改动是否已应用
     */
    @Override
    public boolean consume(Consumption consumption, boolean simulate) {
        if (grid == null || crystals == null || wand == null) {
            return false;
        }
        if (!matches(consumption.grid()) || !hasCrystals(consumption.crystals())) {
            return false;
        }
        if (simulate) {
            return true;
        }
        List<ItemStack> remainders = consumption.remainders();
        for (int slot = 0; slot < PartArcaneCraftingTerminal.GRID_SIZE; slot++) {
            grid.extractItem(slot, 1, false);
            placeRemainder(slot, slot < remainders.size() ? remainders.get(slot) : ItemStack.EMPTY);
        }
        consumeCrystals(consumption.crystals());
        if (!consumption.wand().isEmpty()) {
            wand.setItemDirect(PartArcaneCraftingTerminal.WAND_SLOT, consumption.wand());
        }
        return true;
    }

    /** 逐格比较：消耗对象的网格是把九个格位按 {@code x + y * 3} 展平，即
     * 槽位的读取顺序。数量变化同样算不匹配，因此合成过程中被抽走的网格会被拒绝。 */
    private boolean matches(List<ItemStack> expected) {
        if (expected.size() != PartArcaneCraftingTerminal.GRID_SIZE) {
            return false;
        }
        for (int slot = 0; slot < PartArcaneCraftingTerminal.GRID_SIZE; slot++) {
            if (!ItemStack.matches(grid.getStackInSlot(slot), expected.get(slot))) {
                return false;
            }
        }
        return true;
    }

    private boolean hasCrystals(AspectList needed) {
        for (Holder<IAspect> aspect : needed.aspects()) {
            int found = 0;
            for (int slot = 0; slot < PartArcaneCraftingTerminal.CRYSTAL_SLOTS; slot++) {
                Holder<IAspect> carried = EssentiaCrystals.aspectOf(crystals.getStackInSlot(slot));
                if (carried != null && carried.equals(aspect)) {
                    found += crystals.getStackInSlot(slot).getCount();
                }
            }
            if (found < needed.amountOf(aspect)) {
                return false;
            }
        }
        return true;
    }

    /** 要素作外层循环：同一种要素可能分布在多个槽位里，若按槽位循环，
     * 每个槽位都会被取走整份需求。 */
    private void consumeCrystals(AspectList needed) {
        for (Holder<IAspect> aspect : needed.aspects()) {
            int outstanding = needed.amountOf(aspect);
            for (int slot = 0; slot < PartArcaneCraftingTerminal.CRYSTAL_SLOTS && outstanding > 0; slot++) {
                ItemStack crystal = crystals.getStackInSlot(slot);
                Holder<IAspect> carried = EssentiaCrystals.aspectOf(crystal);
                if (carried == null || !carried.equals(aspect)) {
                    continue;
                }
                int take = Math.min(outstanding, crystal.getCount());
                // 就地收缩：物品栏交出的就是它持有的那个物品堆，与 Thaumaturge 的工作台一致。
                crystal.shrink(take);
                outstanding -= take;
            }
        }
    }

    /** 剩余物归还给它原本所在的格位：空出来的格位直接放入；格位仍有同类物品时
     * 叠加上去；只有两者都不可行时才交给玩家。 */
    private void placeRemainder(int slot, ItemStack remainder) {
        if (remainder.isEmpty()) {
            return;
        }
        ItemStack existing = grid.getStackInSlot(slot);
        if (existing.isEmpty()) {
            grid.setItemDirect(slot, remainder);
            return;
        }
        if (ItemStack.isSameItemSameComponents(existing, remainder)) {
            int room = existing.getMaxStackSize() - existing.getCount();
            int moved = Math.min(room, remainder.getCount());
            existing.grow(moved);
            remainder.shrink(moved);
            if (remainder.isEmpty()) {
                return;
            }
        }
        if (!player.getInventory().add(remainder)) {
            player.drop(remainder, false);
        }
    }
}
