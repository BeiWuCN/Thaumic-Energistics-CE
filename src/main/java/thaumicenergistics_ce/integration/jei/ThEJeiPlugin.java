package thaumicenergistics_ce.integration.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.resources.ResourceLocation;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.util.ThELog;

/**
 * Thaumic Energistics 的 JEI 插件：配方转移那一半，两侧 JEI 都会向它索取。
 * 它不指定任何界面类，因为这是专用服务端也要跑的那一半：幽灵原料处理器在
 * [client.jei.ThEJeiClientPlugin] 里，那是另一个插件，有自己的 UID。
 * 两者都用 Thaumaturge 的奥术配方分类，
 * 好让铭刻机编码的东西与它合成的东西完全一致。
 */
@JeiPlugin
public class ThEJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "jei_plugin");

    /**
     * 构造时打日志：[ForgePluginFinder] 只报告它找到的插件，从不报告漏掉的，
     * 所以这一行不用调试器就能把“没加载”与“加载了但沉默”区分开。
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
        // 用一个自带配方类型的处理器，而不是 [IRecipeTransferInfo]：光秃秃的 info 会被 JEI 的
        // [BasicRecipeTransferHandler] 包裹，后者假定容器槽位与配方槽位一一对应，12 对 9 时就会拒绝。
        registration.addRecipeTransferHandler(
                new KnowledgeInscriberRecipeTransfer(registration.getTransferHelper()),
                ArcaneJeiRecipeType.arcane());
        // 还有奥术合成终端的网格，来自同一个分类；这是第二个处理器而不是共用一个，
        // 因为铭刻机的网格是幽灵网格，而终端的是真实的。
        registration.addRecipeTransferHandler(
                new ArcaneCraftingRecipeTransfer(registration.getTransferHelper()),
                ArcaneJeiRecipeType.arcane());
        // 普通合成配方也要：终端的网格就是九个普通槽位，玩家打开一个木板配方
        // 发现没有转移按钮，会理所当然地认为终端坏了。
        registration.addRecipeTransferHandler(
                new CraftingRecipeTransfer(registration.getTransferHelper()), RecipeTypes.CRAFTING);
        // 一个配方类型一个处理器就能服务两个终端：分别注册有线和无线终端，
        // 只会让其中一个把另一个替换掉。
    }
}
