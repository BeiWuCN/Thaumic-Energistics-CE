package com.beiwu.thaumicenergistics_ce.integration.jade;

import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import com.beiwu.thaumicenergistics_ce.blockentity.alchemyprovider.BlockEntityAlchemyProvider;
import com.beiwu.thaumicenergistics_ce.blockentity.alchemyprovider.BlockEntityAlchemyProviderConnection;
import com.beiwu.thaumicenergistics_ce.blockentity.occultmonitor.BlockEntityOccultMonitor;
import com.beiwu.thaumicenergistics_ce.blockentity.BlockEntityInfusionProvider;
import com.beiwu.thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;
import com.beiwu.thaumicenergistics_ce.blockentity.gachabox.BlockEntityGachaBox;

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
