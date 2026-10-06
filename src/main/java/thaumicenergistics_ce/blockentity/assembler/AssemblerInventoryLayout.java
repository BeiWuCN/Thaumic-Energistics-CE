package thaumicenergistics_ce.blockentity.assembler;

import appeng.core.definitions.AEItems;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.inventory.GearSlots;

/**
 * The machine's inventory: what each band of slots accepts, and the owner's callback when one changes.
 * The slot numbers themselves stay on {@link BlockEntityArcaneAssembler}, where callers read them.
 */
final class AssemblerInventoryLayout extends SimpleContainer {

    private final Runnable onChanged;

    AssemblerInventoryLayout(Runnable onChanged) {
        super(BlockEntityArcaneAssembler.SLOT_COUNT);
        this.onChanged = onChanged;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot >= BlockEntityArcaneAssembler.UPGRADE_SLOT_START) {
            // The card band: the slots the menu's four card wells point at.
            return AEItems.SPEED_CARD.is(stack);
        }
        if (AssemblerDisplaySync.isDisplaySlot(slot)) {
            // The machine's own display: it takes nothing from a player.
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
