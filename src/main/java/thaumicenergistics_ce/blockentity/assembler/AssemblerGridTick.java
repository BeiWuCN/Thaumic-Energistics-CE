package thaumicenergistics_ce.blockentity.assembler;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.ticking.TickRateModulation;

/**
 * The machine's only clock: one pass of the AE2 grid tick. A stale pattern set is rebuilt, the vis buffer
 * is topped up to what the held craft asks for, and a running craft is handed to
 * {@link AssemblerCraftRunner}. Split out of {@link BlockEntityArcaneAssembler}, which keeps the
 * interface method AE2 calls and nothing else of the tick.
 */
final class AssemblerGridTick {

    private AssemblerGridTick() {}

    static TickRateModulation advance(
            BlockEntityArcaneAssembler machine, IGridNode node, int ticksSinceLast) {
        if (machine.getLevel() == null || machine.getLevel().isClientSide()) {
            return TickRateModulation.SLEEP;
        }
        if (machine.patternCache.isStale()) {
            // Settled only on a successful read, so a rebuild with no level yet retries. See refresh().
            machine.patternCache.refresh();
            ICraftingProvider.requestUpdate(machine.mainNode);
        }
        if (!machine.mainNode.isActive()) {
            return TickRateModulation.IDLE;
        }
        if (machine.vis.bufferedVis()
                < machine.vis.visTarget(machine.craft.isCrafting(), machine.craft.craftPrice())) {
            machine.vis.replenishVis();
        }
        if (!machine.craft.isCrafting()) {
            return TickRateModulation.IDLE;
        }
        IGrid grid = node.getGrid();
        if (grid == null) {
            return TickRateModulation.IDLE;
        }
        return machine.craftRunner().craftingTick(grid, ticksSinceLast);
    }
}
