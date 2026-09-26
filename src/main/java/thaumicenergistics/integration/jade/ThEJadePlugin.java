package thaumicenergistics.integration.jade;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import thaumicenergistics.block.BlockArcaneAssembler;
import thaumicenergistics.block.BlockEssentiaVibrationChamber;
import thaumicenergistics.block.BlockInfusionMonitor;
import thaumicenergistics.block.BlockInfusionProvider;
import thaumicenergistics.blockentity.BlockEntityArcaneAssembler;
import thaumicenergistics.blockentity.BlockEntityInfusionMonitor;
import thaumicenergistics.blockentity.BlockEntityInfusionProvider;

/**
 * Registers this mod's Jade providers.
 *
 * <p>Annotated rather than listed in a service file, which is how Jade finds plugins on NeoForge. Jade is
 * an optional dependency and only ever loads this class itself, so a world without Jade never reaches it.
 */
@WailaPlugin
public class ThEJadePlugin implements IWailaPlugin {

    /** Server side: the machines' own numbers, read where they are true. */
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(ArcaneAssemblerProvider.INSTANCE, BlockEntityArcaneAssembler.class);
        registration.registerBlockDataProvider(
                InfusionMonitorProvider.INSTANCE, BlockEntityInfusionMonitor.class);
        registration.registerBlockDataProvider(
                InfusionProviderProvider.INSTANCE, BlockEntityInfusionProvider.class);
    }

    /** Client side: the same providers, drawing what the server sent. */
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(ArcaneAssemblerProvider.INSTANCE, BlockArcaneAssembler.class);
        registration.registerBlockComponent(
                VibrationChamberProvider.INSTANCE, BlockEssentiaVibrationChamber.class);
        registration.registerBlockComponent(
                InfusionMonitorProvider.INSTANCE, BlockInfusionMonitor.class);
        registration.registerBlockComponent(
                InfusionProviderProvider.INSTANCE, BlockInfusionProvider.class);
    }
}
