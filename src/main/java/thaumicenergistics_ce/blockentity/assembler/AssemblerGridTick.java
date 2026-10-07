package thaumicenergistics_ce.blockentity.assembler;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.ticking.TickRateModulation;

/**
 * 机器的唯一时钟：AE2 网格 tick 的一遍。
 * 过期的样板集重建，vis 缓冲补到持有的合成所要求的量，运行中的合成交给 {@link AssemblerCraftRunner}。
 * 从 {@link BlockEntityArcaneAssembler} 拆出，后者只留 AE2 调用的接口方法。
 */
final class AssemblerGridTick {

    private AssemblerGridTick() {}

    static TickRateModulation advance(
            BlockEntityArcaneAssembler machine, IGridNode node, int ticksSinceLast) {
        if (machine.getLevel() == null || machine.getLevel().isClientSide()) {
            return TickRateModulation.SLEEP;
        }
        if (machine.patternCache.isStale()) {
            // 读取成功才结算；还没有 level 的重建会重试，见 refresh()。
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
