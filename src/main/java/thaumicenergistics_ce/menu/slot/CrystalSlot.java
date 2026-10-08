package thaumicenergistics_ce.menu.slot;

import appeng.api.inventories.InternalInventory;
import appeng.menu.slot.AppEngSlot;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.compat.thaumaturge.TcWorkbench;

/**
 * 奥术合成终端六个水晶槽之一，钉在一个元质上。拒绝交给 {@code TcWorkbench.isValidCrystal}，
 * 就是工作台自己的规则（{@code MenuArcaneWorkbench.addSlots}），终端收的东西和工作台一致。
 * {@code mayPlace} 是全部闸门：进槽的每条路都过它。
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
