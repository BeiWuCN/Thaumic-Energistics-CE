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
import thaumicenergistics_ce.compat.thaumaturge.TcRegistry;

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

    public static @Nullable ArcanePatternDetails of(ThEArcanePattern pattern, HolderLookup.Provider registries) {
        return of(pattern, registries, null);
    }

    public static @Nullable ArcanePatternDetails of(
            ThEArcanePattern pattern, HolderLookup.Provider registries, @Nullable Consumer<String> refusal) {
        return of(pattern, registries, refusal, null);
    }

    public static @Nullable ArcanePatternDetails of(
            ThEArcanePattern pattern,
            HolderLookup.Provider registries,
            @Nullable Consumer<String> refusal,
            @Nullable AEItemKey decodedDefinition) {
        AEItemKey definition =
                decodedDefinition != null ? decodedDefinition : AEItemKey.of(pattern.toItem(registries));
        if (definition == null) {
            refuse(refusal, "its result has no AE2 item key");
            return null;
        }

        List<IInput> inputs = new ArrayList<>();
        int cell = 0;
        for (ItemStack ignored : pattern.grid()) {
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
        for (AspectInstance crystal : pattern.crystalItems().entries()) {
            ItemStack stack = TcRegistry.crystalFor(crystal.aspect(), crystal.amount());
            if (stack.isEmpty()) {
                refuse(refusal, "the crystal " + crystal.aspect().getKey().identifier() + " has no crystal item");
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
            return null;
        }
    }
}
