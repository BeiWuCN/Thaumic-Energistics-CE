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
 * 终端自己的三个容器，当作奥术合成所用的存储交给 Thaumaturge。网格就是支付，跟它的工作台一样：
 * 网格里已经摆着材料，配方才匹配；再向网络收一份，等于要玩家摆好的物品的第二份。
 * {@link #consume} 跑两次，一次模拟一次真实，只有第二次真的取走，被拒的合成因此不花钱。
 * 网格是终端的不是网络的，装的是玩家此刻摆的东西。
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
     * 检查网格是否还保有配方匹配的内容、晶体够不够，然后一起扣。
     * @param consumption 一次合成耗掉的东西
     * @param simulate    为 true 时只检查
     * @return 容器是否仍匹配；非模拟时改动有没有落实
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

    /** 逐格比：消耗对象的网格是把九格按 {@code x + y * 3} 展平，也就是槽位的读取顺序。
     * 数量变了也算不匹配，合成中途被抽走物品的网格会被拒。 */
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

    /** 要素作外层循环：同一要素可能落在多个槽位，按槽位循环会从每个槽位都取走整份需求。 */
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
                // 就地收缩：物品栏交出的就是它持有的那个物品堆，与 Thaumaturge 工作台一致。
                crystal.shrink(take);
                outstanding -= take;
            }
        }
    }

    /** 余量还给原来的格位：空格先放；格位还有同类物品就叠上去；两者都不行才给玩家。 */
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
