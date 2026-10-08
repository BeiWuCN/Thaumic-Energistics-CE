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
 * 跑组装机持有的合成：向网格计费、等 vis、查空间、交付产物，归还机器还拿着的投入物。
 * {@link AssemblerCraftJob} 负责接收任务与定价；节点的休眠开关也在这里。
 */
final class AssemblerCraftRunner {

    private static final double ACTIVE_POWER = 1.5;
    private static final int STALLED_CRAFT_REPORT_TICKS = 100;

    /** 停滞 1200 tick 后直接完成，即一分钟。
     * AE2 供应器没有取消回调，干等的合成会占住机器。 */
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
        // 样板没在核心也无所谓：合成只看产出什么、欠什么。
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
        // 用 URGENT；SAME 是空闲速率，忙起来会慢二十倍。
        owner.displaySync.markDisplayForUpdate();
        return TickRateModulation.URGENT;
    }

    /** 报一次进度，然后接着等；AE2 已经取走原料。
     * @return 恒为 {@code false}，等待不会放弃合成 */
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
        // 这个价格等不到头，除非中继点或接口能引来超过灵气容量的 vis。
        boolean unpayableForever = price > 0
                && owner.vis.auraCapacity() > 0
                && price > owner.vis.auraCapacity()
                && !owner.vis.relayNetworkInReach()
                && !owner.vis.interfaceInReach();
        // 中继点存在却不付款不算承诺，见 STALL_RELEASE_TICKS。
        boolean stalledOut = !unpayableForever && owner.craft.stalledTicks() >= STALL_RELEASE_TICKS;
        if (owner.vis.bufferedVis() < price && !unpayableForever && !stalledOut) {
            // 等 vis；tick 处理器会把缓冲补满。
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
            // 照样交付：AE2 已经取走原料，等待也没超时。
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

        // 产物从产物槽取；做出它的配方这时可能已经读不到了。
        ItemStack output = owner.inventory.getItem(BlockEntityArcaneAssembler.TARGET_SLOT).copy();
        AEItemKey outputKey = AEItemKey.of(output);
        if (outputKey == null) {
            finishCraft();
            return TickRateModulation.IDLE;
        }

        // 晶体不能用 vis 顶替；检查要放在插入结果之前。
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

        // 晶体不能用 vis 顶替；检查要放在插入结果之前。
        if (!hasCrystals(storage)) {
            noteStall(AssemblerStatus.waitReason(AssemblerStatus.WAIT_NO_CRYSTALS_RECHECK, "no crystals (recheck)"));
            return TickRateModulation.SAME;
        }
        takeCrystals(storage);
        storage.getInventory().insert(outputKey, output.getCount(), Actionable.MODULATE, owner.actionSource);
        // 只扣这次合成还欠的那部分，不多扣。
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

    /** 归还已经付过款的投入物，先塞回网络，塞不下再丢地上。 */
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
        // 没有合成可跑了，网格可以停掉这台机器的 tick。
        updateSleepiness();
    }

    /** 有合成就唤醒网格，没有就休眠。
     * AE2 按空闲速率 tick 空闲设备，从存档恢复的合成要靠唤醒才跑得起来。 */
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
