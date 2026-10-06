package thaumicenergistics_ce.part;

import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import appeng.parts.p2p.P2PTunnelPart;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import thaumicenergistics_ce.ThEIds;

/**
 * The Flux Transfer Interface: a placeable shell whose only behaviour so far is the memory card
 * pairing every P2P tunnel comes with. What it is meant to be is a pair of them, bound to each
 * other, turning aer and ordo drawn from the network into flux in the bound chunk. None of the
 * storage bus it was renamed from is left: no storage provider, no poll, no config or upgrade
 * slots, no screen. The face, AE2's borrowed storage bus shape and that bus's collision box are
 * all that remain until the flux itself lands.
 */
public class PartFluxTransferInterface extends P2PTunnelPart<PartFluxTransferInterface> {

    @PartModels
    public static final ResourceLocation MODEL_BASE = ThEIds.id("parts/flux_transfer_interface_base");

    @PartModels
    public static final ResourceLocation MODEL_OFF = ThEIds.id("parts/flux_transfer_interface_off");

    @PartModels
    public static final ResourceLocation MODEL_ON = ThEIds.id("parts/flux_transfer_interface_on");

    @PartModels
    public static final ResourceLocation MODEL_HAS_CHANNEL =
            ThEIds.id("parts/flux_transfer_interface_has_channel");

    public static final List<ResourceLocation> MODEL_LOCATIONS =
            List.of(MODEL_BASE, MODEL_OFF, MODEL_ON, MODEL_HAS_CHANNEL);

    private static final PartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF);
    private static final PartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON);
    private static final PartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_HAS_CHANNEL);

    private static final double IDLE_POWER = 1.0;

    public PartFluxTransferInterface(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().setIdlePowerUsage(IDLE_POWER);
    }

    /** The same box AE2's storage bus uses, which is what the borrowed model is shaped for. */
    @Override
    public void getBoxes(IPartCollisionHelper boxes) {
        boxes.addBox(5, 5, 12, 11, 11, 14);
        boxes.addBox(2, 2, 14, 14, 14, 15);
        boxes.addBox(3, 3, 15, 13, 13, 16);
    }

    /** Dark while unpowered, lit when it is powered and paired to its opposite number. */
    @Override
    public IPartModel getStaticModels() {
        if (!isPowered()) {
            return MODELS_OFF;
        }
        return isActive() ? MODELS_ON : MODELS_HAS_CHANNEL;
    }
}
