package thaumicenergistics_ce.integration.jei;

import appeng.api.stacks.GenericStack;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.content.recipe.crucible.CrucibleRecipe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;

/**
 * 把坩埚配方读成处理样板的输入与输出，一个 JEI 类型都不点。
 * 专用服务端没有 JEI 可借，配方本身却读得到；那是 [CrucibleJeiRecipeType] 的事。
 * 要素写成源质是刻意的：坩埚要的是哪几种要素、各要多少，样板里不写，网络就无从备料。
 */
public final class CrucibleRecipeTypes {

    private CrucibleRecipeTypes() {}

    /** 这条配方是不是坩埚配方。 */
    public static boolean isCrucible(RecipeHolder<?> holder) {
        return holder.value() instanceof CrucibleRecipe;
    }

    /**
     * 处理样板的输入格：第一格是催化剂的各个变体，之后是一个要素一格。
     * 催化剂读不出任何物品时返回 null——那是编码不了，不是“没有输入”。
     */
    public static @Nullable List<List<GenericStack>> inputsFor(RecipeHolder<?> holder) {
        if (!(holder.value() instanceof CrucibleRecipe recipe)) {
            return null;
        }
        List<GenericStack> catalyst = new ArrayList<>();
        for (ItemStack variant : recipe.catalyst().getItems()) {
            GenericStack stack = GenericStack.fromItemStack(variant);
            if (stack != null) {
                catalyst.add(stack);
            }
        }
        if (catalyst.isEmpty()) {
            return null;
        }
        List<List<GenericStack>> inputs = new ArrayList<>();
        inputs.add(catalyst);
        for (AspectInstance entry : recipe.aspects().entries()) {
            inputs.add(List.of(new GenericStack(AEssentiaKey.of(entry.aspect()), entry.amount())));
        }
        return inputs;
    }

    /**
     * 处理样板的输出格：配方的结果物品。
     * 用 rawResult 而不是 getResultItem：要素水晶这类结果靠数据组件区分，原始栈才带得住。
     */
    public static @Nullable List<GenericStack> outputsFor(RecipeHolder<?> holder) {
        if (!(holder.value() instanceof CrucibleRecipe recipe)) {
            return null;
        }
        GenericStack result = GenericStack.fromItemStack(recipe.rawResult());
        return result == null ? null : List.of(result);
    }
}
