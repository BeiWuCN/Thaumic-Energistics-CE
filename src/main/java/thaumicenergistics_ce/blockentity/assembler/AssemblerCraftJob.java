package thaumicenergistics_ce.blockentity.assembler;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.arcane.ArcanePatternDetails;
import thaumicenergistics_ce.arcane.ThEArcanePattern;
import thaumicenergistics_ce.compat.thaumaturge.TcRegistry;
import thaumicenergistics_ce.util.ThELog;

/**
 * The Arcane Assembler's craft as a thing to be taken, priced and picked back up: what one costs, which
 * crystals it owes, whether the aura can ever pay, and what happens to one a save interrupted.
 * Split out of {@link BlockEntityArcaneAssembler}, which keeps the inventory, the grid clock and the
 * public face the menu reads; {@link AssemblerCraftRunner} runs the craft this class accepts.
 */
final class AssemblerCraftJob {

    private static final float MIN_CONSUMPTION_MODIFIER = 0.1F;

    private final BlockEntityArcaneAssembler owner;

    AssemblerCraftJob(BlockEntityArcaneAssembler owner) {
        this.owner = owner;
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
        owner.craftRunner().updateSleepiness();
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
        owner.craftRunner().updateSleepiness();
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
