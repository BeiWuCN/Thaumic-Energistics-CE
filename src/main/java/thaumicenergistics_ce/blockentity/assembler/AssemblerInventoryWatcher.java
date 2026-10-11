package thaumicenergistics_ce.blockentity.assembler;

import appeng.api.networking.crafting.ICraftingProvider;

final class AssemblerInventoryWatcher {

    private AssemblerInventoryWatcher() {}

    static void changed(BlockEntityArcaneAssembler machine) {
        if (machine.suppressNotify) {
            return;
        }
        machine.patternCache.invalidate();
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
