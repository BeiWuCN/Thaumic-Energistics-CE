package thaumicenergistics_ce.compat.thaumaturge;

import appeng.api.parts.IPart;
import appeng.api.parts.RegisterPartCapabilitiesEvent;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aura.AuraHelper;
import com.leclowndu93150.thaumaturge.api.aura.IVisRelaySource;
import com.leclowndu93150.thaumaturge.api.aura.VisRelayCapabilities;
import com.leclowndu93150.thaumaturge.api.aura.VisRelayHelper;
import com.leclowndu93150.thaumaturge.content.aura.node.BlockEntityNode;
import com.leclowndu93150.thaumaturge.content.aura.node.NodeVisRelaySource;
import com.leclowndu93150.thaumaturge.content.aura.relay.BlockEntityVisRelay;
import com.leclowndu93150.thaumaturge.content.aura.relay.VisRelayNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Thaumaturge's aura, vis relay chains and node network.
 *
 * <ul>
 *   <li>0.4.6 made the relay pull-based and deleted the reservation API and {@code resolveAddonSource}.
 *   <li>Callers never receive relay or node objects, so walking a chain stays inside this class.
 * </ul>
 */
public final class TcAura {
    private TcAura() {}

    // -- ambient aura --------------------------------------------------------

    public static float vis(Level level, BlockPos pos) {
        return AuraHelper.getVis(level, pos);
    }

    public static int auraBase(Level level, BlockPos pos) {
        return AuraHelper.getAuraBase(level, pos);
    }

    /** Under {@code simulate} the aura is consulted and left alone, so the return is what the
     * caller could take rather than what it took. */
    public static float drainVis(Level level, BlockPos pos, float want, boolean simulate) {
        return AuraHelper.drainVis(level, pos, want, simulate);
    }

    // -- vis relay chain -----------------------------------------------------

    public static boolean relayWithinReach(ServerLevel level, BlockPos consumer) {
        return VisRelayNetwork.findRelayNear(level, consumer) != null;
    }

    /** A relay whose parent chain leads nowhere can be linked to and still carry nothing, so this is
     * a separate question from {@link #relayWithinReach}: one finds the block, this finds the chain. */
    public static boolean relayResolves(ServerLevel level, BlockPos consumer) {
        BlockEntityVisRelay relay = VisRelayNetwork.findRelayNear(level, consumer);
        return relay != null && relay.resolveSource(level) != null;
    }

    public static @Nullable BlockPos nodeBehind(ServerLevel level, BlockPos consumer) {
        BlockEntityNode node = nodeAtEnd(level, VisRelayNetwork.findRelayNear(level, consumer));
        return node == null ? null : node.getBlockPos();
    }

    public static boolean nodeHolds(ServerLevel level, BlockPos nodePos, ResourceKey<IAspect> primal) {
        return level.getBlockEntity(nodePos) instanceof BlockEntityNode node
                && node.getAspectsBase().amountOf(primal, level.registryAccess()) > 0;
    }

    /** Centivis are the chain's own unit: 100 of them to a vis. */
    public static int drainCentivis(ServerLevel level, BlockPos consumer,
            ResourceKey<IAspect> primal, int amount, boolean simulate) {
        return VisRelayHelper.drainCentivis(level, consumer, primal, amount, simulate);
    }

    // -- chain inspection, for the self tests --------------------------------

    public static boolean isRelay(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof BlockEntityVisRelay;
    }

    public static @Nullable RelayLink link(ServerLevel level, BlockPos relayPos) {
        if (!(level.getBlockEntity(relayPos) instanceof BlockEntityVisRelay relay)) {
            return null;
        }
        return new RelayLink(relay.isLinked(), relay.depth(), relay.parentPos());
    }

    public static @Nullable RelayEnd chainEnd(ServerLevel level, BlockPos relayPos) {
        if (!(level.getBlockEntity(relayPos) instanceof BlockEntityVisRelay relay)) {
            return null;
        }
        var linked = relay.resolveSource(level);
        if (linked == null) {
            return null;
        }
        if (linked.source() instanceof NodeVisRelaySource node) {
            return new RelayEnd("node", node.node().getBlockPos());
        }
        // Another addon's source, or one of this mod's own vis interfaces. Either way it is not a
        // node, so there is no aspect list to read here.
        return new RelayEnd(linked.source().getClass().getSimpleName(), linked.position());
    }

    public static @Nullable NodeReport nodeReport(ServerLevel level, BlockPos relayPos) {
        if (!(level.getBlockEntity(relayPos) instanceof BlockEntityVisRelay relay)) {
            return null;
        }
        BlockEntityNode node = nodeAtEnd(level, relay);
        if (node == null) {
            return null;
        }
        List<NodeAspect> palette = new ArrayList<>();
        for (AspectInstance entry : node.getAspectsBase().entries()) {
            String name = entry.aspect().unwrapKey()
                    .map(key -> key.location().getPath())
                    .orElse("?");
            palette.add(new NodeAspect(name, entry.amount(),
                    node.centivisRate(entry.aspect()),
                    node.getAspects().amountOf(entry.aspect())));
        }
        return new NodeReport(node.getBlockPos(), node.isEnergized(), List.copyOf(palette));
    }

    // -- capability registration ---------------------------------------------

    public static <P extends IPart & IVisRelaySource> void registerVisSource(
            RegisterPartCapabilitiesEvent event, Class<P> partClass) {
        event.register(VisRelayCapabilities.SOURCE, (part, context) -> part, partClass);
    }

    // -- internals -----------------------------------------------------------

    private static @Nullable BlockEntityNode nodeAtEnd(
            ServerLevel level, @Nullable BlockEntityVisRelay relay) {
        if (relay == null) {
            return null;
        }
        var linked = relay.resolveSource(level);
        return linked != null && linked.source() instanceof NodeVisRelaySource node ? node.node() : null;
    }

    /** A node's state, flattened so no caller has to name a node to print one. */
    public record NodeReport(BlockPos pos, boolean energized, List<NodeAspect> palette) {}

    /** One aspect a node offers: palette name, palette amount, regen rate and stored centivis. */
    public record NodeAspect(String name, int amount, double rate, int stored) {}

    /** A relay's link state: whether it linked, how deep, and through which parent. */
    public record RelayLink(boolean linked, int depth, @Nullable BlockPos parent) {}

    /** What a relay's parent chain ends at: the source's class name and where it sits. */
    public record RelayEnd(String kind, BlockPos pos) {
        public String description() {
            return kind + " at " + pos;
        }
    }
}
