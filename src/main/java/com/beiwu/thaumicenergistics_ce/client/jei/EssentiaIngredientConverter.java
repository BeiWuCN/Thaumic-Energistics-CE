package com.beiwu.thaumicenergistics_ce.client.jei;

import appeng.api.stacks.GenericStack;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.compat.jei.ingredient.AspectIngredientType;
import mezz.jei.api.ingredients.IIngredientType;
import net.minecraft.core.Holder;
import org.jspecify.annotations.Nullable;
import com.beiwu.thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverter;

/**
 * 要素 AEKey 与神秘使 JEI 要素配料之间的转换器。
 *
 * <p>AE2 的 JEI 整合把界面上显示的 {@link GenericStack} 转成 JEI 配料，玩家才能按 R/U 查配方；
 * 它自带物品和流体两条，别的 {@code AEKeyType} 不认。注册这一条之后，终端和样板槽里的源质
 * 就等同于 {@link AspectInstance}：按 R 会列出神秘使的「从物品堆提取要素」页和本 mod 的
 * 「从物品提取要素」页，也就是"这份要素能被什么做出来"。
 *
 * <p>和幽灵拖拽是相反方向：那边把 JEI 的要素拖进我们的槽，这边把我们的 key 交给 JEI 查表。
 */
public final class EssentiaIngredientConverter implements IngredientConverter<AspectInstance> {

    public static final EssentiaIngredientConverter INSTANCE = new EssentiaIngredientConverter();

    private EssentiaIngredientConverter() {}

    @Override
    public IIngredientType<AspectInstance> getIngredientType() {
        return AspectIngredientType.INSTANCE;
    }

    @Override
    public @Nullable AspectInstance getIngredientFromStack(GenericStack stack) {
        if (!(stack.what() instanceof AEssentiaKey key)) {
            return null;
        }
        Holder<IAspect> aspect = key.resolveAspect();
        if (aspect == null) {
            return null;
        }
        // AE2 用 amount=0 表示「只关心这一种 key，数量无所谓」——样板槽、幽灵配料、
        // 只读显示的条目都会这么传。AspectInstance 不接受 0（构造器直接抛
        // IllegalArgumentException），所以这里只把数量当成展示值：非正的给它 1，
        // 否则沿用真实数量。JEI 查表只看 aspect，不看这个数字。
        long amount = stack.amount();
        return new AspectInstance(aspect, amount > 0 ? (int) Math.min(Integer.MAX_VALUE, amount) : 1);
    }

    @Override
    public @Nullable GenericStack getStackFromIngredient(AspectInstance ingredient) {
        AEssentiaKey key = AEssentiaKey.of(ingredient.aspect());
        return key == null ? null : new GenericStack(key, ingredient.amount());
    }
}
