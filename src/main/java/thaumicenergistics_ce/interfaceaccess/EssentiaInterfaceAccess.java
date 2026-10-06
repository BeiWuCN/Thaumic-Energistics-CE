package thaumicenergistics_ce.interfaceaccess;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import appeng.parts.misc.InterfacePart;
import appeng.util.ConfigInventory;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.util.ThELog;

/**
 * One ME interface that carries our access card: moves essentia between the network and its neighbours.
 * <ul>
 *   <li>Runs from {@link EssentiaInterfaceRegistry}'s round, not from AE2's grid tickable service, so a
 *       card pulled out stops it without any alert from AE2.
 *   <li>Speed and cost are fixed here: {@link #ROUND_TICKS} and {@link #POINTS_PER_ROUND} per direction.
 * </ul>
 */
public final class EssentiaInterfaceAccess {

    /** Ticks between rounds. Same order as the essentia buses; AE2's own interfaces run at 5 too. */
    public static final int ROUND_TICKS = 5;

    /** Essentia, in points, moved per direction per round: 8 out and 8 in, never more. */
    public static final int POINTS_PER_ROUND = 8;

    /** AE, per point moved, priced to match {@code BlockEntityAlchemyProvider.AE_PER_ESSENTIA}. */
    public static final double AE_PER_POINT = 10.0;

    private final InterfaceLogicHost host;
    private final IManagedGridNode node;

    /**
     * Binds a controller to a live host. The host is held rather than looked up again so a round cannot
     * chase an interface that was broken between two ticks.
     */
    public EssentiaInterfaceAccess(InterfaceLogicHost host, IManagedGridNode node) {
        this.host = host;
        this.node = node;
    }

    /**
     * Whether the host is still there to work on: a block entity or part whose level or node went away
     * has to leave the registry, or every round walks a ghost.
     */
    public boolean stillValid() {
        return host.getBlockEntity() != null && node.getGrid() != null;
    }

    /**
     * One round of both directions: out of the network into a neighbour, then out of a neighbour into the
     * network. Any shortfall cancels that direction, never a half move.
     */
    public void runRound() {
        InterfaceLogic logic = host.getInterfaceLogic();
        // Pulled first and only once, so a card taken out mid-round still leaves the marks alone.
        if (!logic.getUpgrades().isInstalled(ModItems.ESSENTIA_ACCESS_CARD.get())) {
            return;
        }
        BlockEntity be = host.getBlockEntity();
        IGrid grid = node.getGrid();
        if (be == null || grid == null || !(be.getLevel() instanceof ServerLevel level)) {
            return;
        }
        MEStorage network = logic.getInventory();
        IEnergyService energy = grid.getService(IEnergyService.class);
        if (network == null || energy == null) {
            return;
        }
        Direction[] faces = faces();
        export(faces, logic.getConfig(), network, energy);
        absorb(faces, logic.getStorage(), network, energy);
    }

    /**
     * The faces to offer a neighbour, in the order a round visits them: the interface part's own side
     * alone, all six for the block form.
     */
    private Direction[] faces() {
        if (host instanceof InterfacePart part) {
            Direction side = part.getSide();
            return side == null ? new Direction[0] : new Direction[] {side};
        }
        return Direction.values();
    }

    /**
     * The essentia storage a face gives back, or {@code null} for a face that has none. Items are never
     * asked for: the player's rule is that this card moves essentia, not what a chest would take.
     */
    private @Nullable IEssentiaStorage essentiaAt(Direction face) {
        BlockEntity be = host.getBlockEntity();
        if (be == null || !(be.getLevel() instanceof ServerLevel level)) {
            return null;
        }
        BlockPos neighbour = be.getBlockPos().relative(face);
        Direction from = face.getOpposite();
        if (!level.isLoaded(neighbour)) {
            return null;
        }
        // A pipe answers TRANSPORT rather than STORAGE, so it is asked first and wrapped to fit.
        IEssentiaTransport tube = level.getCapability(EssentiaCapabilities.TRANSPORT, neighbour, from);
        if (tube != null && tube.isConnectable(from)) {
            return new TubeStorage(tube, from);
        }
        return level.getCapability(EssentiaCapabilities.STORAGE, neighbour, from);
    }

    /** The config row: every essentia mark in it is pushed out of the network into a neighbour. */
    private void export(
            Direction[] faces,
            ConfigInventory config,
            MEStorage network,
            IEnergyService energy) {
        for (int slot = 0; slot < config.size(); slot++) {
            AEKey key = config.getKey(slot);
            if (!(key instanceof AEssentiaKey essentia)) {
                continue;
            }
            Holder<IAspect> aspect = essentia.resolveAspect();
            if (aspect == null) {
                continue;
            }
            for (Direction face : faces) {
                IEssentiaStorage storage = essentiaAt(face);
                if (storage == null) {
                    continue;
                }
                int moved = push(network, storage, aspect, energy);
                if (moved > 0) {
                    break;
                }
            }
        }
    }

    /**
     * The storage row as a whitelist. All nine empty means every aspect is let in; otherwise only the
     * aspects the row lists are. Keys of other types are not ours and are passed over.
     */
    private void absorb(
            Direction[] faces,
            ConfigInventory storage,
            MEStorage network,
            IEnergyService energy) {
        List<AEKey> allowed = whitelist(storage);
        for (Direction face : faces) {
            IEssentiaStorage neighbour = essentiaAt(face);
            if (neighbour == null) {
                continue;
            }
            int budget = POINTS_PER_ROUND;
            for (AspectInstance entry : neighbour.contents().sortedByAmount()) {
                if (budget <= 0) {
                    break;
                }
                Holder<IAspect> aspect = entry.aspect();
                if (aspect == null || entry.amount() <= 0) {
                    continue;
                }
                AEssentiaKey key = AEssentiaKey.of(aspect);
                if (key == null || !mayEnter(allowed, key)) {
                    continue;
                }
                budget -= pull(neighbour, network, aspect, Math.min(budget, entry.amount()), energy);
            }
        }
    }

    /** The storage row reduced to its essentia keys; an empty answer stands for "no filter at all". */
    private List<AEKey> whitelist(ConfigInventory storage) {
        List<AEKey> listed = new ArrayList<>();
        for (int slot = 0; slot < storage.size(); slot++) {
            AEKey key = storage.getKey(slot);
            if (key instanceof AEssentiaKey) {
                listed.add(key);
            }
        }
        return listed;
    }

    /**
     * Whether the storage row lets a key in. An empty row is no filter at all: entries that are not
     * essentia never count, so a row holding only items or fluids behaves like an empty one.
     */
    public static boolean mayEnter(List<AEKey> allowed, AEKey key) {
        boolean filtered = false;
        for (AEKey entry : allowed) {
            if (!(entry instanceof AEssentiaKey)) {
                continue;
            }
            filtered = true;
            if (entry.equals(key)) {
                return true;
            }
        }
        return !filtered;
    }

    /** Network to neighbour: pay first, then take only what was paid for, then give it away. */
    private int push(
            MEStorage network, IEssentiaStorage storage, Holder<IAspect> aspect, IEnergyService energy) {
        int affordable = affordable(POINTS_PER_ROUND, energy);
        if (affordable <= 0) {
            return 0;
        }
        long fit = storage.insert(aspect, affordable, true);
        long have = network.extract(AEssentiaKey.of(aspect), affordable, Actionable.SIMULATE, actionSource());
        int wanted = (int) Math.min(Math.min(fit, have), affordable);
        if (wanted <= 0 || !pay(wanted, energy)) {
            return 0;
        }
        long taken = network.extract(AEssentiaKey.of(aspect), wanted, Actionable.MODULATE, actionSource());
        int given = (int) Math.min(taken, Integer.MAX_VALUE);
        if (given <= 0) {
            return 0;
        }
        int accepted = storage.insert(aspect, given, false);
        if (accepted < given) {
            network.insert(AEssentiaKey.of(aspect), given - accepted, Actionable.MODULATE, actionSource());
        }
        return accepted;
    }

    /** Neighbour to network: the pay-first order that keeps a refused insert from eating the essentia. */
    private int pull(
            IEssentiaStorage storage,
            MEStorage network,
            Holder<IAspect> aspect,
            int available,
            IEnergyService energy) {
        int affordable = affordable(Math.min(POINTS_PER_ROUND, available), energy);
        if (affordable <= 0) {
            return 0;
        }
        long have = storage.extract(aspect, affordable, true);
        int wanted = (int) Math.min(have, affordable);
        if (wanted <= 0 || !pay(wanted, energy)) {
            return 0;
        }
        int taken = storage.extract(aspect, wanted, false);
        if (taken <= 0) {
            return 0;
        }
        long inserted = network.insert(AEssentiaKey.of(aspect), taken, Actionable.MODULATE, actionSource());
        if (inserted < taken) {
            // The network would not take all of it; hand the rest back rather than destroy it.
            storage.insert(aspect, (int) (taken - inserted), false);
        }
        return (int) inserted;
    }

    /** What the grid can pay for, asked without spending: the clamp keeps a rich grid from overflowing. */
    private int affordable(int units, IEnergyService energy) {
        if (units <= 0) {
            return 0;
        }
        double offered =
                energy.extractAEPower(units * AE_PER_POINT, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        return (int) Math.min(units, Math.floor(offered / AE_PER_POINT + 1.0e-6));
    }

    /** Spends for the units, and answers false when the grid turned out poorer than the dry run saw. */
    private boolean pay(int units, IEnergyService energy) {
        double cost = units * AE_PER_POINT;
        double paid = energy.extractAEPower(cost, Actionable.MODULATE, PowerMultiplier.CONFIG);
        if (paid + 1.0e-6 < cost) {
            ThELog.LOG.debug("[essentia-interface] the grid paid {} of {} AE", paid, cost);
            return false;
        }
        return true;
    }

    private IActionSource actionSource() {
        // The node is the machine here; IActionHost is that one accessor, so the lambda is the whole of it.
        return IActionSource.ofMachine(() -> node.getNode());
    }

    /**
     * A pipe read as a container. Essentia on a pipe lives per face rather than as one store, so one
     * adapter serves one face and its revision is of no use to a caller that reads every round.
     */
    private record TubeStorage(IEssentiaTransport transport, Direction face) implements IEssentiaStorage {

        @Override
        public AspectList contents() {
            Holder<IAspect> held = transport.getEssentiaType(face);
            int amount = transport.getEssentiaAmount(face);
            return held == null || amount <= 0 ? AspectList.EMPTY : AspectList.EMPTY.add(held, amount);
        }

        @Override
        public int insert(Holder<IAspect> aspect, int amount, boolean simulate) {
            return transport.addEssentia(aspect, amount, face, simulate);
        }

        @Override
        public int extract(Holder<IAspect> aspect, int amount, boolean simulate) {
            return transport.takeEssentia(aspect, amount, face, simulate);
        }

        @Override
        public long contentRevision() {
            return 0;
        }
    }
}
