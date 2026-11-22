package com.beiwu.thaumicenergistics_ce.integration.jei;

import com.leclowndu93150.thaumaturge.compat.jei.category.CrucibleCategory;
import com.leclowndu93150.thaumaturge.compat.jei.category.InfusionCategory;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.world.item.crafting.RecipeHolder;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import net.minecraft.resources.Identifier;
import com.beiwu.thaumicenergistics_ce.ThEIds;
import com.beiwu.thaumicenergistics_ce.registry.TECEItems;

/**
 * Thaumic Energistics 的 JEI 插件，配方转移那一半，两侧 JEI 都会向它索取。
 * 这里不写任何界面类，专用服务端也要跑它。
 * 幽灵原料处理器在另一个插件 [client.jei.ThEJeiClientPlugin] 里，有自己的 UID。
 */
@JeiPlugin
public class ThEJeiPlugin implements IModPlugin {

    private static final Identifier UID =
            Identifier.fromNamespaceAndPath(ThEIds.MODID, "jei_plugin");

    public ThEJeiPlugin() {
    }

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(ArcaneJeiRecipeType.arcane(),
                TECEItems.ARCANE_CRAFTING_TERMINAL.get(),
                TECEItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get());
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(
                new KnowledgeInscriberRecipeTransfer(registration.getTransferHelper()),
                ArcaneJeiRecipeType.arcane());
        registration.addRecipeTransferHandler(
                new ArcaneCraftingRecipeTransfer(
                        registration.getTransferHelper()),
                ArcaneJeiRecipeType.arcane());
        registration.addRecipeTransferHandler(
                new CraftingRecipeTransfer(registration.getTransferHelper()), RecipeTypes.CRAFTING);
        // 坩埚配方编成处理样板，不从合成分类走：它要的是源质，不是九个格子。
        // 这条也把坩埚配方从 AE2 的通用处理器手里接过来——通用那条会把要素丢掉。
        for (var category : java.util.List.of(CrucibleCategory.RECIPE_TYPE, InfusionCategory.RECIPE_TYPE)) {
            var type = holderType(category);
            registration.addRecipeTransferHandler(
                    new ThaumaturgeProcessingPatternTransfer(registration.getTransferHelper(), type), type);
        }
        // 一个配方类型一个处理器就能服务有线和无线终端。
        // 分开注册只会让其中一个把另一个替换掉。
    }

    @SuppressWarnings("unchecked")
    private static IRecipeType<RecipeHolder<?>> holderType(IRecipeType<?> type) {
        return (IRecipeType<RecipeHolder<?>>) type;
    }
}
