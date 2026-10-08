package thaumicenergistics_ce.menu.slot;

import appeng.api.inventories.InternalInventory;
import appeng.menu.slot.AppEngSlot;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

/**
 * 奥术合成终端的法杖槽，只收法杖。规则就是{@code PartArcaneCraftingTerminal} 给法杖容器
 * 装的那条过滤器（{@code TcWand.isWand}）：按物品类认，不认标签。
 *
 * <p>两处规则必须一致。容器上的过滤器管的是「进容器」，玩家点击走的是这里的
 * {@code mayPlace}；只有过滤器没有 {@code mayPlace} 时，槽位会先把东西收下、
 * 再由容器把它退掉，看起来就是「什么都能放上去」。
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
