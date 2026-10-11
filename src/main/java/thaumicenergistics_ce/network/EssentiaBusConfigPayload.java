package thaumicenergistics_ce.network;

import appeng.api.stacks.GenericStack;
import appeng.helpers.IConfigInvHost;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.IOBusMenu;
import appeng.menu.implementations.StorageBusMenu;
import appeng.menu.implementations.UpgradeableMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.integration.ae2.bus.EssentiaBusAccess;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;

public record EssentiaBusConfigPayload(int containerId, int index, Identifier aspectId)
        implements CustomPacketPayload {
    public static final Type<EssentiaBusConfigPayload> TYPE = new Type<>(ThEIds.id("essentia_bus_config"));
    public static final Identifier CLEAR = ThEIds.id("clear");
    public static final StreamCodec<RegistryFriendlyByteBuf, EssentiaBusConfigPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, EssentiaBusConfigPayload::containerId,
                    ByteBufCodecs.VAR_INT, EssentiaBusConfigPayload::index,
                    Identifier.STREAM_CODEC, EssentiaBusConfigPayload::aspectId, EssentiaBusConfigPayload::new);

    @Override
    public Type<EssentiaBusConfigPayload> type() {
        return TYPE;
    }

    public static boolean supports(UpgradeableMenu<?> menu) {
        return (menu instanceof IOBusMenu || menu instanceof StorageBusMenu)
                && EssentiaBusAccess.hasCard(menu.getHost());
    }

    public void handle(Player player) {
        if (!(player.containerMenu instanceof UpgradeableMenu<?> menu)
                || menu.containerId != containerId || !supports(menu)
                || !(menu.getHost() instanceof IConfigInvHost host)) {
            return;
        }
        var slots = menu.getSlots(SlotSemantics.CONFIG);
        if (index < 0 || index >= slots.size() || !slots.get(index).isActive()) {
            return;
        }
        var config = host.getConfig();
        int configIndex = slots.get(index).getSlotIndex();
        if (configIndex < 0 || configIndex >= config.size()) {
            return;
        }
        if (CLEAR.equals(aspectId)) {
            config.setStack(configIndex, null);
        } else {
            var aspect = AEssentiaKeyType.aspectOf(player.level(), aspectId);
            var key = aspect == null ? null : AEssentiaKey.of(aspect);
            if (key != null) {
                config.setStack(configIndex, new GenericStack(key, 1));
            }
        }
    }
}
