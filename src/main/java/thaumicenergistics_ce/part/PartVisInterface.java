package thaumicenergistics_ce.part;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.parts.p2p.P2PModels;
import appeng.parts.p2p.P2PTunnelPart;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aura.IVisRelaySource;
import com.leclowndu93150.thaumaturge.api.aura.VisRelayHelper;
import com.leclowndu93150.thaumaturge.api.aura.VisRelaySourceContext;
import com.leclowndu93150.thaumaturge.api.aura.VisRelaySources;
import com.leclowndu93150.thaumaturge.content.aura.node.BlockEntityNode;
import com.leclowndu93150.thaumaturge.content.aura.relay.BlockEntityVisRelay;
import com.leclowndu93150.thaumaturge.content.aura.relay.VisRelayNetwork;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.ThEIds;

/**
 * The Vis Interface: lets Thaumaturge machines draw vis out of the ME network's aura.
 *
 * <p>It registers itself as a {@link IVisRelaySource} for the block the cable is on, so a machine beside it
 * asks here instead of the aura, and what is handed over is paid for out of the network's energy. The two
 * halves of an offer come from different places: which aspects may be sold is the relay chain's answer (the
 * energized node's aspect list, so an aspect that node has never held is refused), and how many centivis may
 * be sold is the network's answer (what the energy service can afford). Selling energy-backed vis alone used
 * to fill all six of a machine's bars beside a node holding three; a node on its own is no use either, since
 * it accrues only about 0.19 vis a second - a trickle for a wand, not for an assembler.
 *
 * <p>It extends {@code P2PTunnelPart} for the model and the frequency handling only: an end that can reach a
 * relay answers for itself, and one single end beside a machine works with no second end anywhere.
 */
public class PartVisInterface extends P2PTunnelPart<PartVisInterface>
        implements IGridTickable, IVisRelaySource {

    /** The P2P model this part draws itself with, and the status models AE2 layers over it. */
    public static final ResourceLocation MODEL_VIS_INTERFACE = ThEIds.id("parts/p2p/p2p_tunnel_vis");

    private static final P2PModels MODELS = new P2PModels(MODEL_VIS_INTERFACE);

    /**
     * Every model location this part can be drawn with, for registration. AE2's model registry does not
     * discover its status models on its own, and a missing one is a renderer crash that names no code of ours.
     */
    public static List<ResourceLocation> getModelLocations() {
        return List.of(
                MODEL_VIS_INTERFACE,
                P2PModels.MODEL_STATUS_OFF,
                P2PModels.MODEL_STATUS_ON,
                P2PModels.MODEL_STATUS_HAS_CHANNEL);
    }

    /**
     * What one vis costs in AE - the only brake on the part, since the quantity sold is otherwise whatever
     * the network can afford. A rate rather than a flat fee, so a wand recharge and a craft are not charged
     * the same.
     */
    private static final double AE_PER_VIS = 100.0;

    /** Centivis in one vis. Thaumaturge counts vis in hundredths and the aura is a whole-vis float. */
    private static final int CENTIVIS_PER_VIS = 100;

    /** How often the registration is re-checked. Vis is a low-frequency business; nothing here is urgent. */
    private static final int TICK_RATE = 30;

    /**
     * How long the answer to "which end of this tunnel can reach a relay" is kept. Finding out means
     * scanning the 17x17x17 around a position, and the answer only changes when a relay block is placed or
     * broken. Same cadence as the Arcane Assembler's own relay-chain poll.
     */
    private static final int UPSTREAM_POLL_INTERVAL = 20;

    /**
     * Whether to log every vis delivery; off unless {@code THAUMICENERGISTICS_VIS_TRACE=true}. Without it
     * there is no way to tell a machine fed by this part from one fed by the aura or a bare node.
     */
    private static final boolean TRACE = "true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_VIS_TRACE"));

    /** The registration handed to Thaumaturge, or {@code null} when this part is not registered. */
    private @Nullable VisRelaySourceRegistrationHandle registration;

    /**
     * True while this part is asking a relay chain for vis. That chain can end at this very part, and without
     * the guard the reservation would call itself until the stack ran out.
     */
    private boolean busy;

    /** The end of this tunnel that can reach a relay chain, and when that was last worked out. */
    private @Nullable PartVisInterface upstreamEnd;

    private long nextUpstreamLookup;

    private boolean upstreamKnown;

    public PartVisInterface(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().setIdlePowerUsage(1.0);
        getMainNode().addService(IGridTickable.class, this);
    }

    @Override
    public IPartModel getStaticModels() {
        return MODELS.getModel(isPowered(), isActive());
    }

    /** A small plug on the face it is mounted on, so it reads as fitted to the cable rather than a block. */
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

    /**
     * The world this part is in, or {@code null} while it is being placed or removed - a part reaches the
     * world through its host, which may not be there yet.
     */
    private @Nullable Level visLevel() {
        return getLevel();
    }

    /**
     * Where this part is, for the aura: the host block's position, not a position offset towards the part's
     * face. That is where a machine asks and where the source is registered, so a reservation and its commit
     * always name the same place.
     */
    private BlockPos visPos() {
        return getBlockEntity().getBlockPos();
    }

    // ----- IVisRelaySource - what Thaumaturge machines see -----

    @Override
    public boolean isValid() {
        return getBlockEntity() != null;
    }

    /** Whether this part is on a powered, connected network. Nothing can be paid for without one. */
    @Override
    public boolean isActive() {
        return getMainNode().isActive();
    }

    /**
     * Always linked: the useful case is a single part beside a single machine, with no second end anywhere.
     */
    @Override
    public boolean isLinked() {
        return true;
    }

    /**
     * Offers vis without taking it: the chain says which aspects it can lend, the network says how much it
     * can pay for, and the smaller answer is the offer. Nothing moves here - the energy is only asked with
     * {@link Actionable#SIMULATE}; the taking happens in `commit`.
     */
    @Override
    public @Nullable Reservation reserve(ResourceKey<IAspect> aspect, int centivis) {
        if (aspect == null || centivis <= 0 || busy || !isActive()) {
            return null;
        }
        if (!(visLevel() instanceof ServerLevel server)) {
            return null;
        }
        PartVisInterface source = upstream(server);
        if (source == null) {
            return null;
        }
        IEnergyService energy = energy();
        if (energy == null) {
            return null;
        }
        BlockEntityNode node = nodeBehind(server, source);
        boolean lend = node != null;
        int available;
        if (lend) {
            // The node's own aspect list, not its store or allowance: a node that has held earth can be
            // asked for earth at any time, even when it happens to be empty.
            if (node.getAspectsBase().amountOf(aspect, server.registryAccess()) <= 0) {
                return null;
            }
            available = centivis;
        } else {
            // Another addon source has no aspect list to lend, so ask what it would actually give.
            available = ask(source, server, aspect, centivis, true);
            if (available <= 0) {
                return null;
            }
        }
        int offered = Math.min(centivis, available);
        // One extraction answers both "can it pay" and "how much of the request can it pay for".
        double affordable = energy.extractAEPower(
                aeCost(offered), Actionable.SIMULATE, PowerMultiplier.CONFIG);
        offered = Math.min(offered, centivisFor(affordable));
        if (offered <= 0) {
            return null;
        }
        return new PendingVis(offered, aeCost(offered), aspect, lend ? null : source);
    }

    /**
     * The energized node at the end of the chain this end can reach, or {@code null} when it ends at
     * something else. {@code resolveSource} already answers "can this supply", so a chain resting on a dead
     * node lends nothing.
     */
    private static @Nullable BlockEntityNode nodeBehind(ServerLevel server, PartVisInterface end) {
        BlockEntityVisRelay relay = VisRelayNetwork.findRelayNear(server, end.visPos());
        return relay == null ? null : relay.resolveSource(server);
    }

    private static double aeCost(int centivis) {
        return AE_PER_VIS * centivis / CENTIVIS_PER_VIS;
    }

    private static int centivisFor(double ae) {
        return (int) Math.floor(ae * CENTIVIS_PER_VIS / AE_PER_VIS);
    }

    /**
     * Asks one end of this tunnel for vis from the relay chain it can reach. Used only when the chain ends
     * at another addon source, which has no aspect list to lend against.
     *
     * @param simulate true to ask what the chain would give, false to take it
     */
    private int ask(PartVisInterface end, ServerLevel server, ResourceKey<IAspect> aspect, int centivis,
            boolean simulate) {
        if (busy) {
            return 0;
        }
        busy = true;
        try {
            return VisRelayHelper.drainCentivis(server, end.visPos(), aspect, centivis, simulate);
        } finally {
            busy = false;
        }
    }

    /**
     * The end of this tunnel that can reach a relay chain, worked out at most once a second. This end first
     * - a part placed beside a relay is the ordinary case - then the ends it shares a frequency with.
     */
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
        BlockEntityVisRelay relay = VisRelayNetwork.findRelayNear(server, visPos());
        // A relay resolving to neither a node nor an addon source leads nowhere, so it does not count.
        return relay != null && (relay.resolveSource(server) != null || relay.resolveAddonSource(server) != null);
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

    /**
     * Where this part is drawing its vis from, for diagnostics - named for the log line, see
     * {@code VisRelaySelfTest}.
     */
    public @Nullable BlockPos upstreamPosition() {
        if (!(visLevel() instanceof ServerLevel server)) {
            return null;
        }
        PartVisInterface source = upstream(server);
        return source == null ? null : source.visPos();
    }

    /** The aspect list this part is selling against, for diagnostics. See {@code VisRelaySelfTest}. */
    public @Nullable BlockEntityNode node() {
        if (!(visLevel() instanceof ServerLevel server)) {
            return null;
        }
        PartVisInterface source = upstream(server);
        return source == null ? null : nodeBehind(server, source);
    }

    /**
     * A claim on the network and on either a relay chain or its aspect list, which either becomes real or
     * lapses. Nothing has been taken when this is built, so {@code close} has nothing to release.
     */
    private final class PendingVis implements Reservation {

        private final int centivis;
        private final double cost;
        private final ResourceKey<IAspect> aspect;
        /** The end to drain from, or {@code null} to materialise against the chain's aspect list instead. */
        private final @Nullable PartVisInterface source;

        /** True once the vis has actually been handed over, so a second commit cannot charge twice. */
        private boolean committed;

        private PendingVis(int centivis, double cost, ResourceKey<IAspect> aspect,
                @Nullable PartVisInterface source) {
            this.centivis = centivis;
            this.cost = cost;
            this.aspect = aspect;
            this.source = source;
        }

        @Override
        public int amount() {
            return centivis;
        }

        /**
         * Takes the AE from the network, and the vis from the chain only when the chain is being drained. The
         * payment goes first in both cases: vis handed over against a payment that then failed would be vis
         * created out of nothing. A chain that moved under us hands over less, and the difference is refunded
         * rather than kept.
         */
        @Override
        public int commit() {
            if (committed) {
                return 0;
            }
            IEnergyService energy = energy();
            if (energy == null) {
                return 0;
            }
            double paid = energy.extractAEPower(cost, Actionable.MODULATE, PowerMultiplier.CONFIG);
            if (paid + 1.0e-6 < cost) {
                // Part of the price came out before the network ran dry; hand it back rather than charge.
                energy.injectPower(paid, Actionable.MODULATE);
                return 0;
            }
            if (source == null) {
                // Materialised against the chain's aspects: the payment above is the whole exchange.
                committed = true;
                if (TRACE) {
                    thaumicenergistics_ce.ThaumicEnergistics.LOG.info(
                            "[vis] supplied {} centivis of {} at {} for {} AE against the chain's aspects",
                            centivis, aspect.location().getPath(), visPos(), paid);
                }
                return centivis;
            }
            if (!(source.visLevel() instanceof ServerLevel server)) {
                energy.injectPower(paid, Actionable.MODULATE);
                return 0;
            }
            int taken = ask(source, server, aspect, centivis, false);
            if (taken <= 0) {
                // The chain had nothing left after all: the whole fare goes back.
                energy.injectPower(paid, Actionable.MODULATE);
                return 0;
            }
            if (taken < centivis) {
                energy.injectPower(aeCost(centivis - taken), Actionable.MODULATE);
            }
            committed = true;
            if (TRACE) {
                thaumicenergistics_ce.ThaumicEnergistics.LOG.info(
                        "[vis] carried {} centivis of {} from {} to {} for {} AE",
                        taken, aspect.location().getPath(), source.visPos(), visPos(), paid);
            }
            return taken;
        }

        @Override
        public void close() {
            // Nothing was taken on reserve - see the class note on PendingVis.
        }
    }

    // ----- Registration with Thaumaturge, and ticking -----

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(TICK_RATE, TICK_RATE, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (!isActive()) {
            unregisterSource();
            return TickRateModulation.SAME;
        }
        if (registration == null) {
            registerSource();
        }
        return TickRateModulation.SAME;
    }

    /**
     * Tells Thaumaturge that this position can supply vis, against the block the cable is on rather than the
     * cable's own position: a machine looks at the blocks around it, and the cable is not one of them.
     */
    private void registerSource() {
        if (visLevel() == null || visLevel().isClientSide() || registration != null) {
            return;
        }
        if (!(visLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        try {
            var handle = VisRelaySources.register(
                    new VisRelaySourceContext(serverLevel, hostIdentity(), visPos()), this);
            registration = new VisRelaySourceRegistrationHandle(handle);
            if (TRACE) {
                thaumicenergistics_ce.ThaumicEnergistics.LOG.info(
                        "[vis] registered a vis source at {} for network {}", visPos(), hostIdentity());
            }
        } catch (RuntimeException | LinkageError e) {
            // Thaumaturge absent, or its registry refused: log rather than break the tick loop.
            thaumicenergistics_ce.ThaumicEnergistics.LOG.error(
                    "[vis] could not register a vis source at {}: {}", visPos(), e.toString());
        }
    }

    /** Withdraws the registration, so a part that lost power stops being asked for vis. */
    private void unregisterSource() {
        if (registration != null) {
            registration.close();
            registration = null;
        }
    }

    /** Identity for the registration, so two parts on one cable are two sources rather than one. */
    private java.util.UUID hostIdentity() {
        return java.util.UUID.nameUUIDFromBytes(
                ("thaumicenergistics_ce:vis_interface:" + visPos().asLong()).getBytes());
    }

    /**
     * Keeps the registration handle without naming Thaumaturge's interface again, which could gain or lose
     * members.
     */
    private static final class VisRelaySourceRegistrationHandle {

        private final com.leclowndu93150.thaumaturge.api.aura.VisRelaySourceRegistration handle;

        VisRelaySourceRegistrationHandle(
                com.leclowndu93150.thaumaturge.api.aura.VisRelaySourceRegistration handle) {
            this.handle = handle;
        }

        void close() {
            handle.close();
        }
    }

    // ----- Lifecycle and persistence -----


    @Override
    public void removeFromWorld() {
        unregisterSource();
        super.removeFromWorld();
    }

    @Override
    public void readFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        super.readFromNBT(tag, registries);
        // Nothing of this part's own is persisted; the registration is rebuilt from its position next tick.
    }
}
