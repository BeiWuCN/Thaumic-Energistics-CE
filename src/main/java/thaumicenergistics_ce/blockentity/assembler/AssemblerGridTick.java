package thaumicenergistics_ce.blockentity.assembler;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.ticking.TickRateModulation;

final class AssemblerGridTick {

    private AssemblerGridTick() {}

    static TickRateModulation advance(
            BlockEntityArcaneAssembler machine, IGridNode node, int ticksSinceLast) {
        if (machine.getLevel() == null || machine.getLevel().isClientSide()) {
            return TickRateModulation.SLEEP;
        }
        if (machine.patternCache.isStale()) {
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
