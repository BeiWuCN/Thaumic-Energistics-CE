package thaumicenergistics_ce.blockentity.vibrationchamber;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.TickRateModulation;

/**
 * One grid tick of the chamber, moved out of the block entity so it keeps to the face the grid,
 * the pipes and the screen are answered by. Every state change and every returned modulation is
 * the one the machine gave before the move.
 */
final class ChamberTick {

    private ChamberTick() {
    }

    static TickRateModulation advance(BlockEntityEssentiaVibrationChamber machine, IGridNode node,
            int ticksSinceLast) {
        if (machine.getLevel() == null || machine.getLevel().isClientSide()) {
            return TickRateModulation.IDLE;
        }

        // Runs while the node is inactive too: fuel needs no network, and refusing it would back a pipe up.
        // Nothing is taken while the slot is full: the fuel's power would have nowhere to go.
        if (machine.tank().hasRoom() && !machine.burn().paused()) {
            machine.tank().pull();
        }
        machine.trace().log();

        // Grid, not active grid: a generator must not wait to be powered, and a flat grid needs it most.
        IGrid grid = node.getGrid();
        if (grid == null) {
            // The answer a grid of one gives too: there is nothing to hand the power to.
            machine.burn().update(false);
            return TickRateModulation.SLOWER;
        }

        // Asked of what the grid holds, not of how big it is: see ChamberEnergyOutput#hasNetwork.
        boolean onNetwork = machine.energy().hasNetwork(grid, node);

        // Output first, so room opens in the same visit that finds the slot full.
        if (onNetwork) {
            machine.energy().output(grid, ticksSinceLast);
        }
        machine.burn().update(onNetwork);

        // Held back: no room in the slot for the power, or nothing on the grid to take it.
        if (!machine.burn().mayBurn()) {
            return TickRateModulation.SAME;
        }

        if (machine.burn().remaining() > 0) {
            // Only ticks whose power fits are burnt, the rest later: the unit freezes, it does not restart.
            // Crediting a whole wake-up is what spent fuel on power with nowhere to put it.
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
        // Re-evaluate: the rate just changed, so a unit may be held back from the start.
        machine.burn().update(onNetwork);
        return TickRateModulation.URGENT;
    }
}
