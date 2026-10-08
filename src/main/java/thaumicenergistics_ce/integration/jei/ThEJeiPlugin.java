package thaumicenergistics_ce.integration.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.resources.ResourceLocation;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.util.ThELog;

/**
 * Thaumic Energistics 的 JEI 插件，配方转移那一半，两侧 JEI 都会向它索取。
 * 这里不写任何界面类，专用服务端也要跑它。
 * 幽灵原料处理器在另一个插件 [client.jei.ThEJeiClientPlugin] 里，有自己的 UID。
 * 两个插件都用 Thaumaturge 的配方分类，铭刻机编码的内容与合成一致。
 */
@JeiPlugin
public class ThEJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "jei_plugin");

    /**
     * 构造时打一行日志：[ForgePluginFinder] 只报告找到的插件，不报告漏掉的。
     * 没这行就分不清“没加载”和“加载了但沉默”。
     */
    public ThEJeiPlugin() {
        ThELog.LOG.info("JEI plugin constructed ({})", UID);
    }

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        // 用自带配方类型的处理器，别只给 [IRecipeTransferInfo]。
        // info 会被 JEI 的 [BasicRecipeTransferHandler] 包起来：它假定槽位一一对应，12 对 9 时就拒绝。
        registration.addRecipeTransferHandler(
                new KnowledgeInscriberRecipeTransfer(registration.getTransferHelper()),
                ArcaneJeiRecipeType.arcane());
        // 还有奥术合成终端的网格，同样来自奥术工作台分类。
        // 没有共用第 1 个处理器：铭刻机的网格是幽灵网格，终端的是真实槽位。
        registration.addRecipeTransferHandler(
                new ArcaneCraftingRecipeTransfer(registration.getTransferHelper()),
                ArcaneJeiRecipeType.arcane());
        // 普通合成配方也注册：终端网格就是九个普通槽位。
        // 玩家打开木板配方发现没有转移按钮，会以为终端坏了。
        registration.addRecipeTransferHandler(
                new CraftingRecipeTransfer(registration.getTransferHelper()), RecipeTypes.CRAFTING);
        // 坩埚配方编成处理样板，不从合成分类走：它要的是源质，不是九个格子。
        // 这条也把坩埚配方从 AE2 的通用处理器手里接过来——通用那条会把要素丢掉。
        registration.addRecipeTransferHandler(
                new CruciblePatternTransfer(registration.getTransferHelper()),
                CrucibleJeiRecipeType.crucible());
        // 一个配方类型一个处理器就能服务有线和无线终端。
        // 分开注册只会让其中一个把另一个替换掉。
    }
}
