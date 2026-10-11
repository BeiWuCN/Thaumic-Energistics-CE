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
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

public final class TcAura {
    private TcAura() {}


    public static float vis(Level level, BlockPos pos) {
        return AuraHelper.getVis(level, pos);
    }

    public static int auraBase(Level level, BlockPos pos) {
        return AuraHelper.getAuraBase(level, pos);
    }

    public static float drainVis(Level level, BlockPos pos, float want, boolean simulate) {
        return AuraHelper.drainVis(level, pos, want, simulate);
    }

    public static float drainVis(Level level, BlockPos pos, float want, TransactionContext transaction) {
        return AuraHelper.drainVis(level, pos, want, transaction);
    }

    public static void addFlux(Level level, BlockPos pos, float amount) {
        AuraHelper.addFlux(level, pos, amount);
    }

    public static float drainFlux(Level level, BlockPos pos, float want, boolean simulate) {
        return AuraHelper.drainFlux(level, pos, want, simulate);
    }


    public static boolean relayWithinReach(ServerLevel level, BlockPos consumer) {
        return VisRelayNetwork.findRelayNear(level, consumer) != null;
    }

    public static boolean relayResolves(ServerLevel level, BlockPos consumer) {
        BlockEntityVisRelay relay = VisRelayNetwork.findRelayNear(level, consumer);
        return relay != null && relay.resolveSource(level) != null;
    }

    public static @Nullable BlockPos nodeBehind(ServerLevel level, BlockPos consumer) {
        BlockEntityNode node = nodeAtEnd(level, VisRelayNetwork.findRelayNear(level, consumer));
        return node == null || !node.isEnergized() ? null : node.getBlockPos();
    }

    public static @Nullable BlockPos energizedNodeInFront(ServerLevel level, BlockPos host,
            net.minecraft.core.Direction side) {
        BlockPos pos = host.relative(side);
        return level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof BlockEntityNode node
                && node.isEnergized() ? pos : null;
    }

    public static boolean nodeHolds(ServerLevel level, BlockPos nodePos, ResourceKey<IAspect> primal) {
        return level.hasChunkAt(nodePos) && level.getBlockEntity(nodePos) instanceof BlockEntityNode node
                && node.isEnergized() && node.capacity().amountOf(primal, level.registryAccess()) > 0;
    }

    public static int drainCentivis(ServerLevel level, BlockPos consumer,
            ResourceKey<IAspect> primal, int amount, boolean simulate) {
        return VisRelayHelper.drainCentivis(level, consumer, primal, amount, simulate);
    }


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
        for (AspectInstance entry : node.capacity().entries()) {
            String name = entry.aspect().unwrapKey()
                    .map(key -> key.identifier().getPath())
                    .orElse("?");
            palette.add(new NodeAspect(name, entry.amount(),
                    node.centivisRate(entry.aspect()),
                    node.getAspects().amountOf(entry.aspect())));
        }
        return new NodeReport(node.getBlockPos(), node.isEnergized(), List.copyOf(palette));
    }


    public static <P extends IPart & IVisRelaySource> void registerVisSource(
            RegisterPartCapabilitiesEvent event, Class<P> partClass) {
        event.register(VisRelayCapabilities.SOURCE, (part, context) -> part, partClass);
    }


    private static @Nullable BlockEntityNode nodeAtEnd(
            ServerLevel level, @Nullable BlockEntityVisRelay relay) {
        if (relay == null) {
            return null;
        }
        var linked = relay.resolveSource(level);
        return linked != null && linked.source() instanceof NodeVisRelaySource node ? node.node() : null;
    }

    public record NodeReport(BlockPos pos, boolean energized, List<NodeAspect> palette) {}

    public record NodeAspect(String name, int amount, double rate, int stored) {}

    public record RelayLink(boolean linked, int depth, @Nullable BlockPos parent) {}

    public record RelayEnd(String kind, BlockPos pos) {
        public String description() {
            return kind + " at " + pos;
        }
    }
}
