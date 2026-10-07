package thaumicenergistics_ce.client.jei;

import appeng.client.gui.implementations.InterfaceScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.resources.ResourceLocation;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.client.gui.ScreenDistillationEncoder;
import thaumicenergistics_ce.client.gui.ScreenEssentiaCellWorkbench;
import thaumicenergistics_ce.client.gui.ScreenKnowledgeInscriber;

/**
 * {@link thaumicenergistics_ce.integration.jei.ThEJeiPlugin} 的客户端部分：幽灵配料处理器和它投放的屏幕。
 * 做成第二个插件：registerGuiHandlers 注册本身就要一个 Screen 名。
 * JEI 只从它的客户端启动器到这里，UID 与转移那一半分开。
 */
@JeiPlugin
public class ThEJeiClientPlugin implements IModPlugin {

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "jei_client_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(
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
    }
}
