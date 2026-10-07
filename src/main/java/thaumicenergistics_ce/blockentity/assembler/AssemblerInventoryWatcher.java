package thaumicenergistics_ce.blockentity.assembler;

import appeng.api.networking.crafting.ICraftingProvider;

/**
 * 槽位变化时机器做什么：样板集被丢弃，卡数和装备折扣从物品栏重新读取，
 * 并请求网格回来。从 {@link BlockEntityArcaneAssembler} 拆出，
 * 使对容器编辑的唯一反应能在一处读完。
 */
final class AssemblerInventoryWatcher {

    private AssemblerInventoryWatcher() {}

    static void changed(BlockEntityArcaneAssembler machine) {
        if (machine.suppressNotify) {
            return;
        }
        machine.patternCache.invalidate();
        // 卡现在位于机器自己的槽位中，所以其数量从物品栏读取。
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
