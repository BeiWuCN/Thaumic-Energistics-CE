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

/**
 * 把 {@link ThEArcanePattern} 适配到 AE2 的合成 API。
 * 每个非空网格格位成为一个输入，再加上 vis 支付不了的那些晶体；
 * vis 本身不映射成 AE2 合成材料，它在合成时来自灵气。
 * 元初晶体由 vis 支付，不出现在输入里；复合晶体没有 vis 价值，会作为输入出现。
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

    public static @Nullable ArcanePatternDetails of(ThEArcanePattern pattern, HolderLookup.Provider registries) {
        return of(pattern, registries, null);
    }

    public static @Nullable ArcanePatternDetails of(
            ThEArcanePattern pattern, HolderLookup.Provider registries, @Nullable Consumer<String> refusal) {
        return of(pattern, registries, refusal, null);
    }

    /**
     * 同 {@link #of}，但定义由外部传入，不重建：{@code IPatternDetails} 的相等性定义在它上面，
     * 重建出的 key 未必满足 {@code save(load(tag)) == tag}，就匹配不上机器。
     * @param decodedDefinition 样板解码时的原始 key，传 {@code null} 则自行构建一个
     */
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
            // 格位接受的全部物品，不只是它显示的那一个：
            // 按标签写入的格位接受该标签的任意成员。
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
        // 组装机没法用 vis 支付的晶体；数量就是配方的需求。
        for (AspectInstance crystal : pattern.crystalItems().entries()) {
            ItemStack stack = TcRegistry.crystalFor(crystal.aspect(), crystal.amount());
            if (stack.isEmpty()) {
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
     * 由一组物品 key 里任意一个即可满足的输入，第一个是 AE2 视图显示的那个。
     * 多个 key 正对应矿典条目的含义：该标签的任意成员都可以。
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
            return null;
        }
    }
}
