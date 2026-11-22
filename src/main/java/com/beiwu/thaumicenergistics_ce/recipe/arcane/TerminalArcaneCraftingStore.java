package com.beiwu.thaumicenergistics_ce.recipe.arcane;

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
import com.beiwu.thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

public final class TerminalArcaneCraftingStore implements IArcaneCraftingStore {

    /** 网格固定三格宽；包围盒坐标映射回槽位时按这个步长走。 */
    private static final int GRID_WIDTH = 3;

    private final InternalInventory grid;
    private final InternalInventory crystals;
    private final InternalInventory wand;
    private final Player player;

    /**
     * 建输入时算出的包围盒。Thaumaturge 交给仓库的消耗表按包围盒打包，
     * 尺寸和偏移必须跟输入报的一致，否则扣费会被当成「材料变了」。
     */
    private final int left;
    private final int top;
    private final int width;
    private final int height;

    private final InventoryJournal journal;

    public TerminalArcaneCraftingStore(
            InternalInventory grid, InternalInventory crystals, InternalInventory wand, Player player) {
        this(grid, crystals, wand, player, 0, 0, GRID_WIDTH, GRID_WIDTH);
    }

    public TerminalArcaneCraftingStore(
            InternalInventory grid,
            InternalInventory crystals,
            InternalInventory wand,
            Player player,
            int left,
            int top,
            int width,
            int height) {
        this.grid = grid;
        this.crystals = crystals;
        this.wand = wand;
        this.player = player;
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
        this.journal = new InventoryJournal(grid, crystals, wand);
    }

    /** 包围盒内第 {@code (x, y)} 格对应的九格槽位号。 */
    private int slotAt(int x, int y) {
        return (top + y) * GRID_WIDTH + (left + x);
    }

    @Override
    public boolean consume(Consumption consumption, TransactionContext transaction) {
        if (!matches(consumption.grid()) || !hasCrystals(consumption.crystals())) {
            return false;
        }
        List<ItemStack> remainders = consumption.remainders();
        List<ItemStack> overflow = new ArrayList<>();
        journal.updateSnapshots(transaction);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int packed = x + y * width;
                int slot = slotAt(x, y);
                ItemStack current = grid.getStackInSlot(slot);
                ItemStack remainder = packed < remainders.size() ? remainders.get(packed) : ItemStack.EMPTY;
                grid.setItemDirect(slot, consumeOne(current, remainder, overflow));
            }
        }
        consumeCrystals(consumption.crystals());
        if (!consumption.wand().isEmpty()) {
            wand.setItemDirect(PartArcaneCraftingTerminal.WAND_SLOT, consumption.wand());
        }
        if (!overflow.isEmpty()) {
            new RootCommitJournal(() -> overflow.forEach(stack -> giveBack(stack))).updateSnapshots(transaction);
        }
        return true;
    }

    /**
     * 消耗表是包围盒打包的（{@code width * height}），不是九格。
     * 逐个对上才算数：网格在这期间被动过就整笔作废，和事务的语义一致。
     */
    private boolean matches(List<ItemStack> expected) {
        if (expected.size() != width * height) {
            return false;
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!ItemStack.matches(grid.getStackInSlot(slotAt(x, y)), expected.get(x + y * width))) {
                    return false;
                }
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
        overflow.add(remainder);
        return left;
    }

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
