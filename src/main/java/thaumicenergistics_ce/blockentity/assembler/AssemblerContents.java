package thaumicenergistics_ce.blockentity.assembler;

import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;

/** Gives a broken machine's contents back: what a player owns, plus the inputs a craft already paid for. */
final class AssemblerContents {

    private AssemblerContents() {}

    public static void drop(BlockEntityArcaneAssembler machine) {
        if (machine.level() == null || machine.level().isClientSide()) {
            return;
        }
        // Give back ingredients the network has already paid for before the block goes.
        machine.craftJob().returnHeldInputs();
        machine.suppressNotify = true;
        try {
            for (int slot = 0; slot < BlockEntityArcaneAssembler.SLOT_COUNT; slot++) {
                if (AssemblerDisplaySync.isMachineOwned(slot)) {
                    continue;
                }
                ItemStack stack = machine.inventory.getItem(slot);
                if (!stack.isEmpty()) {
                    Containers.dropItemStack(
                            machine.level(),
                            machine.blockPos().getX(),
                            machine.blockPos().getY(),
                            machine.blockPos().getZ(),
                            stack);
                    machine.inventory.setItem(slot, ItemStack.EMPTY);
                }
            }
        } finally {
            machine.suppressNotify = false;
        }
    }
}
