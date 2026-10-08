package thaumicenergistics_ce.client.jade;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import thaumicenergistics_ce.block.BlockAlchemyProvider;
import thaumicenergistics_ce.block.BlockAlchemyProviderConnection;
import thaumicenergistics_ce.block.BlockArcaneAssembler;
import thaumicenergistics_ce.block.BlockEssentiaVibrationChamber;
import thaumicenergistics_ce.block.BlockGachaBox;
import thaumicenergistics_ce.block.BlockOccultMonitor;
import thaumicenergistics_ce.block.BlockInfusionProvider;
/**
 * 客户端半边，对应 {@link thaumicenergistics_ce.integration.jade.ThEJadePlugin}：它的四个组件。
 * 做成第二个插件，不在第一个上挂方法：[IWailaClientRegistration] 携带的是 [Screen]；
 * 每个组件上报的 UID 与服务端数据半边（在 [integration.jade] 里）相同。
 * Jade 会在两侧问每个 [@WailaPlugin]，客户端只问客户端半边。
 */
@WailaPlugin
public class ThEJadeClientPlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(ArcaneAssemblerTooltip.INSTANCE, BlockArcaneAssembler.class);
        registration.registerBlockComponent(
                VibrationChamberProvider.INSTANCE, BlockEssentiaVibrationChamber.class);
        registration.registerBlockComponent(
                OccultMonitorTooltip.INSTANCE, BlockOccultMonitor.class);
        registration.registerBlockComponent(
                InfusionProviderTooltip.INSTANCE, BlockInfusionProvider.class);
        registration.registerBlockComponent(
                AlchemyProviderTooltip.INSTANCE, BlockAlchemyProvider.class);
        registration.registerBlockComponent(
                AlchemyReceiverTooltip.INSTANCE, BlockAlchemyProviderConnection.class);
        registration.registerBlockComponent(GachaBoxTooltip.INSTANCE, BlockGachaBox.class);
    }
}
