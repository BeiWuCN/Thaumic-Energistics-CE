package thaumicenergistics_ce.blockentity.assembler;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.storage.IStorageService;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.arcane.ArcanePatternDetails;
import thaumicenergistics_ce.arcane.ThEArcanePattern;
import thaumicenergistics_ce.compat.thaumaturge.TcRegistry;
import thaumicenergistics_ce.util.ThELog;

/**
 * The Arcane Assembler's craft: ticking it, paying for it, waiting on it, and handing the crystals over.
 * Split out of {@link BlockEntityArcaneAssembler}, which keeps the inventory, the grid clock and the
 * public face the menu reads.
 * Reaches the machine directly, as its other helpers do; the craft state it drives stays on the machine,
 * because that is what the save format is written from.
 */
final class AssemblerCraftJob {

    private static final double ACTIVE_POWER = 1.5;
    private static final float MIN_CONSUMPTION_MODIFIER = 0.1F;
    private static final int STALLED_CRAFT_REPORT_TICKS = 100;

    /** Ticks of unbroken stalling after which the craft is finished anyway: a minute. AE2 has no
     * cancellation callback on a provider, and a craft waiting for ever keeps the machine busy. */
    private static final int STALL_RELEASE_TICKS = 1200;

    private final BlockEntityArcaneAssembler owner;

    private boolean awakeForCraft;

    AssemblerCraftJob(BlockEntityArcaneAssembler owner) {
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

    /** The crystals a pattern's craft must be handed, as items the network will be asked for. */
    static List<ItemStack> crystalStacksOf(ThEArcanePattern pattern) {
        List<ItemStack> stacks = new ArrayList<>();
        for (AspectInstance crystal : pattern.crystalItems().entries()) {
            ItemStack stack = TcRegistry.crystalFor(crystal.aspect(), crystal.amount());
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return List.copyOf(stacks);
    }

    /** The vis charged for {@code pattern}, after the gear discount and floored by Thaumaturge's own
     * {@code MIN_CONSUMPTION_MODIFIER}, so an equipped assembler still pays something. */
    public int craftCost(ThEArcanePattern pattern) {
        float modifier = Math.max(1.0F - owner.upgrades.gearDiscount() / 100.0F, MIN_CONSUMPTION_MODIFIER);
        return Math.max(1, (int) Math.ceil(pattern.chargedVis() * modifier));
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
        // Nothing left to run, so the grid may stop ticking this machine.
        updateSleepiness();
    }

    /** Whether the aura can pay for {@code pattern}. An unpayable job is refused, not held: the CPU
     * skips a busy provider, so holding it stalls the plan. A low aura waits - its base can rise. */
    boolean canEverPay(ThEArcanePattern pattern) {
        int capacity = owner.vis.auraCapacity();
        // Zero means the chunk is not initialised yet; a relay counts too, its vis living in a node.
        return capacity <= 0
                || owner.vis.relayNetworkInReach()
                || owner.vis.interfaceInReach()
                || craftCost(pattern) <= capacity;
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

    void noteRefusal(Component why) {
        if (why.equals(owner.craft.lastRefusal())) {
            return;
        }
        owner.craft.setLastRefusal(why);
        // getString() resolves against the server's language; every key carries an English fallback.
        ThELog.LOG.info("[assembler] at {} turned a job away: {}", owner.getBlockPos(), why.getString());
    }

    Component cannotPay(int price) {
        return AssemblerStatus.tooExpensive(price, owner.vis.auraCapacity());
    }

    @Nullable ThEArcanePattern resolveExternal(IPatternDetails details) {
        if (owner.getLevel() == null) {
            return null;
        }
        List<GenericStack> outputs = details.getOutputs();
        if (outputs.size() != 1 || !(outputs.getFirst().what() instanceof AEItemKey outputKey)) {
            return null;
        }
        List<ItemStack> inputs = new ArrayList<>();
        for (IPatternDetails.IInput input : details.getInputs()) {
            GenericStack[] possible = input.getPossibleInputs();
            if (possible.length == 0 || !(possible[0].what() instanceof AEItemKey itemKey)) {
                return null;
            }
            inputs.add(itemKey.toStack((int) Math.min(Integer.MAX_VALUE, possible[0].amount())));
        }
        return ThEArcanePattern.fromEncoded(owner.getLevel(), inputs, outputKey.getReadOnlyStack());
    }

    @Nullable ThEArcanePattern patternForResult(ItemStack result) {
        if (result.isEmpty()) {
            return null;
        }
        if (owner.patternCache.isStale()) {
            // Rebuild without settling the stale flag: a set read before a level would become final.
            owner.patternCache.rebuild();
        }
        for (IPatternDetails details : owner.patternCache.patterns()) {
            if (details instanceof ArcanePatternDetails arcane
                    && ItemStack.isSameItemSameComponents(arcane.pattern().result(), result)) {
                return arcane.pattern();
            }
        }
        return null;
    }

    boolean beginCraft(ThEArcanePattern pattern) {
        // Fixed now, not recomputed at completion, so a craft survives a save without the core: the
        // price and the crystals are handed over with the pattern.
        owner.craft.begin(pattern, craftCost(pattern), crystalStacksOf(pattern));
        owner.displaySync.refreshDisplaySlots(pattern.result().copy(), pattern.grid());
        owner.setChanged();
        owner.displaySync.markForUpdate();
        // Wake the grid: measured, a craft pushed while asleep ticked once a second rather than twenty.
        ICraftingProvider.requestUpdate(owner.mainNode);
        updateSleepiness();
        return true;
    }

    // ------------------------------------------------------------------
    // Repairing an interrupted craft
    // ------------------------------------------------------------------

    /** Finishes recovering a craft that a save interrupted, once there is a level to read the core with:
     * not in {@code loadAdditional}, which runs before the block entity has a level. */
    void recoverInterruptedCraft() {
        if (owner.getLevel() == null || owner.getLevel().isClientSide()) {
            return;
        }
        // Driven by the well: only finishCraft empties it, so a product there means a craft did not finish.
        ItemStack waiting = owner.inventory.getItem(BlockEntityArcaneAssembler.TARGET_SLOT);
        if (!waiting.isEmpty()) {
            owner.craft.setCrafting(true);
            // Recovered only for the preview grid: the price and crystals were saved with the craft.
            ThEArcanePattern recovered = patternForResult(waiting);
            owner.craft.setCurrentPattern(recovered);
            if (recovered != null) {
                // A readable pattern restates both numbers; the saved ones are the fallback.
                owner.craft.setCraftPrice(craftCost(recovered));
                owner.craft.setCraftCrystals(crystalStacksOf(recovered));
            }
            ThELog.LOG.info(
                    "[assembler] at {} resumed the craft a save interrupted: {} for {} vis{}",
                    owner.getBlockPos(),
                    waiting.getHoverName().getString(),
                    owner.craft.craftPrice(),
                    recovered == null ? " (the knowledge core no longer has its pattern)" : "");
            // Deliver on the first tick: the crafting time was served before the save.
            owner.craft.setCraftTicks(owner.upgrades.ticksPerCraft());
            owner.craft.clearStall();
        } else {
            owner.craft.setCrafting(false);
            owner.craft.setCraftTicks(0);
            owner.craft.setCraftPrice(0);
            owner.craft.setCraftCrystals(List.of());
            owner.displaySync.clearDisplay(true);
        }
        // Ask to be ticked rather than assuming a later grid event: with no grid yet this is a no-op.
        updateSleepiness();
    }

    // ------------------------------------------------------------------
    // Taking a job
    // ------------------------------------------------------------------

    /** Why the machine cannot take a job at all, or {@code null} when it can: both entry points ask this
     * first, so a refusal reads the same whichever way AE2 came in. */
    private @Nullable Component refusalFor() {
        if (!owner.acceptsPlans()) {
            return AssemblerStatus.refusalReason(AssemblerStatus.REFUSE_BUSY, "it is already holding a craft");
        }
        if (!owner.mainNode.isActive()) {
            return AssemblerStatus.refusalReason(
                    AssemblerStatus.REFUSE_NODE_INACTIVE, "its grid node is not active");
        }
        return null;
    }

    /** Takes the job a pattern provider on the same grid pushed; the inputs are kept only to give back. */
    boolean accept(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        Component refusal = refusalFor();
        if (refusal != null) {
            noteRefusal(refusal);
            return false;
        }
        if (!(patternDetails instanceof ArcanePatternDetails details)) {
            noteRefusal(AssemblerStatus.refusalReason(
                    AssemblerStatus.REFUSE_NOT_ARCANE, "the pattern is not an arcane pattern this machine can read"));
            return false;
        }
        if (!canEverPay(details.pattern())) {
            noteRefusal(cannotPay(craftCost(details.pattern())));
            return false;
        }
        // What AE2 just extracted. This machine pays in vis and crystals, so keep these only to give back.
        owner.craft.heldInputs().clear();
        for (KeyCounter counter : inputHolder) {
            for (var entry : counter) {
                if (entry.getKey() instanceof AEItemKey itemKey && entry.getLongValue() > 0) {
                    owner.craft.heldInputs()
                            .add(itemKey.toStack((int) Math.min(Integer.MAX_VALUE, entry.getLongValue())));
                }
            }
        }
        return beginCraft(details.pattern());
    }

    /** Takes the job a colocated pattern provider pushed; its inputs were extracted before this point. */
    boolean acceptFromMachine(IPatternDetails patternDetails) {
        Component refusal = refusalFor();
        if (refusal != null) {
            noteRefusal(refusal);
            return false;
        }
        if (patternDetails instanceof ArcanePatternDetails details) {
            if (!canEverPay(details.pattern())) {
                noteRefusal(cannotPay(craftCost(details.pattern())));
                return false;
            }
            return beginCraft(details.pattern());
        }
        ThEArcanePattern resolved = resolveExternal(patternDetails);
        if (resolved == null) {
            noteRefusal(AssemblerStatus.refusalReason(
                    AssemblerStatus.REFUSE_UNRESOLVED, "the pattern does not resolve to an arcane recipe"));
            return false;
        }
        if (!canEverPay(resolved)) {
            noteRefusal(cannotPay(craftCost(resolved)));
            return false;
        }
        return beginCraft(resolved);
    }
}
