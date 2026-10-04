package thaumicenergistics_ce.arcane;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.arcane.ThEArcanePattern;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;
import thaumicenergistics_ce.compat.thaumaturge.TcRegistry;

/**
 * Adapts a {@link ThEArcanePattern} to AE2's crafting API.
 * <ul>
 *   <li>Each non-empty grid cell becomes one input, plus any crystal vis cannot pay for.
 *   <li>Vis is not mapped to a synthetic AE2 ingredient: it comes from the aura at craft time.
 *   <li>A primal crystal is paid by vis and stays hidden; a compound one has no vis value, so it appears.
 * </ul>
 */
public final class ArcanePatternDetails implements IPatternDetails {

    private final ThEArcanePattern pattern;
    private final AEItemKey definition;
    private final IInput[] inputs;
    private final List<GenericStack> outputs;

    private ArcanePatternDetails(ThEArcanePattern pattern, AEItemKey definition, IInput[] inputs) {
        this.pattern = pattern;
        this.definition = definition;
        this.inputs = inputs;
        this.outputs = List.of(new GenericStack(AEItemKey.of(pattern.result()), pattern.result().getCount()));
    }

    /** The AE2 view of an arcane pattern, or {@code null} when it has no usable input or output. */
    public static @Nullable ArcanePatternDetails of(ThEArcanePattern pattern, HolderLookup.Provider registries) {
        return of(pattern, registries, null);
    }

    /**
     * As {@link #of}, but reports why a pattern was refused: otherwise it is simply absent from
     * {@link BlockEntityArcaneAssembler#getAvailablePatterns()}, which looks the same as a pattern
     * not recognised after a reload.
     *
     * @param refusal when non-null, receives one short reason if the pattern is refused
     */
    public static @Nullable ArcanePatternDetails of(
            ThEArcanePattern pattern, HolderLookup.Provider registries, @Nullable Consumer<String> refusal) {
        return of(pattern, registries, refusal, null);
    }

    /**
     * As {@link #of}, but with the definition supplied rather than rebuilt: {@code IPatternDetails} equality
     * is defined on it, and a rebuilt key need not satisfy {@code save(load(tag)) == tag} to match a machine.
     *
     * @param decodedDefinition the exact key the pattern was decoded from, or {@code null} to build one
     */
    public static @Nullable ArcanePatternDetails of(
            ThEArcanePattern pattern,
            HolderLookup.Provider registries,
            @Nullable Consumer<String> refusal,
            @Nullable AEItemKey decodedDefinition) {
        // The definition is the *pattern item*, not the result: decodePattern only answers an
        // encoded pattern item; with a result it returns null and ExecutingCraftingJob drops it.
        AEItemKey definition =
                decodedDefinition != null ? decodedDefinition : AEItemKey.of(pattern.toItem(registries));
        if (definition == null) {
            refuse(refusal, "its result has no AE2 item key");
            return null;
        }

        List<IInput> inputs = new ArrayList<>();
        int cell = 0;
        for (ItemStack ignored : pattern.grid()) {
            // Every item the cell will accept, not just the one it displays: a cell written against a tag
            // accepts any member of it.
            List<GenericStack> choices = new ArrayList<>();
            for (ItemStack option : pattern.cellChoices(cell)) {
                AEItemKey key = AEItemKey.of(option);
                if (key != null) {
                    choices.add(new GenericStack(key, Math.max(1, option.getCount())));
                }
            }
            cell++;
            if (!choices.isEmpty()) {
                inputs.add(new ItemChoicesInput(List.copyOf(choices)));
            }
        }
        // Crystals the assembler cannot pay for with vis; the count is the recipe's requirement.
        for (AspectInstance crystal : pattern.crystalItems().entries()) {
            ItemStack stack = TcRegistry.crystalFor(crystal.aspect(), crystal.amount());
            if (stack.isEmpty()) {
                // No crystal item for this aspect, so the recipe cannot be automated at all.
                refuse(refusal, "the crystal " + crystal.aspect().getKey().location() + " has no crystal item");
                return null;
            }
            inputs.add(new ItemChoicesInput(
                    List.of(new GenericStack(AEItemKey.of(stack), crystal.amount()))));
        }
        if (inputs.isEmpty()) {
            refuse(refusal, "it has no usable inputs");
            return null;
        }
        return new ArcanePatternDetails(pattern, definition, inputs.toArray(new IInput[0]));
    }

    private static void refuse(@Nullable Consumer<String> refusal, String reason) {
        if (refusal != null) {
            refusal.accept(reason);
        }
    }

    public ThEArcanePattern pattern() {
        return pattern;
    }

    @Override
    public AEItemKey getDefinition() {
        return definition;
    }

    @Override
    public IInput[] getInputs() {
        return inputs;
    }

    @Override
    public List<GenericStack> getOutputs() {
        return outputs;
    }

    @Override
    public boolean supportsPushInputsToExternalInventory() {
        // The assembler consumes the inputs itself; nothing is forwarded to a neighbour.
        return false;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ArcanePatternDetails details && definition.equals(details.definition);
    }

    @Override
    public int hashCode() {
        return definition.hashCode();
    }

    /**
     * An input satisfied by any one of a set of item keys, the first being the one the AE2 view shows.
     * <ul>
     *   <li>More than one key is what an ore dictionary entry means: any member of the tag will do.
     *   <li>AE2 matches a pushed stack with {@link #isValid} and sends from {@link #getPossibleInputs()}.
     * </ul>
     */
    private record ItemChoicesInput(List<GenericStack> choices) implements IInput {

        @Override
        public GenericStack[] getPossibleInputs() {
            return choices.toArray(new GenericStack[0]);
        }

        @Override
        public long getMultiplier() {
            return 1;
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            if (!(input instanceof AEItemKey itemKey)) {
                return false;
            }
            for (GenericStack choice : choices) {
                if (itemKey.equals(choice.what())) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public @Nullable AEKey getRemainingKey(AEKey template) {
            // Inputs are fully consumed; no container is returned to the network.
            return null;
        }
    }
}
