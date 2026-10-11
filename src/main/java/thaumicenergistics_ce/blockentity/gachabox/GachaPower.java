package thaumicenergistics_ce.blockentity.gachabox;

import thaumicenergistics_ce.config.ThEConfig;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;

final class GachaPower {

    static final double ENERGY_CAPACITY = 4000.0;

    private static final double CHARGE_PER_PASS = 800.0;

    private final BlockEntityGachaBox box;

    GachaPower(BlockEntityGachaBox box) {
        this.box = box;
    }

    void chargeFromGrid() {
        IGrid grid = box.getMainNode().getGrid();
        if (grid == null) {
            return;
        }
        double room = Math.min(CHARGE_PER_PASS, box.getInternalMaxPower() - box.getInternalCurrentPower());
        if (room <= 0) {
            return;
        }
        IEnergyService energy = grid.getEnergyService();
        double pulled = energy.extractAEPower(room, Actionable.MODULATE, PowerMultiplier.ONE);
        if (pulled > 0) {
            box.setInternalCurrentPower(box.getInternalCurrentPower() + pulled);
        }
    }

    boolean canPay() {
        double banked = box.getInternalCurrentPower();
        if (banked >= ThEConfig.GACHA_POWER.get()) {
            return true;
        }
        IGrid grid = box.getMainNode().getGrid();
        if (grid == null) {
            return false;
        }
        IEnergyService energy = grid.getEnergyService();
        return banked + energy.getStoredPower() >= ThEConfig.GACHA_POWER.get();
    }

    boolean spend(double need) {
        double banked = box.extractAEPower(need, Actionable.MODULATE, PowerMultiplier.ONE);
        double missing = need - banked;
        if (missing <= 0) {
            return true;
        }
        IGrid grid = box.getMainNode().getGrid();
        double pulled = 0;
        if (grid != null) {
            IEnergyService energy = grid.getEnergyService();
            pulled = energy.extractAEPower(missing, Actionable.MODULATE, PowerMultiplier.ONE);
        }
        if (pulled >= missing) {
            return true;
        }
        box.setInternalCurrentPower(box.getInternalCurrentPower() + banked);
        return false;
    }
}
