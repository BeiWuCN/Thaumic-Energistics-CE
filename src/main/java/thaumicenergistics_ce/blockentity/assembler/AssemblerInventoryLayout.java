package thaumicenergistics_ce.blockentity.assembler;

import appeng.core.definitions.AEItems;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.inventory.GearSlots;

final class AssemblerInventoryLayout extends SimpleContainer {

    private final Runnable onChanged;

    AssemblerInventoryLayout(Runnable onChanged) {
        super(BlockEntityArcaneAssembler.SLOT_COUNT);
        this.onChanged = onChanged;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot >= BlockEntityArcaneAssembler.UPGRADE_SLOT_START) {
            return AEItems.SPEED_CARD.is(stack);
        }
        if (AssemblerDisplaySync.isDisplaySlot(slot)) {
            return false;
        }
        return switch (slot) {
            case BlockEntityArcaneAssembler.CORE_SLOT -> stack.is(ModItems.KNOWLEDGE_CORE.get());
            default -> slot >= BlockEntityArcaneAssembler.GEAR_SLOT_START
                    && GearSlots.accepts(slot - BlockEntityArcaneAssembler.GEAR_SLOT_START, stack);
        };
    }

    @Override
    public void setChanged() {
        super.setChanged();
        onChanged.run();
    }
}
