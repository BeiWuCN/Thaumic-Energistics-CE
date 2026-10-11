package thaumicenergistics_ce.client.jade;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.JadeUI;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.blockentity.vibrationchamber.BlockEntityEssentiaVibrationChamber.BurnState;
import thaumicenergistics_ce.blockentity.vibrationchamber.BlockEntityEssentiaVibrationChamber;
import thaumicenergistics_ce.integration.jade.JadeGridState;

public class VibrationChamberProvider implements IBlockComponentProvider {

    public static final VibrationChamberProvider INSTANCE = new VibrationChamberProvider();

    private static final Identifier UID =
            Identifier.fromNamespaceAndPath(ThEIds.MODID, "vibration_chamber");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlockEntity() instanceof BlockEntityEssentiaVibrationChamber chamber)) {
            return;
        }
        JadeGridState word = chamber.getBurnState() == BurnState.NO_NETWORK
                ? JadeGridState.OFFLINE
                : JadeGridState.ONLINE;
        tooltip.add(JadeUI.text(word.label().copy().withStyle(word.colour())));

        Identifier aspect = chamber.getCurrentAspect();
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

        switch (chamber.getBurnState()) {
            case BURNING -> tooltip.add(Component.translatable(
                    "thaumicenergistics_ce.jade.burning", String.format("%.1f", chamber.getAePerTick())));
            case NO_NETWORK -> tooltip.add(Component.translatable("thaumicenergistics_ce.jade.no_network"));
            case PAUSED_FULL -> tooltip.add(Component.translatable("thaumicenergistics_ce.jade.tank_full"));
            case IDLE -> {
            }
        }
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}
