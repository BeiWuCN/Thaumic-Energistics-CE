package thaumicenergistics_ce.client.jade;

import appeng.api.integrations.igtooltip.PartTooltips;
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

/**
 * The Flux Transfer Interface's tooltip, drawn from what the server sent down: the reason the pair
 * is refusing in red, or whether it moved its point in the second just gone. It never asks the part
 * anything - a part on this side is a shell with no grid behind it - and it is registered from the
 * client setup because AE2's body registry holds drawing code.
 */
public final class FluxTransferTooltip implements BodyProvider<PartFluxTransferInterface> {

    public static final FluxTransferTooltip INSTANCE = new FluxTransferTooltip();

    private FluxTransferTooltip() {}

    /** Called once, from the client setup: nothing draws a tooltip before that has run, and the
     * registry caches per part class, so a late registration would be a tooltip that never appears. */
    public static void register() {
        PartTooltips.addBody(PartFluxTransferInterface.class, INSTANCE);
    }

    @Override
    public void buildTooltip(
            PartFluxTransferInterface part, TooltipContext context, TooltipBuilder tooltip) {
        CompoundTag data = context.serverData();
        Component wait = decode(data.get(FluxTransferStatusProvider.TAG_WAIT));
        if (wait != null) {
            tooltip.addLine(wait.copy().withStyle(ChatFormatting.RED));
            return;
        }
        boolean working = data.getBoolean(FluxTransferStatusProvider.TAG_WORKING);
        tooltip.addLine(Component.translatable(working
                        ? "jade.thaumicenergistics_ce.flux_transfer_interface.working"
                        : "jade.thaumicenergistics_ce.flux_transfer_interface.idle")
                .withStyle(working ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
    }

    /** The component back, or {@code null} when nothing was sent or the tag cannot be read: an empty
     * red line would read as a refusal with no reason. */
    private static @Nullable Component decode(@Nullable Tag encoded) {
        if (encoded == null) {
            return null;
        }
        return ComponentSerialization.CODEC.parse(NbtOps.INSTANCE, encoded).result().orElse(null);
    }
}
