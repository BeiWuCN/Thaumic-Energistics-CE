package thaumicenergistics_ce.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.ThaumicEnergistics;
import thaumicenergistics_ce.menu.MenuEssentiaBus;

/**
 * "Put this aspect in that config slot", sent by a bus screen when a player drops one out of JEI.
 *
 * <ul>
 * <li>A slot write cannot work: {@code ConfigMenuInventory} converts through {@code AEItemKey}, so a
 * non-item key is dropped and the mark vanishes as soon as the server answers.</li>
 * <li>The aspect therefore travels as an id, like {@link EssentiaFillPayload}; the client's optimistic
 * write only shows the mark during the round trip, the server's answer is what makes it real.</li>
 * <li>An empty {@link ResourceLocation} clears the slot - the only way to undo a filter with the mouse.</li>
 * </ul>
 */
public record EssentiaBusConfigPayload(int containerId, int configSlot, ResourceLocation aspectId)
        implements CustomPacketPayload {

    public static final Type<EssentiaBusConfigPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "essentia_bus_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EssentiaBusConfigPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    EssentiaBusConfigPayload::containerId,
                    ByteBufCodecs.VAR_INT,
                    EssentiaBusConfigPayload::configSlot,
                    ResourceLocation.STREAM_CODEC,
                    EssentiaBusConfigPayload::aspectId,
                    EssentiaBusConfigPayload::new);

    /** The id that means "clear this slot". */
    public static final ResourceLocation CLEAR = ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "clear");

    @Override
    public Type<EssentiaBusConfigPayload> type() {
        return TYPE;
    }

    public void handle(Player player) {
        // Logged before any check: "did it arrive" and "was it accepted" look identical to the player.
        ThaumicEnergistics.LOG.info(
                "[bus-config] received slot {} <- {} for menu {} (open: {})",
                configSlot, aspectId, containerId, player.containerMenu.getClass().getSimpleName());

        if (!(player.containerMenu instanceof MenuEssentiaBus<?> menu) || menu.containerId != containerId) {
            ThaumicEnergistics.LOG.warn("[bus-config] dropped: the open menu is not that bus");
            return;
        }
        menu.setConfigAspect(configSlot, aspectId, player);
        ThaumicEnergistics.LOG.info(
                "[bus-config] slot {} now holds {}", configSlot, menu.configFor(configSlot));
    }
}
