package thaumicenergistics_ce.blockentity.assembler;

import thaumicenergistics_ce.config.ThEConfig;

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

final class AssemblerCraftRunner {

    private static final int STALLED_CRAFT_REPORT_TICKS = 100;

    private static final int STALL_RELEASE_TICKS = 1200;

    private final BlockEntityArcaneAssembler owner;

    private boolean awakeForCraft;

    AssemblerCraftRunner(BlockEntityArcaneAssembler owner) {
        this.owner = owner;
    }


    TickRateModulation craftingTick(IGrid grid, int ticksSinceLast) {
        if (!ThEConfig.ASSEMBLER_ENABLED.get()) {
            return TickRateModulation.SAME;
        }
        if (owner.craft.craftTicks() >= owner.upgrades.ticksPerCraft()) {
            return completeCraft(grid);
        }

        IEnergyService energy = grid.getService(IEnergyService.class);
        if (energy != null) {
            double needed = ThEConfig.ASSEMBLER_POWER.get() * ticksSinceLast;
            double extracted = energy.extractAEPower(needed, Actionable.MODULATE, PowerMultiplier.CONFIG);
            if (extracted < needed * 0.9) {
                noteStall(AssemblerStatus.waitReason(AssemblerStatus.WAIT_NO_POWER, "no power"));
                return TickRateModulation.SAME;
            }
        }
        owner.craft.clearStall();
        owner.craft.addCraftTicks(ticksSinceLast);
        owner.displaySync.markDisplayForUpdate();
        return TickRateModulation.URGENT;
    }

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
        boolean unpayableForever = price > 0
                && owner.vis.auraCapacity() > 0
                && price > owner.vis.auraCapacity()
                && !owner.vis.relayNetworkInReach()
                && !owner.vis.interfaceInReach();
        boolean stalledOut = !unpayableForever && owner.craft.stalledTicks() >= STALL_RELEASE_TICKS;
        if (owner.vis.bufferedVis() < price && !unpayableForever && !stalledOut) {
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

        ItemStack output = owner.inventory.getItem(BlockEntityArcaneAssembler.TARGET_SLOT).copy();
        AEItemKey outputKey = AEItemKey.of(output);
        if (outputKey == null) {
            finishCraft();
            return TickRateModulation.IDLE;
        }

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

        if (!hasCrystals(storage)) {
            noteStall(AssemblerStatus.waitReason(AssemblerStatus.WAIT_NO_CRYSTALS_RECHECK, "no crystals (recheck)"));
            return TickRateModulation.SAME;
        }
        takeCrystals(storage);
        storage.getInventory().insert(outputKey, output.getCount(), Actionable.MODULATE, owner.actionSource);
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
        updateSleepiness();
    }

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
