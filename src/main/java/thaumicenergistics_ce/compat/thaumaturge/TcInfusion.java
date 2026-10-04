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
 * The infusion altar, asked the four questions this mod has about it.
 *
 * <ul>
 *   <li>{@code BlockEntityInfusionMatrix} is the altar itself. Its numbers leave through
 *       {@link Altar}, so the matrix type does not travel with them.
 *   <li>{@code InfusionStabilitySurvey} is the only thing that knows which blocks break symmetry.
 *   <li>The catalyst stands two blocks below the matrix, on a {@code BlockEntityPedestal}.
 *   <li>{@code TCRecipeTypes.INFUSION} is the type the altar looks its recipes up by.
 * </ul>
 */
public final class TcInfusion {
    private TcInfusion() {}

    /** How far below the matrix the pedestal stands. */
    private static final int PEDESTAL_DROP = 2;

    // -- the altar -----------------------------------------------------------

    /** An altar's live numbers, frozen where they were read. */
    public record Altar(boolean crafting, float stability, @Nullable AspectList remaining) {}

    /** The altar at {@code pos}, or null when that block is not one. */
    public static @Nullable Altar altarAt(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof BlockEntityInfusionMatrix matrix) {
            return new Altar(matrix.isCrafting(), matrix.stability(), matrix.remainingEssentia());
        }
        return null;
    }

    /**
     * The blocks that break the altar's symmetry, or null when the survey has no answer.
     *
     * <p>The survey scans a cube, so callers cache the answer; the copy is made here because the
     * caller holds the list across ticks.
     */
    public static @Nullable List<BlockPos> problemBlocks(Level level, BlockPos matrix) {
        InfusionStabilitySurvey.Result survey = InfusionStabilitySurvey.survey(level, matrix);
        return survey == null ? null : List.copyOf(survey.problemBlocks());
    }

    // -- the catalyst --------------------------------------------------------

    /** The item on the pedestal below an altar, or empty when no pedestal stands there. */
    public static ItemStack catalystUnder(Level level, BlockPos matrix) {
        if (level.getBlockEntity(matrix.below(PEDESTAL_DROP)) instanceof BlockEntityPedestal pedestal) {
            return pedestal.getItem();
        }
        return ItemStack.EMPTY;
    }

    // -- the recipe ----------------------------------------------------------

    /** A recipe, flattened to the numbers this mod prints. */
    public record Recipe(int instability, ItemStack result, @Nullable AspectList aspects) {}

    /**
     * The recipe {@code catalyst} starts, or null when no recipe takes it.
     *
     * <p>The walk is linear and the answer is asked for twice a scan, so callers cache this too.
     */
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
