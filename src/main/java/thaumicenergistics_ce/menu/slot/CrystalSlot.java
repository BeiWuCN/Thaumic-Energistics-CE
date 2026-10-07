package thaumicenergistics_ce.menu.slot;

import appeng.api.inventories.InternalInventory;
import appeng.menu.slot.AppEngSlot;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.compat.thaumaturge.TcWorkbench;

/**
 * 奥术合成终端六个水晶槽位之一，固定对应一个元初要素。拒绝与否委托给
 * {@code TcWorkbench.isValidCrystal}，即工作台自己的规则（{@code MenuArcaneWorkbench.addSlots}），
 * 所以终端接受工作台所接受的东西。{@code mayPlace} 就是全部闸门：进入该槽位的每条路径
 * 都要经过它。
 */
public class CrystalSlot extends AppEngSlot {
    private final ResourceKey<IAspect> required;

    public CrystalSlot(InternalInventory inventory, int slot, ResourceKey<IAspect> required) {
        super(inventory, slot);
        this.required = required;
    }

    public ResourceKey<IAspect> requiredAspect() {
        return required;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return super.mayPlace(stack) && TcWorkbench.isValidCrystal(stack, required);
    }
}
