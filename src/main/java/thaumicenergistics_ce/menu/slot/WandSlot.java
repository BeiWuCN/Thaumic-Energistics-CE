package thaumicenergistics_ce.menu.slot;

import appeng.api.inventories.InternalInventory;
import appeng.menu.slot.AppEngSlot;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

public class WandSlot extends AppEngSlot {

    public WandSlot(InternalInventory inventory, int slot) {
        super(inventory, slot);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return super.mayPlace(stack) && PartArcaneCraftingTerminal.isWand(stack);
    }
}
