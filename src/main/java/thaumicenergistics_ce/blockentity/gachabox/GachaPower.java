package thaumicenergistics_ce.blockentity.gachabox;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnit;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;

/**
 * 箱子取电的方式：缓冲区靠索取填充，而不是等着被充电。一台只等着被充的机器
 * 无论背后网络持有多大都会停在零，所以它每个 tick 主动从网格拉一小口。一次
 * 转动的电力先花缓冲区，缓冲区不够的部分由网格补上；补不齐就把它出的那部分放回去。
 */
final class GachaPower {

    /** 箱子可以存入的量：四千 AE，够连续转动一轮而不需要供电线。 */
    static final double ENERGY_CAPACITY = 4000.0;

    /** 单次索取从网格拉走多少：足以产生影响，又不是一次拿走整个缓冲。 */
    private static final double CHARGE_PER_PASS = 800.0;

    private final BlockEntityGachaBox box;

    GachaPower(BlockEntityGachaBox box) {
        this.box = box;
    }

    /** 把缓冲区还有空间容纳的部分直接从网格拉出来，用的就是充电器自己的方式。 */
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
            box.injectExternalPower(PowerUnit.AE, pulled, Actionable.MODULATE);
        }
    }

    /** 当现在能支付一次转动时为真，把缓冲区和它背后的网络都算进去。 */
    boolean canPay() {
        double banked = box.getInternalCurrentPower();
        if (banked >= GachaOdds.AE_PER_TURN) {
            return true;
        }
        IGrid grid = box.getMainNode().getGrid();
        if (grid == null) {
            return false;
        }
        IEnergyService energy = grid.getEnergyService();
        return banked + energy.getStoredPower() >= GachaOdds.AE_PER_TURN;
    }

    /** 从缓冲区里取走这次转动的电力，缓冲区缺的部分由网格补上。 */
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
        // 这次转动没有发生，所以从缓冲区里出来的电力又放回缓冲区。
        box.injectExternalPower(PowerUnit.AE, banked, Actionable.MODULATE);
        return false;
    }
}
