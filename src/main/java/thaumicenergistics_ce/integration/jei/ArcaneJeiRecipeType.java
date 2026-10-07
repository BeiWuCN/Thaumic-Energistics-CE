package thaumicenergistics_ce.integration.jei;

import com.leclowndu93150.thaumaturge.compat.jei.category.ArcaneWorkbenchCategory;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Thaumaturge 的奥术工作台配方类型，借用不镜像：另开一页会和玩家看的那一页不一致。
 * 放在 [ArcaneRecipeTypes] 之外：只有 JEI 会索取它，而另一个 mod 的 JEI 类正是
 * 专用服务端绝不能碰的东西。
 * 类型在方法内解析，不存静态字段：JEI 加载前这里不会被读到，
 * 类加载时也就不会碰到 JEI 的类。
 */
final class ArcaneJeiRecipeType {

    private ArcaneJeiRecipeType() {}

    /** Thaumaturge 自己的工作台配方类型。 */
    static RecipeType<RecipeHolder<?>> arcane() {
        RecipeType<?> type = ArcaneWorkbenchCategory.RECIPE_TYPE;
        @SuppressWarnings("unchecked")
        RecipeType<RecipeHolder<?>> cast = (RecipeType<RecipeHolder<?>>) (RecipeType<?>) type;
        return cast;
    }
}
