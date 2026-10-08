package thaumicenergistics_ce.client.jade;

import appeng.api.integrations.igtooltip.TooltipBuilder;
import appeng.api.integrations.igtooltip.TooltipContext;
import appeng.api.integrations.igtooltip.providers.BodyProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.integration.jade.FluxTransferStatusProvider;
import thaumicenergistics_ce.part.PartFluxTransferInterface;

/** 通量接口的悬浮提示，绘制那一半。它绝不碰部件：
 * 客户端上那只是个空壳，没有网格可问。 */
public final class FluxTransferTooltip implements BodyProvider<PartFluxTransferInterface> {

    public static final FluxTransferTooltip INSTANCE = new FluxTransferTooltip();

    private FluxTransferTooltip() {}

    @Override
    public void buildTooltip(
            PartFluxTransferInterface part, TooltipContext context, TooltipBuilder tooltip) {
        CompoundTag data = context.serverData();
        Component wait = decode(data.get(FluxTransferStatusProvider.TAG_WAIT));
        if (wait != null) {
            tooltip.addLine(wait.copy().withStyle(ChatFormatting.RED));
            return;
        }
        boolean working = data.getBooleanOr(FluxTransferStatusProvider.TAG_WORKING, false);
        tooltip.addLine(Component.translatable(working
                        ? "jade.thaumicenergistics_ce.flux_transfer_interface.working"
                        : "jade.thaumicenergistics_ce.flux_transfer_interface.idle")
                .withStyle(working ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
    }

    private static @Nullable Component decode(@Nullable Tag encoded) {
        if (encoded == null) {
            return null;
        }
        return ComponentSerialization.CODEC.parse(NbtOps.INSTANCE, encoded).result().orElse(null);
    }
}
