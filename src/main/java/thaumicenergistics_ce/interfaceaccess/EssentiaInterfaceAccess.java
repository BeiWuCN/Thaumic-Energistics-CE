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
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.util.ThELog;

/**
 * One ME interface that carries our access card: pulls essentia from the containers it touches into the
 * network, and empties both rows when the card comes out. The marks live in the config row and
 * stay. The flow is one direction only, because the essentia terminal already takes essentia out of
 * the network. The access is driven by the registry's round rather than AE2's tickable service, so
 * a pulled card stops it.
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
     * Drops aspects an earlier build of this card let JEI write into the storage row. Once only: later
     * rounds leave the row alone, since an aspect in it may be AE2's own stock of the mark by then.
     */
    private void cleanStorageRow(ConfigInventory storage) {
        if (storageRowCleared) {
            return;
        }
        storageRowCleared = true;
        EssentiaInterfaceRows.dropStaleAspects(storage);
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
        List<AEKey> allowed = EssentiaInterfaceRows.whitelist(config);
        for (Direction face : faces) {
            IEssentiaStorage neighbour = EssentiaNeighbour.at(host, face);
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
                if (key == null || !EssentiaInterfaceRows.mayEnter(allowed, key)) {
                    continue;
                }
                budget -= pull(neighbour, network, aspect, Math.min(budget, entry.amount()), energy);
            }
        }
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
}
