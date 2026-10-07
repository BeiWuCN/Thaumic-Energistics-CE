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
 * Registers this mod's Jade providers: the server data half, which Jade asks for on both sides.
 * The class is annotated rather than listed in a service file, because that is how Jade finds plugins
 * on NeoForge, and Jade is optional, so the class is loaded only when Jade is present. The block
 * components, drawn on a client, are registered by {@code client.jade.ThEJadeClientPlugin}.
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
