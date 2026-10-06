package thaumicenergistics_ce.client.jade;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import thaumicenergistics_ce.block.BlockAlchemyProvider;
import thaumicenergistics_ce.block.BlockAlchemyProviderConnection;
import thaumicenergistics_ce.block.BlockArcaneAssembler;
import thaumicenergistics_ce.block.BlockEssentiaVibrationChamber;
import thaumicenergistics_ce.block.BlockOccultMonitor;
import thaumicenergistics_ce.block.BlockInfusionProvider;
/**
 * The client half of {@link thaumicenergistics_ce.integration.jade.ThEJadePlugin}: its four components.
 * <ul>
 *   <li>A second plugin, not a method on the first: {@code IWailaClientRegistration} carries {@code Screen}.
 *   <li>Each component reports the same UID as its server data half in {@code integration.jade}.
 *   <li>Jade asks every {@code @WailaPlugin} on both sides, but only a client for the client half.
 * </ul>
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
    }
}
