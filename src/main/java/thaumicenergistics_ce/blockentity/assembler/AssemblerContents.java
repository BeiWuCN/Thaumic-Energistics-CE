package thaumicenergistics_ce.blockentity.assembler;

import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;

/** 归还损坏机器的内容物：玩家拥有的物品，加上合成已经付过款的投入物。 */
final class AssemblerContents {

    private AssemblerContents() {}

    public static void drop(BlockEntityArcaneAssembler machine) {
        if (machine.getLevel() == null || machine.getLevel().isClientSide()) {
            return;
        }
        // 在方块消失前归还网络已经付过款的原料。
        machine.craftRunner().returnHeldInputs();
        machine.suppressNotify = true;
        try {
            for (int slot = 0; slot < BlockEntityArcaneAssembler.SLOT_COUNT; slot++) {
                if (AssemblerDisplaySync.isMachineOwned(slot)) {
                    continue;
                }
                ItemStack stack = machine.inventory.getItem(slot);
                if (!stack.isEmpty()) {
                    Containers.dropItemStack(
                            machine.getLevel(),
                            machine.getBlockPos().getX(),
                            machine.getBlockPos().getY(),
                            machine.getBlockPos().getZ(),
                            stack);
                    machine.inventory.setItem(slot, ItemStack.EMPTY);
                }
            }
        } finally {
            machine.suppressNotify = false;
        }
    }
}
