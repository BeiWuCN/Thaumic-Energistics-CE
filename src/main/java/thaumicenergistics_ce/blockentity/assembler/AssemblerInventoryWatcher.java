package thaumicenergistics_ce.blockentity.assembler;

import appeng.api.networking.crafting.ICraftingProvider;

/**
 * 槽位变化时机器做什么：丢掉样板集，从物品栏重读卡数和装备折扣，再请求网格回来。
 * 从 {@link BlockEntityArcaneAssembler} 拆出：对容器编辑的唯一反应在一处读完。
 */
final class AssemblerInventoryWatcher {

    private AssemblerInventoryWatcher() {}

    static void changed(BlockEntityArcaneAssembler machine) {
        if (machine.suppressNotify) {
            return;
        }
        machine.patternCache.invalidate();
        // 卡现在在机器自己的槽位里，数量从物品栏读。
        machine.upgrades.refreshSpeedUpgrades();
        machine.upgrades.recalculateGearDiscount();
        machine.setChanged();
        boolean serverSide = machine.getLevel() != null && !machine.getLevel().isClientSide();
        if (serverSide && machine.mainNode.getGrid() != null) {
            machine.patternCache.refresh();
            ICraftingProvider.requestUpdate(machine.mainNode);
        }
        if (serverSide) {
            machine.displaySync.refreshPatternSlots();
        }
    }
}
