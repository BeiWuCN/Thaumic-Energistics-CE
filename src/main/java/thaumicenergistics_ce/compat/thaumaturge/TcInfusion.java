package thaumicenergistics_ce.compat.thaumaturge;

import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.content.infusion.BlockEntityInfusionMatrix;
import com.leclowndu93150.thaumaturge.content.infusion.BlockEntityPedestal;
import com.leclowndu93150.thaumaturge.content.infusion.InfusionRecipe;
import com.leclowndu93150.thaumaturge.content.infusion.InfusionStabilitySurvey;
import com.leclowndu93150.thaumaturge.registry.TTRecipeTypes;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public final class TcInfusion {
    private TcInfusion() {}

    private static final int PEDESTAL_DROP = 2;


    public record Altar(boolean crafting, float stability, @Nullable AspectList remaining) {}

    public static @Nullable Altar altarAt(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof BlockEntityInfusionMatrix matrix) {
            return new Altar(matrix.isCrafting(), matrix.stability(), matrix.remainingEssentia());
        }
        return null;
    }

    public static @Nullable List<BlockPos> problemBlocks(Level level, BlockPos matrix) {
        InfusionStabilitySurvey.Result survey = InfusionStabilitySurvey.survey(level, matrix);
        return survey == null ? null : List.copyOf(survey.problemBlocks());
    }


    public static ItemStack catalystUnder(Level level, BlockPos matrix) {
        if (level.getBlockEntity(matrix.below(PEDESTAL_DROP)) instanceof BlockEntityPedestal pedestal) {
            return pedestal.getItem();
        }
        return ItemStack.EMPTY;
    }


    public record Recipe(int instability, ItemStack result, @Nullable AspectList aspects) {}

    public static @Nullable Recipe recipeFor(Level level, ItemStack catalyst) {
        if (catalyst.isEmpty()) {
            return null;
        }
        for (RecipeHolder<InfusionRecipe> holder :
                level.getServer().getRecipeManager().recipeMap().byType(TTRecipeTypes.INFUSION.get())) {
            InfusionRecipe recipe = holder.value();
            if (recipe.catalyst().test(catalyst)) {
                return new Recipe(recipe.instability(), recipe.resultItem(), recipe.aspects());
            }
        }
        return null;
    }
}
