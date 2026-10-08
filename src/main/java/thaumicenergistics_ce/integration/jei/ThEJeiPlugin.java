package thaumicenergistics_ce.integration.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.resources.Identifier;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.init.ModMenuTypes;
import thaumicenergistics_ce.util.ThELog;

/**
 * Thaumic Energistics 的 JEI 插件，配方转移那一半，两侧 JEI 都会向它索取。
 * 这里不写任何界面类，专用服务端也要跑它。
 * 幽灵原料处理器在另一个插件 [client.jei.ThEJeiClientPlugin] 里，有自己的 UID。
 * 两个插件都用 Thaumaturge 的奥术配方分类，铭刻机编码的内容与合成一致。
 */
@JeiPlugin
public class ThEJeiPlugin implements IModPlugin {

    private static final Identifier UID =
            Identifier.fromNamespaceAndPath(ThEIds.MODID, "jei_plugin");

    /**
     * 构造时打一行日志：[ForgePluginFinder] 只报告找到的插件，不报告漏掉的。
     * 没这行就分不清“没加载”和“加载了但沉默”。
     */
    public ThEJeiPlugin() {
        ThELog.LOG.info("JEI plugin constructed ({})", UID);
    }

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        // 用自带配方类型的处理器，别只给 [IRecipeTransferInfo]。
        // info 会被 JEI 的 [BasicRecipeTransferHandler] 包起来：它假定槽位一一对应，12 对 9 时就拒绝。
        registration.addRecipeTransferHandler(
                new KnowledgeInscriberRecipeTransfer(registration.getTransferHelper()),
                ArcaneJeiRecipeType.arcane());
        // 用自带配方类型的处理器，别只给 [IRecipeTransferInfo]。
        // info 会被 JEI 的 [BasicRecipeTransferHandler] 包起来：它假定槽位一一对应，12 对 9 时就拒绝。
        registration.addRecipeTransferHandler(
                new ArcaneCraftingRecipeTransfer(
                        registration.getTransferHelper(), ModMenuTypes.ARCANE_CRAFTING_TERMINAL.get()),
                ArcaneJeiRecipeType.arcane());
        // 用自带配方类型的处理器，别只给 [IRecipeTransferInfo]。
        // info 会被 JEI 的 [BasicRecipeTransferHandler] 包起来：它假定槽位一一对应，12 对 9 时就拒绝。
        registration.addRecipeTransferHandler(
                new CraftingRecipeTransfer(
                        registration.getTransferHelper(), ModMenuTypes.ARCANE_CRAFTING_TERMINAL.get()),
                RecipeTypes.CRAFTING);
        // 用自带配方类型的处理器，别只给 [IRecipeTransferInfo]。
        // info 会被 JEI 的 [BasicRecipeTransferHandler] 包起来：它假定槽位一一对应，12 对 9 时就拒绝。
        registration.addRecipeTransferHandler(
                new ArcaneCraftingRecipeTransfer(
                        registration.getTransferHelper(), ModMenuTypes.WIRELESS_ARCANE_CRAFTING_TERMINAL.get()),
                ArcaneJeiRecipeType.arcane());
        registration.addRecipeTransferHandler(
                new CraftingRecipeTransfer(
                        registration.getTransferHelper(), ModMenuTypes.WIRELESS_ARCANE_CRAFTING_TERMINAL.get()),
                RecipeTypes.CRAFTING);
    }
}
