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
 * Runs the craft the assembler is holding: charging the grid for it, waiting for vis, checking there is
 * room, handing the product over, and giving back what a machine that went away was still holding.
 * Split out of {@link AssemblerCraftJob}, which takes a job and prices it; the node's sleep state lives
 * here too, because waking and sleeping the machine follows whether a craft is running at all.
 */
final class AssemblerCraftRunner {

    private static final double ACTIVE_POWER = 1.5;
    private static final int STALLED_CRAFT_REPORT_TICKS = 100;

    /** Ticks of unbroken stalling after which the craft is finished anyway: a minute. AE2 has no
     * cancellation callback on a provider, and a craft waiting for ever keeps the machine busy. */
    private static final int STALL_RELEASE_TICKS = 1200;

    private final BlockEntityArcaneAssembler owner;

    private boolean awakeForCraft;

    AssemblerCraftRunner(BlockEntityArcaneAssembler owner) {
        this.owner = owner;
    }

    // ------------------------------------------------------------------
    // Crafting
    // ------------------------------------------------------------------

    TickRateModulation craftingTick(IGrid grid, int ticksSinceLast) {
        // No test for a missing pattern: a craft is defined by what it produces and what it owes.
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
        // URGENT, not SAME: at the idle rate a busy craft runs twenty times too slow.
        owner.displaySync.markDisplayForUpdate();
        return TickRateModulation.URGENT;
    }

    /** Reports once that a craft is waiting, then keeps waiting: AE2 already extracted the ingredients.
     * @return always {@code false}: a craft is never abandoned for waiting */
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
        // Waiting for this price is forever unless a relay or interface reaches vis the aura cannot hold.
        boolean unpayableForever = price > 0
                && owner.vis.auraCapacity() > 0
                && price > owner.vis.auraCapacity()
                && !owner.vis.relayNetworkInReach()
                && !owner.vis.interfaceInReach();
        // A relay that exists but never pays is not a promise either - see STALL_RELEASE_TICKS.
        boolean stalledOut = !unpayableForever && owner.craft.stalledTicks() >= STALL_RELEASE_TICKS;
        if (owner.vis.bufferedVis() < price && !unpayableForever && !stalledOut) {
            // Waiting on vis; the tick handler keeps refilling the buffer.
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
            // Delivered anyway, the lesser evil: AE2 already took the ingredients and waits with no timeout.
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

        // The product comes from the well: the recipe that made it may no longer be readable.
        ItemStack output = owner.inventory.getItem(BlockEntityArcaneAssembler.TARGET_SLOT).copy();
        AEItemKey outputKey = AEItemKey.of(output);
        if (outputKey == null) {
            finishCraft();
            return TickRateModulation.IDLE;
        }

        // Crystals vis cannot stand in for; checked before the result is inserted, never after.
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

        // Re-check after the simulate: the extraction below is the point of no return for the crystals.
        if (!hasCrystals(storage)) {
            noteStall(AssemblerStatus.waitReason(AssemblerStatus.WAIT_NO_CRYSTALS_RECHECK, "no crystals (recheck)"));
            return TickRateModulation.SAME;
        }
        takeCrystals(storage);
        storage.getInventory().insert(outputKey, output.getCount(), Actionable.MODULATE, owner.actionSource);
        // What the craft still owed, and no more.
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

    /** Gives back the inputs a craft already paid for, to the network first and to the ground after. */
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
        // Nothing left to run, so the grid may stop ticking this machine.
        updateSleepiness();
    }

    /** Wakes the grid's tick while a craft is held, and lets it sleep when not: a craft restored from a
     * save never runs otherwise, as AE2 ticks idle devices at the idle rate. */
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
