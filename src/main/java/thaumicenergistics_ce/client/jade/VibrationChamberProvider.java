package thaumicenergistics_ce.client.jade;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElementHelper;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.blockentity.vibrationchamber.BlockEntityEssentiaVibrationChamber.BurnState;
import thaumicenergistics_ce.blockentity.vibrationchamber.BlockEntityEssentiaVibrationChamber;
import thaumicenergistics_ce.integration.jade.JadeGridState;

/**
 * 源质振动室的 Jade tooltip：有没有能烧的网络，机器在做什么。
 * 各行取自客户端的副本，不用服务端数据：Jade 只采一次数据，之后行就冻住了；
 * 燃烧倒计时和槽内能量刻意不写，两者每 tick 都在变。
 * 仅客户端：只有 Jade 的 [registerClient] 注册它，专用服务端跳过。
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
        // 用 AE2 自己描述节点的说法，取自机器状态：不占频道，AE2 的四个说法里只出现这两个，都不是快照。
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

        // 状态放最后：机器在做什么，或者没烧起来的原因。
        switch (chamber.getBurnState()) {
            case BURNING -> tooltip.add(Component.translatable(
                    "thaumicenergistics_ce.jade.burning", String.format("%.1f", chamber.getAePerTick())));
            case NO_NETWORK -> tooltip.add(Component.translatable("thaumicenergistics_ce.jade.no_network"));
            case PAUSED_FULL -> tooltip.add(Component.translatable("thaumicenergistics_ce.jade.tank_full"));
            case IDLE -> {
                // 没什么可说：槽里还有空位，也没装可烧的东西。
            }
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
