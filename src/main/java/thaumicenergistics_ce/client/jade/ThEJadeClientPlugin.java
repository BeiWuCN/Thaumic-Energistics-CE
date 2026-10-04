package thaumicenergistics_ce.client.jade;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import thaumicenergistics_ce.block.BlockArcaneAssembler;
import thaumicenergistics_ce.block.BlockEssentiaVibrationChamber;
import thaumicenergistics_ce.block.BlockInfusionMonitor;
import thaumicenergistics_ce.block.BlockInfusionProvider;
import thaumicenergistics_ce.integration.jade.ArcaneAssemblerProvider;
import thaumicenergistics_ce.integration.jade.InfusionMonitorProvider;

/**
 * The client half of {@link thaumicenergistics_ce.integration.jade.ThEJadePlugin}: the two providers that
 * draw on the client's side of the tooltip, alongside the two that serve both sides.
 *
 * <ul>
 *   <li>A second plugin rather than a method on the first: {@code IWailaClientRegistration}'s own
 *       signatures carry {@code Screen}, so the call can only be written where client classes may be
 *       named. Splitting the plugin keeps the client providers out of the common tree's imports.</li>
 *   <li>Jade bootstraps every {@code @WailaPlugin} class on both sides and asks each one for its common
 *       registration; only a physical client is asked for the client registration, which
 *       {@code CommonProxy.isPhysicallyClient()} decides.</li>
 * </ul>
 */
@WailaPlugin
public class ThEJadeClientPlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(ArcaneAssemblerProvider.INSTANCE, BlockArcaneAssembler.class);
        registration.registerBlockComponent(
                VibrationChamberProvider.INSTANCE, BlockEssentiaVibrationChamber.class);
        registration.registerBlockComponent(
                InfusionMonitorProvider.INSTANCE, BlockInfusionMonitor.class);
        registration.registerBlockComponent(
                InfusionProviderTooltip.INSTANCE, BlockInfusionProvider.class);
    }
}
