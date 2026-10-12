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
import thaumicenergistics_ce.recipe.arcane.ArcanePatternDetails;
import thaumicenergistics_ce.recipe.arcane.ThEArcanePattern;
import thaumicenergistics_ce.compat.thaumaturge.TcRegistry;

final class AssemblerCraftJob {

    private static final float MIN_CONSUMPTION_MODIFIER = 0.1F;

    private final BlockEntityArcaneAssembler owner;

    AssemblerCraftJob(BlockEntityArcaneAssembler owner) {
        this.owner = owner;
    }

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

    public int craftCost(ThEArcanePattern pattern) {
        float modifier = Math.max(1.0F - owner.upgrades.gearDiscount() / 100.0F, MIN_CONSUMPTION_MODIFIER);
        return Math.max(1, (int) Math.ceil(pattern.chargedVis() * modifier));
    }

    boolean canEverPay(ThEArcanePattern pattern) {
        if (craftCost(pattern) > AssemblerVisPool.IDLE_TARGET) {
            return false;
        }
        int capacity = owner.vis.auraCapacity();
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
    }

    Component cannotPay(int price) {
        int capacity = owner.vis.auraCapacity();
        return AssemblerStatus.tooExpensive(price,
                capacity <= 0 ? AssemblerVisPool.IDLE_TARGET : Math.min(AssemblerVisPool.IDLE_TARGET, capacity));
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
        owner.craft.begin(pattern, craftCost(pattern), crystalStacksOf(pattern));
        owner.displaySync.refreshDisplaySlots(pattern.result().copy(), pattern.grid());
        owner.setChanged();
        owner.displaySync.markForUpdate();
        ICraftingProvider.requestUpdate(owner.mainNode);
        owner.craftRunner().updateSleepiness();
        return true;
    }


    void recoverInterruptedCraft() {
        if (owner.getLevel() == null || owner.getLevel().isClientSide()) {
            return;
        }
        ItemStack waiting = owner.inventory.getItem(BlockEntityArcaneAssembler.TARGET_SLOT);
        if (!waiting.isEmpty()) {
            owner.craft.setCrafting(true);
            ThEArcanePattern recovered = patternForResult(waiting);
            owner.craft.setCurrentPattern(recovered);
            if (recovered != null) {
                owner.craft.setCraftPrice(craftCost(recovered));
                owner.craft.setCraftCrystals(crystalStacksOf(recovered));
            }
            owner.craft.setCraftTicks(owner.upgrades.ticksPerCraft());
            owner.craft.clearStall();
        } else {
            owner.craft.setCrafting(false);
            owner.craft.setCraftTicks(0);
            owner.craft.setCraftPrice(0);
            owner.craft.setCraftCrystals(List.of());
            owner.displaySync.clearDisplay(true);
        }
        owner.craftRunner().updateSleepiness();
    }


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
