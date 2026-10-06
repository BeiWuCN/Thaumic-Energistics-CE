package thaumicenergistics_ce.integration.jade;

import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import thaumicenergistics_ce.blockentity.alchemyprovider.BlockEntityAlchemyProvider;
import thaumicenergistics_ce.blockentity.infusionmonitor.BlockEntityInfusionMonitor;
import thaumicenergistics_ce.blockentity.BlockEntityInfusionProvider;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;

/**
 * Registers this mod's Jade providers: the server data half, which Jade asks for on both sides.
 * <ul>
 * <li>Annotated rather than a service file: that is how Jade finds plugins on NeoForge.</li>
 * <li>Jade is optional; this class is only loaded when Jade is present.</li>
 * <li>The block components, drawn on a client, are registered by {@code client.jade.ThEJadeClientPlugin}.
 * </ul>
 */
@WailaPlugin
public class ThEJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(ArcaneAssemblerProvider.INSTANCE, BlockEntityArcaneAssembler.class);
        registration.registerBlockDataProvider(
                InfusionMonitorProvider.INSTANCE, BlockEntityInfusionMonitor.class);
        registration.registerBlockDataProvider(
                InfusionProviderProvider.INSTANCE, BlockEntityInfusionProvider.class);
        registration.registerBlockDataProvider(
                AlchemyProviderProvider.INSTANCE, BlockEntityAlchemyProvider.class);
    }
}
