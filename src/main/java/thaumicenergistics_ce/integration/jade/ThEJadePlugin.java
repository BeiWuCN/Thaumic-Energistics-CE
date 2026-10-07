package thaumicenergistics_ce.integration.jade;

import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import thaumicenergistics_ce.blockentity.alchemyprovider.BlockEntityAlchemyProvider;
import thaumicenergistics_ce.blockentity.alchemyprovider.BlockEntityAlchemyProviderConnection;
import thaumicenergistics_ce.blockentity.occultmonitor.BlockEntityOccultMonitor;
import thaumicenergistics_ce.blockentity.BlockEntityInfusionProvider;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;
import thaumicenergistics_ce.blockentity.gachabox.BlockEntityGachaBox;

/**
 * 注册本 mod 的 Jade 提供器：服务端数据那一半，两侧 Jade 都会向它索取。
 * 用注解标记类而不是在 service 文件里列出，因为 NeoForge 上 Jade 就是这样找插件的；
 * Jade 是可选的，所以只有 Jade 存在时这个类才会被加载。绘制在客户端的方块组件由
 * [client.jade.ThEJadeClientPlugin] 注册。
 */
@WailaPlugin
public class ThEJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(ArcaneAssemblerProvider.INSTANCE, BlockEntityArcaneAssembler.class);
        registration.registerBlockDataProvider(
                OccultMonitorProvider.INSTANCE, BlockEntityOccultMonitor.class);
        registration.registerBlockDataProvider(
                InfusionProviderProvider.INSTANCE, BlockEntityInfusionProvider.class);
        registration.registerBlockDataProvider(
                AlchemyProviderProvider.INSTANCE, BlockEntityAlchemyProvider.class);
        registration.registerBlockDataProvider(
                AlchemyReceiverProvider.INSTANCE, BlockEntityAlchemyProviderConnection.class);
        registration.registerBlockDataProvider(GachaBoxProvider.INSTANCE, BlockEntityGachaBox.class);
    }
}
