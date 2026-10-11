package thaumicenergistics_ce.blockentity.vibrationchamber;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.networking.energy.IEnergyService;

final class ChamberEnergyOutput {

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

    void add(double amount) {
        storedEnergy = Math.min(BlockEntityEssentiaVibrationChamber.MAX_ENERGY_STORAGE, storedEnergy + amount);
    }

    void restore(double energy) {
        storedEnergy = energy;
    }

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
