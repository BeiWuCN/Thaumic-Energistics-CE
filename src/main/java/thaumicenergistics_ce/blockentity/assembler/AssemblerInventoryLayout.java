package thaumicenergistics_ce.blockentity.assembler;

import appeng.core.definitions.AEItems;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.inventory.GearSlots;

/**
 * 机器的物品栏：每个槽段接受什么，以及所有者在一格变化时的回调。
 * 槽位号本身留在 {@link BlockEntityArcaneAssembler} 上，调用方在那里读取。
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
            // 卡槽段：菜单四个卡槽指向的槽位。
            return AEItems.SPEED_CARD.is(stack);
        }
        if (AssemblerDisplaySync.isDisplaySlot(slot)) {
            // 机器自己的显示：不从玩家那里接收任何物品。
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
