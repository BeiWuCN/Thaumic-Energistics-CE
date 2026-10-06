package thaumicenergistics_ce.interfaceaccess;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
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
 * One ME interface that carries our access card: pulls essentia from the containers it touches into the
 * network, and empties both rows when the card comes out. The marks live in the config row and stay.
 * <ul>
 *   <li>One direction only: the essentia terminal already takes essentia out of the network.
 *   <li>Driven by the registry's round rather than AE2's tickable service, so a pulled card stops it.
 * </ul>
 */
public final class EssentiaInterfaceAccess {

    /** Ticks between rounds. Same order as the essentia buses; AE2's own interfaces run at 5 too. */
    public static final int ROUND_TICKS = 5;

    /** Essentia, in points, pulled out of the neighbours per round: 8, never more. */
    public static final int POINTS_PER_ROUND = 8;

    /** AE, per point moved, priced to match {@code BlockEntityAlchemyProvider.AE_PER_ESSENTIA}. */
    public static final double AE_PER_POINT = 10.0;

    private final InterfaceLogicHost host;
    private final IManagedGridNode node;

    /** Set by the first round, which is the only one that clears an earlier build's marks. */
    private boolean storageRowCleared;

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
     * One round: every marked aspect is pulled out of one neighbour into the network, and a shortfall
     * cancels that move rather than halving it.
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
        // The grid's own storage, not logic.getInventory(): with a config row that one answers with the
        // interface's storage row, so the same point lands in the network and in the interface at once.
        MEStorage network = grid.getStorageService().getInventory();
        IEnergyService energy = grid.getService(IEnergyService.class);
        if (network == null || energy == null) {
            return;
        }
        cleanStorageRow(logic.getStorage());
        absorb(faces(), logic.getConfig(), network, energy);
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

    /**
     * Drops aspects an earlier build of this card let JEI write into the storage row. Once only: later
     * rounds leave the row alone, since an aspect in it may be AE2's own stock of the mark by then.
     */
    private void cleanStorageRow(ConfigInventory storage) {
        if (storageRowCleared) {
            return;
        }
        storageRowCleared = true;
        for (int slot = 0; slot < storage.size(); slot++) {
            if (storage.getKey(slot) instanceof AEssentiaKey) {
                ThELog.LOG.info("[essentia-interface] clearing a stale aspect in storage slot {}", slot);
                storage.setStack(slot, null);
            }
        }
    }

    /**
     * The config row as a whitelist. A row with no aspect in it pulls every aspect; otherwise only the
     * aspects it lists are. Keys of other types are not ours and are passed over.
     */
    private void absorb(
            Direction[] faces,
            ConfigInventory config,
            MEStorage network,
            IEnergyService energy) {
        List<AEKey> allowed = whitelist(config);
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

    /** The config row reduced to its essentia keys; an empty answer stands for "no filter at all". */
    private List<AEKey> whitelist(ConfigInventory row) {
        List<AEKey> listed = new ArrayList<>();
        for (int slot = 0; slot < row.size(); slot++) {
            AEKey key = row.getKey(slot);
            if (key instanceof AEssentiaKey) {
                listed.add(key);
            }
        }
        return listed;
    }

    /**
     * Whether the config row lets a key in. An empty row is no filter at all: entries that are not
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

    /**
     * Empties both rows of an interface whose card has just come out: the marks go, and the storage row
     * is handed back to the grid. An aspect the grid refuses is dropped; a key of another type stays.
     */
    public static void releaseRows(
            ConfigInventory config,
            ConfigInventory storage,
            @Nullable MEStorage network,
            IActionSource source) {
        config.clear();
        for (int slot = 0; slot < storage.size(); slot++) {
            GenericStack held = storage.getStack(slot);
            if (held == null) {
                continue;
            }
            long rest = held.amount() - returnToNetwork(held, network, source);
            if (rest <= 0) {
                storage.setStack(slot, null);
            } else if (held.what() instanceof AEssentiaKey) {
                ThELog.LOG.info(
                        "[essentia-interface] discarding {} of {} as the card comes out", rest, held.what());
                storage.setStack(slot, null);
            } else {
                storage.setStack(slot, new GenericStack(held.what(), rest));
            }
        }
    }

    /**
     * Clears the aspects out of a storage row that is about to be dropped, so that breaking an interface
     * never puts one on the ground. An aspect has no item to be dropped as; the rest goes to the grid.
     */
    public static void rescueEssentia(
            ConfigInventory storage,
            @Nullable MEStorage network,
            IActionSource source) {
        for (int slot = 0; slot < storage.size(); slot++) {
            GenericStack held = storage.getStack(slot);
            if (held == null || !(held.what() instanceof AEssentiaKey)) {
                continue;
            }
            long rest = held.amount() - returnToNetwork(held, network, source);
            if (rest > 0) {
                ThELog.LOG.info(
                        "[essentia-interface] discarding {} of {} as the interface goes", rest, held.what());
            }
            storage.setStack(slot, null);
        }
    }

    /** Whether a row holds an aspect, which is how an interface of ours is told from a plain one. */
    public static boolean holdsEssentia(ConfigInventory row) {
        for (int slot = 0; slot < row.size(); slot++) {
            if (row.getKey(slot) instanceof AEssentiaKey) {
                return true;
            }
        }
        return false;
    }

    /** What the grid takes of a held stack; a grid that is gone or full takes nothing. */
    private static long returnToNetwork(
            GenericStack held,
            @Nullable MEStorage network,
            IActionSource source) {
        if (network == null) {
            return 0;
        }
        return network.insert(held.what(), held.amount(), Actionable.MODULATE, source);
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
