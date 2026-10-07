package thaumicenergistics_ce.blockentity.vibrationchamber;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.TickRateModulation;

/**
 * 振动室的一次网格 tick，从方块实体中移出，以保持它只面对网格、管道和界面
 * 所对应的那个门面。每一次状态变化和每一次返回的调制，都与搬移之前机器给出的
 * 完全一致。
 */
final class ChamberTick {

    private ChamberTick() {
    }

    static TickRateModulation advance(BlockEntityEssentiaVibrationChamber machine, IGridNode node,
            int ticksSinceLast) {
        if (machine.getLevel() == null || machine.getLevel().isClientSide()) {
            return TickRateModulation.IDLE;
        }

        // 节点不活跃时也运行：燃料不需要网络，拒收它会把管道堵回去。
        // 槽位满时什么都不取：燃料的电力将无处可去。
        if (machine.tank().hasRoom() && !machine.burn().paused()) {
            machine.tank().pull();
        }
        machine.trace().log();

        // 用网格而不是活跃网格：发电机不应等别人供电，而扁平网络最需要它。
        IGrid grid = node.getGrid();
        if (grid == null) {
            // 这也是只有自己一个节点的网格给出的答案：没有可以把电力交给的对象。
            machine.burn().update(false);
            return TickRateModulation.SLOWER;
        }

        // 要问网格持有什么，而不是问它有多大：见 [ChamberEnergyOutput#hasNetwork]。
        boolean onNetwork = machine.energy().hasNetwork(grid, node);

        // 先输出，这样发现槽位满的这一次访问里空间就会腾出来。
        if (onNetwork) {
            machine.energy().output(grid, ticksSinceLast);
        }
        machine.burn().update(onNetwork);

        // 被按住：槽位里没有空间装这份电力，或者网格上没有东西接收它。
        if (!machine.burn().mayBurn()) {
            return TickRateModulation.SAME;
        }

        if (machine.burn().remaining() > 0) {
            // 只烧掉其功率装得下的那些 tick，剩下的以后再说：这份燃料冻结，而不是重新开始。
            // 一次性记入整个唤醒时段，正是燃料被烧成无处安放的电力原因。
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
        // 重新评估：速率刚刚变化，所以一份燃料可能从一开始就被按住。
        machine.burn().update(onNetwork);
        return TickRateModulation.URGENT;
    }
}
