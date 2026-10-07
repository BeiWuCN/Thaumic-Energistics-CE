package thaumicenergistics_ce.compat.thaumaturge;

import appeng.api.parts.IPart;
import appeng.api.parts.RegisterPartCapabilitiesEvent;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aura.AuraHelper;
import com.leclowndu93150.thaumaturge.api.aura.IVisRelaySource;
import com.leclowndu93150.thaumaturge.api.aura.VisRelayCapabilities;
import com.leclowndu93150.thaumaturge.api.aura.VisRelayHelper;
import com.leclowndu93150.thaumaturge.content.aura.node.BlockEntityNode;
import com.leclowndu93150.thaumaturge.content.aura.node.NodeVisRelaySource;
import com.leclowndu93150.thaumaturge.content.aura.relay.BlockEntityVisRelay;
import com.leclowndu93150.thaumaturge.content.aura.relay.VisRelayNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Thaumaturge's aura, vis relay chains and node network. Version 0.4.6 made the relay
 * pull-based and deleted the reservation API along with {@code resolveAddonSource}, and since
 * callers never receive relay or node objects, walking a chain stays inside this class.
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

    /** Flux is a number on the chunk, not a thing in a slot: adding it conjures it, and there is no
     * upstream to ask whether it fits. A transfer's job is to make the target hold more, not less. */
    public static void addFlux(Level level, BlockPos pos, float amount) {
        AuraHelper.addFlux(level, pos, amount);
    }

    /** Takes up to {@code want} flux off the chunk and reports what actually came: the drawing end's
     * source is the chunk it stands in, so a short answer means there was nothing there to move. */
    public static float drainFlux(Level level, BlockPos pos, float want, boolean simulate) {
        return AuraHelper.drainFlux(level, pos, want, simulate);
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
}
