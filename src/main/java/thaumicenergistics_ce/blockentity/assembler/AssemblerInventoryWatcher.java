package thaumicenergistics_ce.blockentity.assembler;

import appeng.api.networking.crafting.ICraftingProvider;

/**
 * What the machine does when a slot changes: the pattern set is dropped, the card count and the gear
 * discount are re-read from the inventory, and the grid is asked to come back. Split out of
 * {@link BlockEntityArcaneAssembler} so the one reaction to a container edit is readable in one place.
 */
final class AssemblerInventoryWatcher {

    private AssemblerInventoryWatcher() {}

    static void changed(BlockEntityArcaneAssembler machine) {
        if (machine.suppressNotify) {
            return;
        }
        machine.patternCache.invalidate();
        // The cards sit in the machine's own slots now, so their count is read off the inventory.
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
