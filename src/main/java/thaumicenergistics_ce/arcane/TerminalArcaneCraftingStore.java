package thaumicenergistics_ce.arcane;

import appeng.api.inventories.InternalInventory;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneCraftingStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.transaction.RootCommitJournal;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

/**
 * 把终端自己的三个容器当作奥术合成的存储交给 Thaumaturge：网格、六个晶体槽、法杖槽。
 * 网格就是支付，跟它自己的工作台一样：网格里摆着什么，配方才配得上；再向网络收一份，等于要玩家摆好的物品的第二份。
 *
 * <p>这里实现的 {@link #consume} 直接改容器，同时把改动记进传来那个事务，
 * 所以合成失败、或者任何一个外层事务中止，三个容器都会自己回到原样。
 * 网格是终端的不是网络的，装的是玩家此刻摆的东西。
 */
public final class TerminalArcaneCraftingStore implements IArcaneCraftingStore {

    private final InternalInventory grid;
    private final InternalInventory crystals;
    private final InternalInventory wand;
    private final Player player;

    /** 记下三个容器改动前的内容，事务中止时靠它还原。 */
    private final InventoryJournal journal;

    public TerminalArcaneCraftingStore(
            InternalInventory grid, InternalInventory crystals, InternalInventory wand, Player player) {
        this.grid = grid;
        this.crystals = crystals;
        this.wand = wand;
        this.player = player;
        this.journal = new InventoryJournal(grid, crystals, wand);
    }

    /**
     * 先查网格是不是还保有配方匹配时的内容、晶体够不够，然后一起扣。
     * 网格对不上就返回 {@code false}，让合成中止。
     *
     * @param consumption 一次合成耗掉的东西
     * @param transaction 这次改动所属的、已经打开的事务
     * @return 容器是否仍与 {@code consumption.grid()} 相符；不符就是 {@code false}
     */
    @Override
    public boolean consume(Consumption consumption, TransactionContext transaction) {
        if (!matches(consumption.grid()) || !hasCrystals(consumption.crystals())) {
            return false;
        }
        List<ItemStack> remainders = consumption.remainders();
        List<ItemStack> overflow = new ArrayList<>();
        // 改动之前先取快照：NeoForge 的快照要能回到第一次改动前的样子。
        journal.updateSnapshots(transaction);
        for (int slot = 0; slot < PartArcaneCraftingTerminal.GRID_SIZE; slot++) {
            ItemStack current = grid.getStackInSlot(slot);
            ItemStack remainder = slot < remainders.size() ? remainders.get(slot) : ItemStack.EMPTY;
            grid.setItemDirect(slot, consumeOne(current, remainder, overflow));
        }
        consumeCrystals(consumption.crystals());
        if (!consumption.wand().isEmpty()) {
            wand.setItemDirect(PartArcaneCraftingTerminal.WAND_SLOT, consumption.wand());
        }
        if (!overflow.isEmpty()) {
            // 只有合成真的成立才交还：中止时玩家还在原处，手里的东西没动过。
            new RootCommitJournal(() -> overflow.forEach(stack -> giveBack(stack))).updateSnapshots(transaction);
        }
        return true;
    }

    /** 逐格比：消耗对象的网格正好是九个格位，按 {@code x + y * 3} 展平，也就是槽位的读取顺序。
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
                crystals.setItemDirect(slot, shrunk(crystal, take));
                outstanding -= take;
            }
        }
    }

    /**
     * 取走格位上的一个，并把这格留下的东西放回去。
     * 走的都是整堆替换，不就地改：快照存的就是原来的那几叠物品，就地改会连快照一起改掉，中止就还原不回来了。
     */
    private static ItemStack consumeOne(ItemStack current, ItemStack remainder, List<ItemStack> overflow) {
        ItemStack left = shrunk(current, 1);
        if (remainder.isEmpty()) {
            return left;
        }
        if (left.isEmpty()) {
            return remainder;
        }
        if (ItemStack.isSameItemSameComponents(left, remainder)) {
            remainder.grow(left.getCount());
            return remainder;
        }
        // 格位里还剩别的东西，留下的就没处放，等合成成立再给玩家。
        overflow.add(remainder);
        return left;
    }

    /** 给玩家：物品栏放不下就掉在脚下。 */
    private void giveBack(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static ItemStack shrunk(ItemStack stack, int amount) {
        return stack.getCount() <= amount ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - amount);
    }

    /**
     * 三个容器的快照。中止时把每一叠换回原样，合成中途被改动的容器因此能整个还原。
     */
    private static final class InventoryJournal extends SnapshotJournal<List<List<ItemStack>>> {

        private final InternalInventory[] inventories;

        InventoryJournal(InternalInventory grid, InternalInventory crystals, InternalInventory wand) {
            this.inventories = new InternalInventory[] { grid, crystals, wand };
        }

        @Override
        protected List<List<ItemStack>> createSnapshot() {
            return Arrays.stream(inventories).map(InventoryJournal::copy).toList();
        }

        @Override
        protected void revertToSnapshot(List<List<ItemStack>> snapshot) {
            for (int i = 0; i < inventories.length && i < snapshot.size(); i++) {
                restore(inventories[i], snapshot.get(i));
            }
        }

        private static List<ItemStack> copy(InternalInventory inventory) {
            List<ItemStack> contents = new ArrayList<>(inventory.size());
            for (int slot = 0; slot < inventory.size(); slot++) {
                contents.add(inventory.getStackInSlot(slot).copy());
            }
            return contents;
        }

        private static void restore(InternalInventory inventory, List<ItemStack> contents) {
            for (int slot = 0; slot < contents.size() && slot < inventory.size(); slot++) {
                inventory.setItemDirect(slot, contents.get(slot).copy());
            }
        }
    }
}
