package thaumicenergistics_ce.compat.thaumaturge;

import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.content.infusion.BlockEntityInfusionMatrix;
import com.leclowndu93150.thaumaturge.content.infusion.BlockEntityPedestal;
import com.leclowndu93150.thaumaturge.content.infusion.InfusionRecipe;
import com.leclowndu93150.thaumaturge.content.infusion.InfusionStabilitySurvey;
import com.leclowndu93150.thaumaturge.registry.TCRecipeTypes;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * 注魔祭坛，回答本 mod 关于它的四个问题。{@code BlockEntityInfusionMatrix}
 * 就是祭坛，它的数值经 {@link Altar} 传出；{@code InfusionStabilitySurvey} 知道哪些
 * 方块破坏对称性；催化剂立在矩阵下方两格处；配方类型是
 * {@code TCRecipeTypes.INFUSION}。
 */
public final class TcInfusion {
    private TcInfusion() {}

    /** 基座位于矩阵下方多少格。 */
    private static final int PEDESTAL_DROP = 2;

    // -- 祭坛 -----------------------------------------------------------

    /** 祭坛的实时数值，在读取处冻结。 */
    public record Altar(boolean crafting, float stability, @Nullable AspectList remaining) {}

    /** {@code pos} 处的祭坛，该方块不是祭坛时为 null。 */
    public static @Nullable Altar altarAt(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof BlockEntityInfusionMatrix matrix) {
            return new Altar(matrix.isCrafting(), matrix.stability(), matrix.remainingEssentia());
        }
        return null;
    }

    /** 破坏祭坛对称性的方块，勘测无答案时为 null。勘测会扫描
     * 一个立方体，所以调用方会缓存它；副本在此复制，因为调用方要跨 tick 持有。 */
    public static @Nullable List<BlockPos> problemBlocks(Level level, BlockPos matrix) {
        InfusionStabilitySurvey.Result survey = InfusionStabilitySurvey.survey(level, matrix);
        return survey == null ? null : List.copyOf(survey.problemBlocks());
    }

    // -- 催化剂 --------------------------------------------------------

    /** 祭坛下方基座上的物品，该处没有基座时为空。 */
    public static ItemStack catalystUnder(Level level, BlockPos matrix) {
        if (level.getBlockEntity(matrix.below(PEDESTAL_DROP)) instanceof BlockEntityPedestal pedestal) {
            return pedestal.getItem();
        }
        return ItemStack.EMPTY;
    }

    // -- 配方 ----------------------------------------------------------

    /** 一个配方，压平成本 mod 所打印的数值。 */
    public record Recipe(int instability, ItemStack result, @Nullable AspectList aspects) {}

    /** {@code catalyst} 触发的配方，没有配方接受它时为 null。遍历是线性的，
     * 且每次扫描会问两次答案，所以调用方也缓存它。 */
    public static @Nullable Recipe recipeFor(Level level, ItemStack catalyst) {
        if (catalyst.isEmpty()) {
            return null;
        }
        for (var holder : level.getRecipeManager().getAllRecipesFor(TCRecipeTypes.INFUSION.get())) {
            InfusionRecipe recipe = holder.value();
            if (recipe.catalyst().test(catalyst)) {
                return new Recipe(recipe.instability(), recipe.resultItem(), recipe.aspects());
            }
        }
        return null;
    }
}
