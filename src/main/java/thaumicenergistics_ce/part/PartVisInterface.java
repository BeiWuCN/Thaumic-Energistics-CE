package thaumicenergistics_ce.part;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.parts.p2p.P2PModels;
import appeng.parts.p2p.P2PTunnelPart;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aura.IVisRelaySource;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.compat.thaumaturge.TcAura;

/**
 * The Vis Interface: lets Thaumaturge machines draw vis out of the ME network's aura.
 * <ul>
 *   <li>An {@link IVisRelaySource} for the block the cable is on: an offer is the smaller of the aspects
 *       the reached node may sell and the centivis the energy service can afford.
 *   <li>Never asks a chain for vis itself - that loop is forbidden - so the vis is made out of AE instead.
 * </ul>
 */
public class PartVisInterface extends P2PTunnelPart<PartVisInterface> implements IVisRelaySource {

    public static final ResourceLocation MODEL_VIS_INTERFACE = ThEIds.id("parts/p2p/p2p_tunnel_vis");

    private static final P2PModels MODELS = new P2PModels(MODEL_VIS_INTERFACE);

    /** AE2's model registry does not discover the status models on its own, and a missing one is a
     * renderer crash, so every location this part can be drawn with has to be listed. */
    public static final List<ResourceLocation> MODEL_LOCATIONS = List.of(
            MODEL_VIS_INTERFACE,
            P2PModels.MODEL_STATUS_OFF,
            P2PModels.MODEL_STATUS_ON,
            P2PModels.MODEL_STATUS_HAS_CHANNEL);

    /** A rate, not a flat fee: a wand recharge and a craft differ. */
    private static final double AE_PER_VIS = 100.0;

    private static final int CENTIVIS_PER_VIS = 100;

    /** Above any network's buffer, so a simulated extraction answers with the whole buffer rather
     * than with the request. Well under {@code Integer.MAX_VALUE} centivis. */
    private static final double AE_SIMULATE_CEILING = 1.0e9;

    /** Finding either of the two cached lookups scans 17x17x17, so neither runs every tick.
     * Twenty ticks is one second. */
    private static final int UPSTREAM_POLL_INTERVAL = 20;

    /** Whether to log every vis delivery. Off unless {@code THAUMICENERGISTICS_VIS_TRACE=true}. */
    private static final boolean TRACE = "true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_VIS_TRACE"));

    private @Nullable PartVisInterface upstreamEnd;

    private long nextUpstreamLookup;

    private boolean upstreamKnown;

    private @Nullable BlockPos permitPos;

    private long nextPermitLookup;

    private boolean permitKnown;

    public PartVisInterface(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().setIdlePowerUsage(1.0);
    }

    @Override
    public IPartModel getStaticModels() {
        return MODELS.getModel(isPowered(), isActive());
    }

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(6, 6, 15, 10, 10, 16);
        bch.addBox(4, 4, 14, 12, 12, 15);
        bch.addBox(5, 5, 13, 11, 11, 14);
    }

    @Override
    public int getLightLevel() {
        return isActive() ? 8 : 0;
    }

    public @Nullable IGrid getGrid() {
        IGridNode node = getMainNode().getNode();
        return node == null ? null : node.getGrid();
    }

    private @Nullable Level visLevel() {
        return getLevel();
    }

    private BlockPos visPos() {
        return getBlockEntity().getBlockPos();
    }

    // ----- IVisRelaySource - what Thaumaturge and the Arcane Assembler see -----

    @Override
    public boolean isActive() {
        return getMainNode().isActive();
    }

    /** {@code false} when the reachable chain ends here, so the relay is sent back to the node
     * beside it: one linked to this source would have no aspect list. */
    @Override
    public boolean canSupply() {
        return isActive() && permit() != null;
    }

    @Override
    public int availableCentivis(ResourceKey<IAspect> primal) {
        if (primal == null) {
            return 0;
        }
        BlockPos node = permit();
        if (node == null || !(visLevel() instanceof ServerLevel server)) {
            return 0;
        }
        // The node's aspect list, not its store: an emptied node can be asked again.
        if (!TcAura.nodeHolds(server, node, primal)) {
            return 0;
        }
        IEnergyService energy = energy();
        if (energy == null) {
            return 0;
        }
        // One simulated extraction answers "how much can the network pay for", in centivis.
        double affordable = energy.extractAEPower(
                AE_SIMULATE_CEILING, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        return centivisFor(affordable);
    }

    /** The vis is not taken from anywhere: that AE payment is the whole exchange, so a payment that
     * comes up short hands the price back. */
    @Override
    public int drainCentivis(ResourceKey<IAspect> primal, int amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        int offered = Math.min(amount, availableCentivis(primal));
        if (offered <= 0) {
            return 0;
        }
        if (simulate) {
            return offered;
        }
        IEnergyService energy = energy();
        if (energy == null) {
            return 0;
        }
        double cost = aeCost(offered);
        double paid = energy.extractAEPower(cost, Actionable.MODULATE, PowerMultiplier.CONFIG);
        if (paid + 1.0e-6 < cost) {
            // Part of the price came out before the network ran dry; hand it back.
            energy.injectPower(paid, Actionable.MODULATE);
            return 0;
        }
        if (TRACE) {
            thaumicenergistics_ce.ThaumicEnergistics.LOG.info(
                    "[vis] supplied {} centivis of {} at {} for {} AE against the chain's aspects",
                    offered, primal.location().getPath(), visPos(), paid);
        }
        return offered;
    }

    public @Nullable VisReservation reserve(ResourceKey<IAspect> aspect, int centivis) {
        if (aspect == null || centivis <= 0) {
            return null;
        }
        int offered = Math.min(centivis, availableCentivis(aspect));
        return offered <= 0 ? null : new PendingVis(offered, aspect);
    }

    private static double aeCost(int centivis) {
        return AE_PER_VIS * centivis / CENTIVIS_PER_VIS;
    }

    private static int centivisFor(double ae) {
        return (int) Math.floor(ae * CENTIVIS_PER_VIS / AE_PER_VIS);
    }

    private @Nullable BlockPos permit() {
        if (!(visLevel() instanceof ServerLevel server)) {
            return null;
        }
        long now = server.getGameTime();
        if (permitKnown && now < nextPermitLookup) {
            return permitPos;
        }
        permitKnown = true;
        nextPermitLookup = now + UPSTREAM_POLL_INTERVAL;
        PartVisInterface end = upstream(server);
        permitPos = end == null ? null : TcAura.nodeBehind(server, end.visPos());
        return permitPos;
    }

    private @Nullable PartVisInterface upstream(ServerLevel server) {
        long now = server.getGameTime();
        if (upstreamKnown && now < nextUpstreamLookup) {
            return upstreamEnd;
        }
        upstreamKnown = true;
        nextUpstreamLookup = now + UPSTREAM_POLL_INTERVAL;
        upstreamEnd = canReachRelay(server) ? this : partnerWithRelay(server);
        return upstreamEnd;
    }

    private boolean canReachRelay(ServerLevel server) {
        // A relay resolving to nothing leads nowhere; this scans the relay block, not the part.
        return TcAura.relayResolves(server, visPos());
    }

    private @Nullable PartVisInterface partnerWithRelay(ServerLevel server) {
        PartVisInterface input = getInput();
        if (input != null && input != this && input.canReachRelay(server)) {
            return input;
        }
        for (PartVisInterface output : getOutputStream().toList()) {
            if (output != this && output.canReachRelay(server)) {
                return output;
            }
        }
        return null;
    }

    private @Nullable IEnergyService energy() {
        IGrid grid = getGrid();
        return grid == null ? null : grid.getService(IEnergyService.class);
    }

    public @Nullable BlockPos upstreamPosition() {
        if (!(visLevel() instanceof ServerLevel server)) {
            return null;
        }
        PartVisInterface source = upstream(server);
        return source == null ? null : source.visPos();
    }

    public @Nullable BlockPos permitPosition() {
        return permit();
    }

    private final class PendingVis implements VisReservation {

        private final int centivis;
        private final ResourceKey<IAspect> aspect;

        /** True once the vis has actually been handed over, so a second commit cannot charge twice. */
        private boolean committed;

        private PendingVis(int centivis, ResourceKey<IAspect> aspect) {
            this.centivis = centivis;
            this.aspect = aspect;
        }

        @Override
        public int amount() {
            return centivis;
        }

        /** Vis handed over against a payment that then failed would be vis created from nothing. */
        @Override
        public int commit() {
            if (committed) {
                return 0;
            }
            int taken = drainCentivis(aspect, centivis, false);
            if (taken > 0) {
                committed = true;
            }
            return taken;
        }

        @Override
        public void close() {
            // Nothing was taken on reserve - see the note on VisReservation.
        }
    }
}
