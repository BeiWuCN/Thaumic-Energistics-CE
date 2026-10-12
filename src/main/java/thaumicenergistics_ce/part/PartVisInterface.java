package thaumicenergistics_ce.part;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.parts.p2p.P2PTunnelPart;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aura.IVisRelaySource;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.compat.thaumaturge.TcAura;
import thaumicenergistics_ce.util.ThETransaction;

public class PartVisInterface extends P2PTunnelPart<PartVisInterface> implements IVisRelaySource {

    public static final Identifier MODEL_VIS_INTERFACE = ThEIds.id("parts/p2p/p2p_tunnel_vis");

    public static final Identifier MODEL_STATUS_OFF =
            Identifier.fromNamespaceAndPath("ae2", "part/display_status_off");

    public static final Identifier MODEL_STATUS_ON =
            Identifier.fromNamespaceAndPath("ae2", "part/display_status_on");

    public static final Identifier MODEL_STATUS_HAS_CHANNEL =
            Identifier.fromNamespaceAndPath("ae2", "part/display_status_has_channel");

    public static final List<Identifier> MODEL_LOCATIONS = List.of(
            MODEL_VIS_INTERFACE, MODEL_STATUS_OFF, MODEL_STATUS_ON, MODEL_STATUS_HAS_CHANNEL);

    private static final double AE_PER_VIS = 100.0;

    private static final int CENTIVIS_PER_VIS = 100;

    private static final double AE_SIMULATE_CEILING = 1.0e9;

    private static final int UPSTREAM_POLL_INTERVAL = 20;

    private @Nullable PartVisInterface upstreamEnd;

    private long nextUpstreamLookup;

    private boolean upstreamKnown;

    public PartVisInterface(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().setIdlePowerUsage(1.0);
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

    @Override
    public boolean isActive() {
        return getMainNode().isActive();
    }

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
        if (!TcAura.nodeHolds(server, node, primal)) {
            return 0;
        }
        IEnergyService energy = energy();
        if (energy == null) {
            return 0;
        }
        double affordable = energy.extractAEPower(
                AE_SIMULATE_CEILING, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        return centivisFor(affordable);
    }

    @Override
    public int drainCentivis(ResourceKey<IAspect> primal, int amount, TransactionContext transaction) {
        if (amount <= 0) {
            return 0;
        }
        int offered = Math.min(amount, availableCentivis(primal));
        if (offered <= 0) {
            return 0;
        }
        IEnergyService energy = energy();
        if (energy == null) {
            return 0;
        }
        double cost = aeCost(offered);
        double paid = energy.extractAEPower(cost, Actionable.MODULATE, PowerMultiplier.CONFIG);
        if (paid + 1.0e-6 < cost) {
            energy.injectPower(paid, Actionable.MODULATE);
            return 0;
        }
        new net.neoforged.neoforge.transfer.transaction.SnapshotJournal<Double>() {
            private double charged;

            void record() {
                updateSnapshots(transaction);
                charged = paid;
            }

            @Override
            protected Double createSnapshot() {
                return charged;
            }

            @Override
            protected void revertToSnapshot(Double snapshot) {
                energy.injectPower(charged - snapshot, Actionable.MODULATE);
                charged = snapshot;
            }
        }.record();

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
        PartVisInterface end = upstream(server);
        return end == null ? null : end.sourceNode(server);
    }

    private @Nullable BlockPos sourceNode(ServerLevel server) {
        BlockPos direct = TcAura.energizedNodeInFront(server, visPos(), getSide());
        return direct != null ? direct : TcAura.nodeBehind(server, visPos());
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
        return sourceNode(server) != null;
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

        private boolean committed;

        private PendingVis(int centivis, ResourceKey<IAspect> aspect) {
            this.centivis = centivis;
            this.aspect = aspect;
        }

        @Override
        public int amount() {
            return centivis;
        }

        @Override
        public int commit() {
            if (committed) {
                return 0;
            }
            int taken = ThETransaction.apply(transaction -> drainCentivis(aspect, centivis, transaction));
            if (taken > 0) {
                committed = true;
            }
            return taken;
        }

        @Override
        public void close() {
        }
    }
}
