package thaumicenergistics_ce.blockentity.gachabox;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;

/**
 * 箱子取电的方式：缓冲区靠索取填充，不等被充电。
 * 只等着被充的机器背后网络再大也停在零，每个 tick 主动从网格拉一小口。
 * 一次转动的电力先花缓冲区，不够的部分由网格补上；补不齐就把已经出的那部分放回去。
 */
final class GachaPower {

    /** 箱子能存 4000 AE，够连续转一轮不用供电线。 */
    static final double ENERGY_CAPACITY = 4000.0;

    /** 单次索取从网格拉走 800 AE：能产生影响，又不一次拿走整个缓冲。 */
    private static final double CHARGE_PER_PASS = 800.0;

    private final BlockEntityGachaBox box;

    GachaPower(BlockEntityGachaBox box) {
        this.box = box;
    }

    /** 把缓冲区还装得下的部分直接从网格拉出来，用充电器自己的方式。 */
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
            // 走缓冲区字段而不是 FE 接口：FE 接口要换算还要取整，
            // 网格本来发的就是 AE。
            box.setInternalCurrentPower(box.getInternalCurrentPower() + pulled);
        }
    }

    /** 现在能不能付一次转动，缓冲区和它背后的网络都算进去。 */
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

    /** 从缓冲区取走这次转动的电力，缓冲区缺的部分由网格补上。 */
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
        // 这次转动没发生，从缓冲区出来的电力放回缓冲区。
        box.setInternalCurrentPower(box.getInternalCurrentPower() + banked);
        return false;
    }
}
