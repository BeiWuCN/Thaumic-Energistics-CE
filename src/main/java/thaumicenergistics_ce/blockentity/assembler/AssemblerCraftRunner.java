package thaumicenergistics_ce.blockentity.assembler;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.storage.IStorageService;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.AEItemKey;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.util.ThELog;

/**
 * 运行组装机持有的合成：为它向网格计费、等待 vis、检查是否有空间、
 * 交付产物，并归还已离开的机器仍持有的物品。从 {@link AssemblerCraftJob} 拆出，
 * 后者负责接收任务并定价；节点的休眠状态也在这里，
 * 因为机器的唤醒与休眠取决于是否有合成在运行。
 */
final class AssemblerCraftRunner {

    private static final double ACTIVE_POWER = 1.5;
    private static final int STALLED_CRAFT_REPORT_TICKS = 100;

    /** 合成持续停滞多少个 tick 后直接完成：一分钟。AE2 在供应器上没有
     * 取消回调，而永远等待的合成会一直占用机器。 */
    private static final int STALL_RELEASE_TICKS = 1200;

    private final BlockEntityArcaneAssembler owner;

    private boolean awakeForCraft;

    AssemblerCraftRunner(BlockEntityArcaneAssembler owner) {
        this.owner = owner;
    }

    // ------------------------------------------------------------------
    // 合成
    // ------------------------------------------------------------------

    TickRateModulation craftingTick(IGrid grid, int ticksSinceLast) {
        // 不检查样板是否缺失：一次合成由它产出什么和欠什么定义。
        if (owner.craft.craftTicks() >= owner.upgrades.ticksPerCraft()) {
            return completeCraft(grid);
        }

        IEnergyService energy = grid.getService(IEnergyService.class);
        if (energy != null) {
            double needed = ACTIVE_POWER * ticksSinceLast;
            double extracted = energy.extractAEPower(needed, Actionable.MODULATE, PowerMultiplier.CONFIG);
            if (extracted < needed * 0.9) {
                noteStall(AssemblerStatus.waitReason(AssemblerStatus.WAIT_NO_POWER, "no power"));
                return TickRateModulation.SAME;
            }
        }
        owner.craft.clearStall();
        owner.craft.addCraftTicks(ticksSinceLast);
        // 用 URGENT 而非 SAME：按空闲速率，忙碌的合成会慢二十倍。
        owner.displaySync.markDisplayForUpdate();
        return TickRateModulation.URGENT;
    }

    /** 只报告一次合成在等待，然后继续等待：AE2 已经提取了原料。
     * @return 恒为 {@code false}：合成不会因为等待被放弃 */
    private boolean noteStall(Component reason) {
        owner.craft.noteStall(reason);
        if (owner.craft.stalledTicks() == STALLED_CRAFT_REPORT_TICKS) {
            ThELog.LOG.info(
                    "[assembler] at {} a craft is waiting for {} ({} ticks so far); it will finish when it"
                            + " can",
                    owner.getBlockPos(),
                    reason.getString(),
                    owner.craft.stalledTicks());
        }
        return false;
    }

    private TickRateModulation completeCraft(IGrid grid) {
        int price = owner.craft.craftPrice();
        // 等待这个价格是永久的，除非中继点或接口能引来灵气容量装不下的 vis。
        boolean unpayableForever = price > 0
                && owner.vis.auraCapacity() > 0
                && price > owner.vis.auraCapacity()
                && !owner.vis.relayNetworkInReach()
                && !owner.vis.interfaceInReach();
        // 存在但永不付款的中继点也不是承诺——见 STALL_RELEASE_TICKS。
        boolean stalledOut = !unpayableForever && owner.craft.stalledTicks() >= STALL_RELEASE_TICKS;
        if (owner.vis.bufferedVis() < price && !unpayableForever && !stalledOut) {
            // 等待 vis；tick 处理器会不断补满缓冲。
            noteStall(AssemblerStatus.waitReason(
                    AssemblerStatus.WAIT_NO_VIS,
                    "no vis (%s banked of %s needed, target %s)",
                    owner.vis.bufferedVis(),
                    price,
                    owner.vis.visTarget(owner.craft.isCrafting(), owner.craft.craftPrice())));
            return TickRateModulation.SAME;
        }
        if (owner.vis.bufferedVis() < price && stalledOut) {
            ThELog.LOG.warn(
                    "[assembler] at {} delivers {} after {} ticks of waiting for {} vis: a machine that waits"
                            + " for ever refuses every later job",
                    owner.getBlockPos(),
                    owner.inventory.getItem(BlockEntityArcaneAssembler.TARGET_SLOT),
                    owner.craft.stalledTicks(),
                    price);
        }
        if (owner.vis.bufferedVis() < price) {
            // 仍然交付，两害相权取其轻：AE2 已经取走原料且等待没有超时。
            ThELog.LOG.info(
                    "[assembler] at {} delivers {} without charging its {} vis: this chunk's aura can never hold"
                            + " more than {}",
                    owner.getBlockPos(),
                    owner.inventory.getItem(BlockEntityArcaneAssembler.TARGET_SLOT),
                    price,
                    owner.vis.auraCapacity());
        }

        IStorageService storage = grid.getService(IStorageService.class);
        if (storage == null) {
            return TickRateModulation.IDLE;
        }

        // 产物来自产物槽：制作它的配方可能已经无法读取。
        ItemStack output = owner.inventory.getItem(BlockEntityArcaneAssembler.TARGET_SLOT).copy();
        AEItemKey outputKey = AEItemKey.of(output);
        if (outputKey == null) {
            finishCraft();
            return TickRateModulation.IDLE;
        }

        // 晶体无法用 vis 替代；在插入结果之前检查，绝不之后。
        if (!hasCrystals(storage)) {
            noteStall(AssemblerStatus.waitReason(AssemblerStatus.WAIT_NO_CRYSTALS, "no crystals"));
            return TickRateModulation.SAME;
        }

        long insertable = storage.getInventory()
                .insert(outputKey, output.getCount(), Actionable.SIMULATE, owner.actionSource);
        if (insertable < output.getCount()) {
            noteStall(
                    AssemblerStatus.waitReason(AssemblerStatus.WAIT_NO_ROOM, "no room for %s", output.getHoverName()));
            return TickRateModulation.SAME;
        }

        // 模拟之后重新检查：下面的提取对晶体来说是不可回退的一步。
        if (!hasCrystals(storage)) {
            noteStall(AssemblerStatus.waitReason(AssemblerStatus.WAIT_NO_CRYSTALS_RECHECK, "no crystals (recheck)"));
            return TickRateModulation.SAME;
        }
        takeCrystals(storage);
        storage.getInventory().insert(outputKey, output.getCount(), Actionable.MODULATE, owner.actionSource);
        // 合成仍然欠的部分，不多取。
        owner.vis.spendVis(price);
        finishCraft();
        return TickRateModulation.URGENT;
    }

    private boolean hasCrystals(IStorageService storage) {
        for (ItemStack stack : owner.craft.craftCrystals()) {
            AEItemKey key = AEItemKey.of(stack);
            if (key == null) {
                return false;
            }
            long available = storage.getInventory()
                    .extract(key, stack.getCount(), Actionable.SIMULATE, owner.actionSource);
            if (available < stack.getCount()) {
                return false;
            }
        }
        return true;
    }

    private void takeCrystals(IStorageService storage) {
        for (ItemStack stack : owner.craft.craftCrystals()) {
            AEItemKey key = AEItemKey.of(stack);
            if (key != null) {
                storage.getInventory()
                        .extract(key, stack.getCount(), Actionable.MODULATE, owner.actionSource);
            }
        }
    }

    /** 归还合成已经付过款的投入物，先给网络，再给地面。 */
    void returnHeldInputs() {
        if (owner.craft.heldInputs().isEmpty()) {
            return;
        }
        IStorageService storage = null;
        IGrid grid = gridOrNull();
        if (grid != null) {
            storage = grid.getService(IStorageService.class);
        }
        for (ItemStack stack : owner.craft.heldInputs()) {
            if (stack.isEmpty()) {
                continue;
            }
            AEItemKey key = AEItemKey.of(stack);
            long left = stack.getCount();
            if (storage != null && key != null) {
                long inserted = storage.getInventory()
                        .insert(key, stack.getCount(), Actionable.MODULATE, owner.actionSource);
                left -= inserted;
            }
            if (left > 0) {
                Containers.dropItemStack(
                        owner.getLevel(),
                        owner.getBlockPos().getX(),
                        owner.getBlockPos().getY(),
                        owner.getBlockPos().getZ(),
                        stack.copyWithCount((int) left));
            }
        }
        owner.craft.heldInputs().clear();
    }

    private appeng.api.networking.@Nullable IGrid gridOrNull() {
        return owner.mainNode == null ? null : owner.mainNode.getGrid();
    }

    void finishCraft() {
        owner.craft.reset();
        owner.displaySync.clearDisplay(false);
        owner.setChanged();
        owner.displaySync.markForUpdate();
        // 已无合成可运行，所以网格可以停止 tick 这台机器。
        updateSleepiness();
    }

    /** 持有合成时唤醒网格的 tick，没有时让它休眠：从存档恢复的合成
     * 否则永远不会运行，因为 AE2 按空闲速率 tick 空闲设备。 */
    void updateSleepiness() {
        if (owner.getLevel() == null || owner.getLevel().isClientSide() || awakeForCraft == owner.craft.isCrafting()) {
            return;
        }
        IGrid grid = gridOrNull();
        IGridNode node = owner.mainNode.getNode();
        if (grid == null || node == null) {
            return;
        }
        awakeForCraft = owner.craft.isCrafting();
        if (owner.craft.isCrafting()) {
            grid.getTickManager().wakeDevice(node);
        } else {
            grid.getTickManager().sleepDevice(node);
        }
    }
}
