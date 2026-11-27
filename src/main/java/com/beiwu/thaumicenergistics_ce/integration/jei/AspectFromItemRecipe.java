package com.beiwu.thaumicenergistics_ce.integration.jei;

import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * JEI 的「从物品提取要素」页：一件物品，以及它蒸馏时能给出的要素与数量。
 *
 * <p>和神秘使本体那张「从物品堆提取要素」正好对调：那边按要素归组、列出含有它的物品，
 * 这边按物品归组、列出它能提出的要素。这样一页就是一条处理样板的原料与产物，
 * 可以直接转进 ME 样板编码终端的处理编码页。
 *
 * @param source 蒸馏的原料
 * @param aspects 蒸馏产物，数量与要素蒸馏编码器算出来的同一套
 */
public record AspectFromItemRecipe(ItemStack source, List<AspectInstance> aspects) {
}
