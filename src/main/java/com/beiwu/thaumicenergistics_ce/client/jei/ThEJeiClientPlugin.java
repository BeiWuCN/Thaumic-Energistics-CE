package com.beiwu.thaumicenergistics_ce.client.jei;

import appeng.client.gui.implementations.InterfaceScreen;
import com.leclowndu93150.thaumaturge.api.aspect.AspectIndexAccess;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import com.beiwu.thaumicenergistics_ce.ThEIds;
import com.beiwu.thaumicenergistics_ce.integration.jei.AspectFromItemRecipe;
import com.beiwu.thaumicenergistics_ce.client.gui.ScreenArcaneEncodingTerminal;
import com.beiwu.thaumicenergistics_ce.client.gui.ScreenDistillationEncoder;
import com.beiwu.thaumicenergistics_ce.client.gui.ScreenEssentiaCellWorkbench;
import com.beiwu.thaumicenergistics_ce.client.gui.ScreenKnowledgeInscriber;

/**
 * {@link com.beiwu.thaumicenergistics_ce.integration.jei.ThEJeiPlugin} 的客户端部分：幽灵配料处理器和它投放的屏幕。
 * 做成第二个插件：registerGuiHandlers 注册本身就要一个 Screen 名。
 * JEI 只从它的客户端启动器到这里，UID 与转移那一半分开。
 */
@JeiPlugin
public class ThEJeiClientPlugin implements IModPlugin {

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "jei_client_plugin");

    /**
     * 构造时就登记要素转换器：JEI 查配方时会遍历 {@code IngredientConverters}，
     * 登记得早一次就够了。没装 AE2 JEI 整合时整段跳过，免得踩到它的类。
     */
    public ThEJeiClientPlugin() {
        if (net.neoforged.fml.ModList.get().isLoaded("ae2jeiintegration")) {
            EssentiaJeiBridge.register();
        }
    }

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    /** 本 mod 自己的 JEI 分类：从物品提取要素，供要素蒸馏编处理样板用。 */
    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new AspectFromItemCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    /**
     * 每一件有要素组成的物品一页。数据源和要素蒸馏编码器完全同一套
     * （{@code AspectIndexAccess.of}），所以 JEI 上看到的数量就是样板会写进去的数量。
     */
    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(AspectFromItemCategory.RECIPE_TYPE, buildAspectRecipes(registration));
    }

    private static List<AspectFromItemRecipe> buildAspectRecipes(IRecipeRegistration registration) {
        List<AspectFromItemRecipe> recipes = new ArrayList<>();
        for (ItemStack stack : registration.getIngredientManager().getAllIngredients(VanillaTypes.ITEM_STACK)) {
            if (stack.isEmpty()) {
                continue;
            }
            AspectList composition = AspectIndexAccess.of(stack);
            if (composition == null || composition.isEmpty()) {
                continue;
            }
            List<AspectInstance> aspects = new ArrayList<>(composition.entries().size());
            for (AspectInstance entry : composition.entries()) {
                if (entry.amount() > 0 && aspects.size() < AspectFromItemCategory.MAX_ASPECTS) {
                    aspects.add(new AspectInstance(entry.aspect(), entry.amount()));
                }
            }
            if (!aspects.isEmpty()) {
                recipes.add(new AspectFromItemRecipe(stack.copyWithCount(1), List.copyOf(aspects)));
            }
        }
        return recipes;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {        registration.addGhostIngredientHandler(
                ScreenKnowledgeInscriber.class, new KnowledgeInscriberGhostIngredientHandler());
        // 每个具体屏幕类一个处理器：JEI 把一个 [Class] 和同类型的处理器配对，
        // 而这个拖拽目标收的是 Thaumaturge 的要素配料，不是物品。
        registration.addGhostIngredientHandler(
                ScreenEssentiaCellWorkbench.class, new CellWorkbenchGhostIngredientHandler());
        // 还有蒸馏编码器的来源槽位，待蒸馏的物品可以直接拖进去，玩家不用手动从终端拿。
        registration.addGhostIngredientHandler(
                ScreenDistillationEncoder.class, new DistillationEncoderGhostIngredientHandler());
        // AE2 自己的 ME 接口，前提是访问卡插在里面：两种宿主形式共用一个屏幕，
        // 这一行就覆盖了方块和线缆部件。
        registration.addGhostIngredientHandler(
                InterfaceScreen.class, new EssentiaInterfaceGhostIngredientHandler());
        // 奥术编码终端右边的样板面板比 195 宽的背景伸出 38px，申报成额外区域让 JEI 让开，
        // 否则物品列表会正压在 21 个槽位上。
        registration.addGuiContainerHandler(
                ScreenArcaneEncodingTerminal.class,
                new IGuiContainerHandler<ScreenArcaneEncodingTerminal>() {
                    @Override
                    public List<Rect2i> getGuiExtraAreas(ScreenArcaneEncodingTerminal screen) {
                        return screen.jeiExtraAreas();
                    }
                });
    }
}
