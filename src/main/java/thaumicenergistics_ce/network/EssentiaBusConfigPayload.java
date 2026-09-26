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
 * <h2>Why this is a packet and not a slot write</h2>
 *
 * <p>The usual way to fill an AE2 config slot from JEI is to wrap the key in an item stack carrying it as a
 * data component and call {@code Slot#set} - which is what this mod's ghost handler did first, and it does
 * not work for a key that is not an item. {@code ConfigMenuInventory} converts between the menu's item stack
 * and the config's {@code AEKey} through {@code AEItemKey}; an essentia key is not one, so the conversion
 * drops it and the entry appears and then vanishes as soon as the server answers. That is exactly the
 * reported symptom: drag an aspect in, the mark does not stay.
 *
 * <p>So the aspect travels as an id, the way {@link EssentiaFillPayload} already sends one, and the server
 * rebuilds the key and writes the config inventory directly. The client's optimistic write is only there so
 * the mark appears during the round trip; the server's answer is what makes it real.
 *
 * <h2>The empty case</h2>
 *
 * <p>An empty {@link ResourceLocation} clears the slot. That is not a special case bolted on: JEI offers the
 * same target whatever is dragged, and clearing is the only way to undo a filter with the mouse.
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
        // Logged before any check, so "did the packet arrive at all" is answered separately from "was it
        // accepted". Those two failures look identical from the player's side and have nothing in common.
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
