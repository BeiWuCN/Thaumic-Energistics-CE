package thaumicenergistics_ce.integration.jei;

import com.leclowndu93150.thaumaturge.compat.jei.category.CrucibleCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Thaumaturge 的坩埚配方类型，借用不镜像：另开一页会和玩家看的那一页不一致。
 * 和 [ArcaneJeiRecipeType] 分成两个类：类别不同，类型就不同，合成一个字段会让两个类别互相顶掉。
 * 类型在方法内解析，不存静态字段：JEI 加载前这里不会被读到，
 * 类加载时也就不会碰到 JEI 的类。
 */
final class CrucibleJeiRecipeType {

    private CrucibleJeiRecipeType() {}

    /** Thaumaturge 自己的坩埚配方类型。 */
    static IRecipeType<RecipeHolder<?>> crucible() {
        IRecipeType<?> type = CrucibleCategory.RECIPE_TYPE;
        @SuppressWarnings("unchecked")
        IRecipeType<RecipeHolder<?>> cast = (IRecipeType<RecipeHolder<?>>) (IRecipeType<?>) type;
        return cast;
    }
}
