package thaumicenergistics_ce.network;

import appeng.api.stacks.GenericStack;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import appeng.menu.implementations.InterfaceMenu;
import appeng.util.ConfigInventory;
import com.leclowndu93150.thaumaturge.api.aspect.Aspects;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.util.ThELog;

/**
 * "Put this aspect in that interface slot", sent when a player drops one out of JEI onto an interface.
 * <ul>
 *   <li>The slot is always in the config row, the row whose marks the card pulls neighbours in with.
 *   <li>An aspect travels as an id: a slot write goes through {@code AEItemKey}, which drops a key that
 *       is not an item - the same reason {@code EssentiaBusConfigPayload} exists.
 * </ul>
 */
public record EssentiaInterfaceMarkPayload(int containerId, int index, ResourceLocation aspectId)
        implements CustomPacketPayload {

    public static final Type<EssentiaInterfaceMarkPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "essentia_interface_mark"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EssentiaInterfaceMarkPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    EssentiaInterfaceMarkPayload::containerId,
                    ByteBufCodecs.VAR_INT,
                    EssentiaInterfaceMarkPayload::index,
                    ResourceLocation.STREAM_CODEC,
                    EssentiaInterfaceMarkPayload::aspectId,
                    EssentiaInterfaceMarkPayload::new);

    public static final ResourceLocation CLEAR = ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "clear");

    @Override
    public Type<EssentiaInterfaceMarkPayload> type() {
        return TYPE;
    }

    /** Applies the mark, but only if the interface this menu belongs to has the access card in it. */
    public void handle(Player player) {
        if (!(player.containerMenu instanceof InterfaceMenu menu) || menu.containerId != containerId) {
            return;
        }
        // Without the card the row is AE2's own and an essentia key in it would have no meaning.
        if (!menu.getUpgrades().isInstalled(ModItems.ESSENTIA_ACCESS_CARD.get())) {
            return;
        }
        if (!(menu.getHost() instanceof InterfaceLogicHost host)) {
            return;
        }
        InterfaceLogic logic = host.getInterfaceLogic();
        ConfigInventory target = logic.getConfig();
        if (index < 0 || index >= target.size()) {
            ThELog.LOG.debug("[essentia-interface] mark {} is out of range", index);
            return;
        }
        if (CLEAR.equals(aspectId)) {
            target.setStack(index, null);
            return;
        }
        Holder<IAspect> aspect = Aspects.resolve(
                player.level(),
                ResourceKey.create(IAspect.REGISTRY_KEY, aspectId));
        if (aspect == null) {
            ThELog.LOG.warn("[essentia-interface] the server cannot resolve aspect {}", aspectId);
            return;
        }
        AEssentiaKey key = AEssentiaKey.of(aspect);
        if (key == null) {
            // Not registry-backed: no id, so the mark could never match anything either.
            ThELog.LOG.warn("[essentia-interface] aspect {} is not a registry entry", aspectId);
            return;
        }
        target.setStack(index, new GenericStack(key, 1));
    }
}
