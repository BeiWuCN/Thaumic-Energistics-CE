package thaumicenergistics_ce.blockentity;

import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import thaumicenergistics_ce.util.ThELog;

/**
 * Whether to log what the chamber sees of its neighbours, once a second; on with
 * {@code THAUMICENERGISTICS_EVC_TRACE=true}. Tells a pipe not reaching from one out-pulled.
 */
final class ChamberTrace {

    private static final boolean TRACE = "true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_EVC_TRACE"));

    private final BlockEntityEssentiaVibrationChamber chamber;
    private final ChamberEssentiaTank tank;
    private final ChamberEnergyOutput energy;
    private final ChamberBurn burn;

    private long nextTrace;

    ChamberTrace(BlockEntityEssentiaVibrationChamber chamber, ChamberEssentiaTank tank, ChamberEnergyOutput energy,
            ChamberBurn burn) {
        this.chamber = chamber;
        this.tank = tank;
        this.energy = energy;
        this.burn = burn;
    }

    /** One line a second, listing every side that offers a tube or a container and what it answers. */
    void log() {
        if (!TRACE || chamber.getLevel() == null || !(chamber.getLevel() instanceof ServerLevel server)) {
            return;
        }
        long now = server.getGameTime();
        if (now < nextTrace) {
            return;
        }
        nextTrace = now + 20;
        StringBuilder sides = new StringBuilder();
        for (Direction side : Direction.values()) {
            BlockPos neighbour = chamber.getBlockPos().relative(side);
            Direction facing = side.getOpposite();
            IEssentiaTransport tube =
                    chamber.getLevel().getCapability(EssentiaCapabilities.TRANSPORT, neighbour, facing);
            IEssentiaStorage container =
                    chamber.getLevel().getCapability(EssentiaCapabilities.STORAGE, neighbour, facing);
            if (tube == null && container == null) {
                continue;
            }
            if (sides.length() > 0) {
                sides.append(" | ");
            }
            sides.append(side).append(' ');
            if (tube != null) {
                sides.append("tube[canOut=").append(tube.canOutputTo(facing))
                        .append(" suck=").append(tube.getSuctionAmount(facing))
                        .append(" type=").append(tube.getSuctionType(facing) == null ? "any" : "set")
                        .append(" has=").append(tube.getEssentiaAmount(facing))
                        .append(" mine=").append(chamber.getSuctionAmount(side))
                        .append(" min=").append(tube.getMinimumSuction())
                        .append(']');
            }
            if (container != null) {
                sides.append(" storage[").append(container.contents().size()).append(" kinds]");
            }
        }
        ThELog.LOG.info(
                "[evc] at {} stored={}/{} energy={}/{} state={} pulled={} lastSecond | {}",
                chamber.getBlockPos(), tank.amount(), BlockEntityEssentiaVibrationChamber.MAX_ESSENTIA,
                Math.round(energy.amount()), (long) BlockEntityEssentiaVibrationChamber.MAX_ENERGY_STORAGE,
                burn.state(), tank.traced(),
                sides.length() == 0 ? "nothing adjacent" : sides);
        tank.resetTraced();
    }
}
