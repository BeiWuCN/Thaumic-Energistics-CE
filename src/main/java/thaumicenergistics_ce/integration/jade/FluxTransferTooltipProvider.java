package thaumicenergistics_ce.integration.jade;

import appeng.api.integrations.igtooltip.BaseClassRegistration;
import appeng.api.integrations.igtooltip.ClientRegistration;
import appeng.api.integrations.igtooltip.CommonRegistration;
import appeng.api.integrations.igtooltip.PartTooltips;
import appeng.api.integrations.igtooltip.TooltipProvider;
import thaumicenergistics_ce.client.jade.FluxTransferTooltip;
import thaumicenergistics_ce.part.PartFluxTransferInterface;

public final class FluxTransferTooltipProvider implements TooltipProvider {

    @Override
    public void registerCommon(CommonRegistration registration) {
        PartTooltips.addServerData(PartFluxTransferInterface.class, FluxTransferStatusProvider.INSTANCE);
    }

    @Override
    public void registerClient(ClientRegistration registration) {
        PartTooltips.addBody(PartFluxTransferInterface.class, FluxTransferTooltip.INSTANCE);
    }

    @Override
    public void registerBlockEntityBaseClasses(BaseClassRegistration registration) {
    }
}
