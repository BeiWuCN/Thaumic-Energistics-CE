package thaumicenergistics_ce.blockentity.vibrationchamber;

import thaumicenergistics_ce.config.ThEConfig;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.TickRateModulation;

final class ChamberTick {

    private ChamberTick() {
    }

    static TickRateModulation advance(BlockEntityEssentiaVibrationChamber machine, IGridNode node,
            int ticksSinceLast) {
        if (machine.getLevel() == null || machine.getLevel().isClientSide()) {
            return TickRateModulation.IDLE;
        }
        if (!ThEConfig.VIBRATION_ENABLED.get()) {
            return TickRateModulation.SAME;
        }

        if (machine.tank().hasRoom() && !machine.burn().paused()) {
            machine.tank().pull();
        }

        IGrid grid = node.getGrid();
        if (grid == null) {
            machine.burn().update(false);
            return TickRateModulation.SLOWER;
        }

        boolean onNetwork = machine.energy().hasNetwork(grid, node);

        if (onNetwork) {
            machine.energy().output(grid, ticksSinceLast);
        }
        machine.burn().update(onNetwork);

        if (!machine.burn().mayBurn()) {
            return TickRateModulation.SAME;
        }

        if (machine.burn().remaining() > 0) {
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
        machine.burn().update(onNetwork);
        return TickRateModulation.URGENT;
    }
}
