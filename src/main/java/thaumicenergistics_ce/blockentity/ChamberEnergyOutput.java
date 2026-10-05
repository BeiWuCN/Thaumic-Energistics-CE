package thaumicenergistics_ce.blockentity;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.networking.energy.IEnergyService;

/**
 * The chamber's energy slot: what the grid takes out of it, and what counts as full.
 * <ul>
 *   <li>AE2 destroys what the grid refuses: only what was accepted leaves the slot.
 *   <li>{@link #isFull()} is a level read off the room left, never a latch, so the screen follows the gauge.
 *   <li>Whether there is a network at all is asked of what the grid holds, not of how big it is.
 * </ul>
 */
final class ChamberEnergyOutput {

    /** Room the energy slot may keep and still count as full. One tick of the slowest burn is the least a
     * tick can be worth, so "full" begins where the gauge crosses 15.9 kAE, not at a stuck 15.7 kAE. */
    private static final double FULL_MARGIN = ChamberBurn.BASE_AE_PER_TICK / 2.0;

    private final BlockEntityEssentiaVibrationChamber chamber;

    private double storedEnergy;

    ChamberEnergyOutput(BlockEntityEssentiaVibrationChamber chamber) {
        this.chamber = chamber;
    }

    double amount() {
        return storedEnergy;
    }

    float fillProgress() {
        return (float) (storedEnergy / BlockEntityEssentiaVibrationChamber.MAX_ENERGY_STORAGE);
    }

    double room() {
        return BlockEntityEssentiaVibrationChamber.MAX_ENERGY_STORAGE - storedEnergy;
    }

    boolean isFull() {
        return room() <= FULL_MARGIN;
    }

    /** Puts burnt power in, never past the slot's size. */
    void add(double amount) {
        storedEnergy = Math.min(BlockEntityEssentiaVibrationChamber.MAX_ENERGY_STORAGE, storedEnergy + amount);
    }

    void restore(double energy) {
        storedEnergy = energy;
    }

    /**
     * Whether this grid can take the power: a node holding it ({@link IAEPowerStorage}) or machines that draw
     * it. A mere grid is not enough: its 25 AE per node buffer (GridEnergyStorage:83) takes it.
     */
    boolean hasNetwork(IGrid grid, IGridNode self) {
        IEnergyService energy = grid.getService(IEnergyService.class);
        if (energy == null) {
            return false;
        }
        if (energy.getIdlePowerUsage() > 0.0) {
            return true;
        }
        for (IGridNode other : grid.getNodes()) {
            if (other != self && other.getService(IAEPowerStorage.class) != null) {
                return true;
            }
        }
        return false;
    }

    /** Hands the grid what it accepts, tick by tick; a refusal leaves the power in the slot. */
    void output(IGrid grid, int ticksSinceLast) {
        if (storedEnergy <= 0) {
            return;
        }
        IEnergyService energy = grid.getService(IEnergyService.class);
        if (energy == null) {
            return;
        }
        double offered = Math.min(
                storedEnergy, BlockEntityEssentiaVibrationChamber.MAX_OUTPUT_PER_TICK * ticksSinceLast);
        double rejected = energy.injectPower(offered, Actionable.MODULATE);
        if (offered - rejected > 0) {
            storedEnergy = Math.max(0, storedEnergy - (offered - rejected));
            chamber.setChanged();
        }
    }
}
