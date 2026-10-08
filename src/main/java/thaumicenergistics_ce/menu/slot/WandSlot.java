package thaumicenergistics_ce.menu.slot;

import appeng.api.inventories.InternalInventory;
import appeng.menu.slot.AppEngSlot;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

/**
 * 奥术合成终端的法杖槽，只收法杖，规则取自 {@code PartArcaneCraftingTerminal} 给法杖容器
 * 装的那条过滤器（{@code TcWand.isWand}）：按物品类认，不认标签。
 * 只有容器过滤器而没有这里的 {@code mayPlace} 时，槽位会先收下再由容器退回，
 * 看起来就是「什么都能放上去」，所以两处规则必须一致。
 */
public class WandSlot extends AppEngSlot {

    public WandSlot(InternalInventory inventory, int slot) {
        super(inventory, slot);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return super.mayPlace(stack) && PartArcaneCraftingTerminal.isWand(stack);
    }
}
