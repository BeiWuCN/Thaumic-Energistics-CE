package thaumicenergistics_ce.integration.jade;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElementHelper;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.blockentity.BlockEntityEssentiaVibrationChamber.BurnState;
import thaumicenergistics_ce.blockentity.BlockEntityEssentiaVibrationChamber;

/**
 * The Essentia Vibration Chamber's Jade tooltip: whether there is a network to burn for, and what the
 * machine does with it.
 *
 * <ul>
 * <li><b>Drawn from the client's copy of the machine, not server data</b>, because Jade collects a
 * provider's server data once, when the tooltip is first drawn: lines built from it sit still while
 * the tooltip stays open, so the owner removed the machine's only consumer and it went on saying
 * "Device Online". The block entity streams state, burn rate and fuel to the client on change (see its
 * {@code writeToStream}), so these lines follow the machine instead.</li>
 * <li>Deliberately not shown: the burn's countdown and slot energy, because both move every tick or
 * visit. The machine's own screen carries them live through its menu.</li>
 * </ul>
 */
public class VibrationChamberProvider implements IBlockComponentProvider {

    public static final VibrationChamberProvider INSTANCE = new VibrationChamberProvider();

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "vibration_chamber");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlockEntity() instanceof BlockEntityEssentiaVibrationChamber chamber)) {
            return;
        }
        IElementHelper helper = IElementHelper.get();
        // AE2's own word for the node, from the machine's state: channel-free, so of AE2's four words
        // only these two can happen, and neither is a snapshot.
        JadeGridState word = chamber.getBurnState() == BurnState.NO_NETWORK
                ? JadeGridState.OFFLINE
                : JadeGridState.ONLINE;
        tooltip.add(helper.text(word.label().copy().withStyle(word.colour())));

        ResourceLocation aspect = chamber.getCurrentAspect();
        if (aspect != null && chamber.getStoredEssentia() > 0) {
            tooltip.add(Component.translatable(
                    "thaumicenergistics_ce.jade.current_aspect",
                    Component.translatable("aspect.thaumaturge." + aspect.getPath())));
        }
        tooltip.add(Component.translatable(
                "thaumicenergistics_ce.jade.essentia_stored",
                chamber.getStoredEssentia(),
                chamber.getMaxEssentia()));
        tooltip.add(Component.translatable(
                "thaumicenergistics_ce.jade.energy_output",
                String.format("%.0f", chamber.getMaxOutputPerTick())));

        // The state, last: what the machine is doing, or why it is not burning.
        switch (chamber.getBurnState()) {
            case BURNING -> tooltip.add(Component.translatable(
                    "thaumicenergistics_ce.jade.burning", String.format("%.1f", chamber.getAePerTick())));
            case NO_NETWORK -> tooltip.add(Component.translatable("thaumicenergistics_ce.jade.no_network"));
            case PAUSED_FULL -> tooltip.add(Component.translatable("thaumicenergistics_ce.jade.tank_full"));
            case IDLE -> {
                // Nothing to say: there is room in the slot and nothing loaded to burn.
            }
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
