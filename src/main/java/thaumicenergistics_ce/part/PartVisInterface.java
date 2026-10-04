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
 *   <li>An {@link IVisRelaySource} for the block the cable is on, so a machine beside it asks here
 *       instead of the aura; what it hands over is paid out of the network's energy. Thaumaturge
 *       finds sources through a block capability, which {@code TcAura} registers once
 *       for the whole part class: a part is not a block entity and cannot be registered by position.
 *   <li>An offer is the smaller of two answers: which aspects may be sold, from the energized node
 *       on the relay chain this part reaches, and how many centivis the energy service can afford.
 *   <li>Extends {@code P2PTunnelPart} for the model and frequency handling only; one end beside a
 *       machine works with no second end anywhere.
 *   <li>Never asks a relay chain for vis itself. A relay resolves to whichever source is nearest,
 *       and a source draining the network it sits on is a loop Thaumaturge's contract forbids; the
 *       vis here is made out of AE instead, against the chain's aspect list.
 * </ul>
 */
public class PartVisInterface extends P2PTunnelPart<PartVisInterface> implements IVisRelaySource {

    /** The P2P model this part draws itself with, and the status models AE2 layers over it. */
    public static final ResourceLocation MODEL_VIS_INTERFACE = ThEIds.id("parts/p2p/p2p_tunnel_vis");

    private static final P2PModels MODELS = new P2PModels(MODEL_VIS_INTERFACE);

    /** Every model location this part can be drawn with: AE2's model registry does not discover the
     * status models on its own, and a missing one is a renderer crash. */
    public static final List<ResourceLocation> MODEL_LOCATIONS = List.of(
            MODEL_VIS_INTERFACE,
            P2PModels.MODEL_STATUS_OFF,
            P2PModels.MODEL_STATUS_ON,
            P2PModels.MODEL_STATUS_HAS_CHANNEL);

    /** What one vis costs in AE. A rate, not a flat fee: a wand recharge and a craft differ. */
    private static final double AE_PER_VIS = 100.0;

    /** Centivis in one vis: Thaumaturge counts vis in hundredths. */
    private static final int CENTIVIS_PER_VIS = 100;

    /** More AE than any network holds, so a simulated extraction answers with the whole buffer
     * rather than with the request. Well under {@code Integer.MAX_VALUE} centivis. */
    private static final double AE_SIMULATE_CEILING = 1.0e9;

    /** How long the two lookups below are kept. Finding either scans 17x17x17, same cadence as the
     * Arcane Assembler's own relay-chain poll, and neither answer changes within a tick. */
    private static final int UPSTREAM_POLL_INTERVAL = 20;

    /** Whether to log every vis delivery. Off unless {@code THAUMICENERGISTICS_VIS_TRACE=true}. */
    private static final boolean TRACE = "true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_VIS_TRACE"));

    /** The end of this tunnel that can reach a relay chain; recomputed at most once a second. */
    private @Nullable PartVisInterface upstreamEnd;

    private long nextUpstreamLookup;

    private boolean upstreamKnown;

    /** Where the node this part sells against sits, read off the chain the upstream end reaches. */
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

    /** A small plug on the mounted face, so it reads as fitted to the cable rather than a block. */
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

    /** The world this part is in, or {@code null} while it is being placed or removed. */
    private @Nullable Level visLevel() {
        return getLevel();
    }

    /** Where this part is, for the aura: the host block's position, not one offset towards the part's
     * face, so a reservation and its commit name the same place. */
    private BlockPos visPos() {
        return getBlockEntity().getBlockPos();
    }

    // ----- IVisRelaySource - what Thaumaturge and the Arcane Assembler see -----

    /** Whether this part is on a powered, connected network. Nothing can be paid for without one. */
    @Override
    public boolean isActive() {
        return getMainNode().isActive();
    }

    /** Whether relays may link to and drain this part.
     *
     * <p>This is {@code false} whenever the chain the part can reach ends at the part itself, and that
     * is deliberate: relays link to the nearest source, this part is one, and a relay linked to it
     * would have no node left to read an aspect list from. Reporting {@code false} sends the relay
     * back to the node beside it, after which this part reports {@code true} and stays reachable
     * through that relay - a chain of one source that is not this one. */
    @Override
    public boolean canSupply() {
        return isActive() && permit() != null;
    }

    /** How much this part could hand over right now, without taking anything. */
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

    /** Hands over vis made out of AE, against the chain's aspect list. The vis is not taken from
     * anywhere: that payment is the whole exchange, so a failed payment hands the price back. */
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

    /** Offers vis without taking it: what the chain allows and the network can pay for. The energy
     * is only asked with {@link Actionable#SIMULATE}, and nothing is created before {@code commit}. */
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

    /** Where the node this part sells against sits, kept for {@link #UPSTREAM_POLL_INTERVAL} ticks.
     *
     * <p>{@code null} when the chain this end can reach ends somewhere else. A chain ending at an
     * addon source that is not a node has no aspect list to sell against, so that is no permit
     * either - including when that source is this part. */
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

    /** The end of this tunnel that can reach a relay chain, worked out at most once a second. */
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

    /** Where this part draws its vis from, for diagnostics, as named in {@code VisRelaySelfTest}. */
    public @Nullable BlockPos upstreamPosition() {
        if (!(visLevel() instanceof ServerLevel server)) {
            return null;
        }
        PartVisInterface source = upstream(server);
        return source == null ? null : source.visPos();
    }

    /** Where the node this part sells against sits, for diagnostics. See {@code VisRelaySelfTest}. */
    public @Nullable BlockPos permitPosition() {
        return permit();
    }

    /** A claim on a relay chain's aspect list and on the network's energy; nothing is taken before
     * {@code commit}, so {@code close} releases nothing. */
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

        /** Pays the AE and hands the vis over in one step: vis handed over against a payment that
         * then failed would be vis created from nothing. */
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
            // Nothing was taken on reserve - see the note on PendingVis.
        }
    }
}
