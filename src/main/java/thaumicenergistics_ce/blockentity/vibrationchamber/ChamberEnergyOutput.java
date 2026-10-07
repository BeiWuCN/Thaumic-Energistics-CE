package thaumicenergistics_ce.blockentity.vibrationchamber;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.networking.energy.IEnergyService;

/**
 * 振动室的能量槽：网格从它里面取走什么，以及什么算作满。AE2 会销毁
 * 网格拒收的部分，所以只有被接受的部分才离开槽位；{@link #isFull()} 是
 * 由剩余空间读出的水平而非锁存，因此界面随仪表走。是否存在网络要问
 * 网格持有什么，而不是问它有多大。
 */
final class ChamberEnergyOutput {

    /** 能量槽可以保留多少还仍算作满。最慢燃烧的一个 tick 是一个 tick 至少能值的量，
     * 所以“满”从仪表越过 15.9 kAE 开始，而不是卡在 15.7 kAE。 */
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

    /** 把烧出的电力放进去，绝不超出槽位的容量。 */
    void add(double amount) {
        storedEnergy = Math.min(BlockEntityEssentiaVibrationChamber.MAX_ENERGY_STORAGE, storedEnergy + amount);
    }

    void restore(double energy) {
        storedEnergy = energy;
    }

    /**
     * 这个网格能否接收电力：要么有持有电力的节点（{@link IAEPowerStorage}），要么有用电的机器。
     * 光有网格不算：它每个节点 25 AE 的缓冲区（[GridEnergyStorage:83]）就会把它收下。
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

    /** 逐 tick 把网格愿意接受的部分交给它；被拒收时电力留在槽位里。 */
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
