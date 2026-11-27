package com.beiwu.thaumicenergistics_ce.recipe.arcane;

import appeng.api.config.Actionable;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.util.Platform;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneCraftingStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import com.leclowndu93150.thaumaturge.api.transaction.RootCommitJournal;
import com.leclowndu93150.thaumaturge.api.transaction.SnapshotJournal;
import com.leclowndu93150.thaumaturge.api.transaction.TransactionContext;
import com.beiwu.thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

public final class TerminalArcaneCraftingStore implements IArcaneCraftingStore {

    /** 网格固定三格宽；包围盒坐标映射回槽位时按这个步长走。 */
    private static final int GRID_WIDTH = 3;

    private final InternalInventory grid;
    private final InternalInventory crystals;
    private final InternalInventory wand;
    private final Player player;

    /**
     * 无缝补货的材料来源：合成时先从 ME 网络抽材料，网格槽保持原样。
     * 为空时不走网络（例如没有对应存储的纯工作台场景）。
     */
    private final MEStorage storage;
    private final IEnergySource networkEnergy;
    private final IActionSource actionSource;

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
        this(grid, crystals, wand, player, 0, 0, GRID_WIDTH, GRID_WIDTH, null, null, null);
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
        this(grid, crystals, wand, player, left, top, width, height, null, null, null);
    }

    /**
     * 带无缝补货的版本：storage 非空时，合成材料优先从 ME 网络抽取，网格槽不消耗。
     */
    public TerminalArcaneCraftingStore(
            InternalInventory grid,
            InternalInventory crystals,
            InternalInventory wand,
            Player player,
            int left,
            int top,
            int width,
            int height,
            @Nullable MEStorage storage,
            @Nullable IEnergySource networkEnergy,
            @Nullable IActionSource actionSource) {
        this.grid = grid;
        this.crystals = crystals;
        this.wand = wand;
        this.player = player;
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
        this.storage = storage;
        this.networkEnergy = networkEnergy;
        this.actionSource = actionSource;
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
                if (current.isEmpty()) {
                    continue;
                }
                // 三档优先级：先吃网格里多于一个模板的那份；那份吃完才动 ME 网络；
                // 网络也供不上时才动模板本身，尽量保住玩家摆好的配方形状。
                if (current.getCount() > 1) {
                    // 1) 本地溢出：扣一个后仍留至少一个，形状与库存都在。
                    grid.setItemDirect(slot, consumeOne(current, remainder, overflow));
                } else if (withdrawFromNetwork(current)) {
                    // 2) 恰好一网格且网络有货：扣网络，槽位原样不动。
                    // 不能写回从网络取的那一个 —— 玩家手动堆的 N 个会被替换成 1 个，吞掉东西。
                    if (!remainder.isEmpty()) {
                        overflow.add(remainder);
                    }
                } else {
                    // 3) 恰好一网格且网络备货不足一个：混合，动用网格里这最后一份。
                    grid.setItemDirect(slot, consumeOne(current, remainder, overflow));
                }
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

    /** 从 ME 网络抽走一个与模板相同的物品。抽到返回 {@code true}，没货、断电或没电返回 {@code false}。 */
    private boolean withdrawFromNetwork(ItemStack template) {
        if (storage == null || actionSource == null || networkEnergy == null) {
            return false;
        }
        AEItemKey key = AEItemKey.of(template);
        if (key == null) {
            return false;
        }
        if (networkEnergy.extractAEPower(1, Actionable.SIMULATE, appeng.api.config.PowerMultiplier.CONFIG) < 0.9) {
            return false;
        }
        if (storage.extract(key, 1, Actionable.MODULATE, actionSource) < 1) {
            return false;
        }
        networkEnergy.extractAEPower(1, Actionable.MODULATE, appeng.api.config.PowerMultiplier.CONFIG);
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
            // 用 long 累加：创造元件报 Integer.MAX_VALUE（2.1G）库存，本地哪怕只有 1 颗，
            // int 相加就溢出成负数，被误判成「晶体不足」，consume 返回 false
            // 被 Thaumaturge 报成 INGREDIENTS_CHANGED（材料变化）。
            long found = 0;
            for (int slot = 0; slot < PartArcaneCraftingTerminal.CRYSTAL_SLOTS; slot++) {
                Holder<IAspect> carried = EssentiaCrystals.aspectOf(crystals.getStackInSlot(slot));
                if (carried != null && carried.equals(aspect)) {
                    found += crystals.getStackInSlot(slot).getCount();
                }
            }
            // 晶体优先或只能用碎片时，网络库存也算可供的一部分。
            found += networkCrystalsAvailable(aspect);
            if (found < needed.amountOf(aspect)) {
                return false;
            }
        }
        return true;
    }

    /** 网络里这个要素的碎片数量；没网或断电返回 0，与成本事件的口径一致。 */
    private int networkCrystalsAvailable(Holder<IAspect> aspect) {
        if (storage == null || actionSource == null || networkEnergy == null) {
            return 0;
        }
        if (networkEnergy.extractAEPower(1, Actionable.SIMULATE, appeng.api.config.PowerMultiplier.CONFIG) < 0.9) {
            return 0;
        }
        AEItemKey key = AEItemKey.of(EssentiaCrystals.create(aspect, 1));
        if (key == null) {
            return 0;
        }
        long available = storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, actionSource);
        // 网络库存是 long，直接转 int 在超过 21 亿时会变负数，让可用性判断出错。
        return (int) Math.min(Integer.MAX_VALUE, available);
    }

    private void consumeCrystals(AspectList needed) {
        for (Holder<IAspect> aspect : needed.aspects()) {
            int required = needed.amountOf(aspect);
            int local = countLocalCrystals(aspect);
            if (local > required) {
                // 1) 本地溢出：只吃多于一份模板的那部分，留下的仍够摆一格配方。
                takeLocalCrystals(aspect, required);
                continue;
            }
            // 2) 本地恰好一份（或不足）：先吃 ME 网络，槽位原样不动。
            int fromNetwork = 0;
            if (storage != null && actionSource != null && networkEnergy != null) {
                AEItemKey key = AEItemKey.of(EssentiaCrystals.create(aspect, required));
                if (key != null
                        && networkEnergy.extractAEPower(1, Actionable.SIMULATE,
                                appeng.api.config.PowerMultiplier.CONFIG) >= 0.9) {
                    long got = storage.extract(key, required, Actionable.MODULATE, actionSource);
                    if (got > 0) {
                        networkEnergy.extractAEPower(1, Actionable.MODULATE,
                                appeng.api.config.PowerMultiplier.CONFIG);
                        fromNetwork = (int) got;
                    }
                }
            }
            // 3) 网络备货不足一份：差额动用本地这最后一份，和网格材料同一套混合取向。
            int shortfall = required - fromNetwork;
            if (shortfall > 0) {
                takeLocalCrystals(aspect, shortfall);
            }
        }
    }

    /** 六个晶体槽里这个要素一共几颗。 */
    private int countLocalCrystals(Holder<IAspect> aspect) {
        int total = 0;
        for (int slot = 0; slot < PartArcaneCraftingTerminal.CRYSTAL_SLOTS; slot++) {
            Holder<IAspect> carried = EssentiaCrystals.aspectOf(crystals.getStackInSlot(slot));
            if (carried != null && carried.equals(aspect)) {
                total += crystals.getStackInSlot(slot).getCount();
            }
        }
        return total;
    }

    /** 从晶体槽扣掉至多 {@code amount} 颗，够扣就扣、不够就把槽掏空。 */
    private void takeLocalCrystals(Holder<IAspect> aspect, int amount) {
        int outstanding = amount;
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
