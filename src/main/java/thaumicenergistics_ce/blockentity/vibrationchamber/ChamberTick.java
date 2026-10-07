package thaumicenergistics_ce.blockentity.vibrationchamber;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.TickRateModulation;

/**
 * 振动室的一次网格 tick，从方块实体里拿出来，只面对网格、管道和界面对应的那个门面。
 * 状态变化和返回的调制与原机器完全一致。
 */
final class ChamberTick {

    private ChamberTick() {
    }

    static TickRateModulation advance(BlockEntityEssentiaVibrationChamber machine, IGridNode node,
            int ticksSinceLast) {
        if (machine.getLevel() == null || machine.getLevel().isClientSide()) {
            return TickRateModulation.IDLE;
        }

        // 节点不活跃时也运行：燃料不靠网络，拒收会把管道堵回去。
        // 槽位满时不取：燃料的电没处去。
        if (machine.tank().hasRoom() && !machine.burn().paused()) {
            machine.tank().pull();
        }
        machine.trace().log();

        // 取网格，不取活跃网格：发电机不该等别人供电，扁平网络最需要它。
        IGrid grid = node.getGrid();
        if (grid == null) {
            // 自己独占的网格也是这个答案：没有别的对象收电。
            machine.burn().update(false);
            return TickRateModulation.SLOWER;
        }

        // 问网格里有什么，别问网格多大，见 [ChamberEnergyOutput#hasNetwork]。
        boolean onNetwork = machine.energy().hasNetwork(grid, node);

        // 先输出，本次访问里就能腾出槽位空间。
        if (onNetwork) {
            machine.energy().output(grid, ticksSinceLast);
        }
        machine.burn().update(onNetwork);

        // 暂停：槽位装不下这份电力，或网格上没人接收。
        if (!machine.burn().mayBurn()) {
            return TickRateModulation.SAME;
        }

        if (machine.burn().remaining() > 0) {
            // 只烧掉功率装得下的 tick，电量冻结，不重来。
            // 整段唤醒时段一次记入，燃料就会烧成没处放的电力。
            double perTick = machine.burn().tickPower();
            int burnt = machine.burn().ticksThatFit(ticksSinceLast);
            machine.energy().add(burnt * perTick);
            boolean burntOut = machine.burn().spend(burnt);
            machine.burn().update(onNetwork);
            if (burntOut) {
                machine.markChanged();
            }
            return TickRateModulation.SAME;
        }

        if (machine.tank().amount() <= 0) {
            return TickRateModulation.SLOWER;
        }

        machine.burn().start();
        // 重新评估：速率刚变，一份燃料可能一开始就暂停。
        machine.burn().update(onNetwork);
        return TickRateModulation.URGENT;
    }
}
