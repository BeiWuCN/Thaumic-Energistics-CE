package com.beiwu.thaumicenergistics_ce.client.jade;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import com.beiwu.thaumicenergistics_ce.block.BlockAlchemyProvider;
import com.beiwu.thaumicenergistics_ce.block.BlockAlchemyProviderConnection;
import com.beiwu.thaumicenergistics_ce.block.BlockArcaneAssembler;
import com.beiwu.thaumicenergistics_ce.block.BlockEssentiaVibrationChamber;
import com.beiwu.thaumicenergistics_ce.block.BlockGachaBox;
import com.beiwu.thaumicenergistics_ce.block.BlockOccultMonitor;
import com.beiwu.thaumicenergistics_ce.block.BlockInfusionProvider;
@WailaPlugin
public class ThEJadeClientPlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.addRayTraceCallback((hit, accessor, original) -> {
            if (accessor instanceof snownee.jade.api.BlockAccessor block
                    && block.getBlockState().getBlock() instanceof com.beiwu.thaumicenergistics_ce.block.BlockGachaBoxSpace) {
                var pos = block.getPosition().below();
                var state = block.getLevel().getBlockState(pos);
                if (state.getBlock() instanceof BlockGachaBox) {
                    var target = block.getHitResult();
                    return registration.blockAccessor().from(block)
                            .hit(new net.minecraft.world.phys.BlockHitResult(target.getLocation(),
                                    target.getDirection(), pos, target.isInside()))
                            .blockState(state).blockEntity(block.getLevel().getBlockEntity(pos)).build();
                }
            }
            return accessor;
        });
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
