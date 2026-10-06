package thaumicenergistics_ce.menu.slot;

import appeng.api.inventories.InternalInventory;
import appeng.menu.slot.AppEngSlot;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.compat.thaumaturge.TcWorkbench;

/**
 * One of the Arcane Crafting Terminal's six crystal slots, pinned to a single primal aspect.
 * Refusal delegates to {@code TcWorkbench.isValidCrystal}, the workbench's own rule
 * ({@code MenuArcaneWorkbench.addSlots}), so the terminal accepts what the workbench accepts.
 * {@code mayPlace} is the whole gate: every route into the slot goes through it.
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
