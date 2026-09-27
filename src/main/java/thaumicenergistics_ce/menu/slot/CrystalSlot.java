package thaumicenergistics_ce.menu.slot;

import appeng.api.inventories.InternalInventory;
import appeng.menu.slot.AppEngSlot;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.content.workbench.SlotCrystalEssentia;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;

/**
 * One of the Arcane Crafting Terminal's six crystal slots, pinned to a single primal aspect.
 *
 * <p>The terminal's crystal slots used to be plain {@link AppEngSlot}s, so all six accepted any essentia
 * crystal and six copies of the same aspect could be placed. That is not what the machine they mirror does:
 * Thaumaturge's arcane workbench gives each of its six slots a required aspect
 * ({@code MenuArcaneWorkbench.addSlots}) and refuses anything else, so a workbench can never hold two Aer
 * crystals while the terminal could.
 *
 * <p>The refusal is delegated to {@link SlotCrystalEssentia#isValidCrystal} rather than reimplemented from
 * the crystal's component. That method is the workbench's own rule, so "the terminal accepts what the
 * workbench accepts" holds by construction instead of by two implementations agreeing today.
 *
 * <p>{@code mayPlace} is the whole gate. Every route into a slot - a click, a shift-click, a double-click
 * gather, and AE2's own item movement - goes through it, so there is no second path to close.
 */
public class CrystalSlot extends AppEngSlot {
    private final ResourceKey<IAspect> required;

    public CrystalSlot(InternalInventory inventory, int slot, ResourceKey<IAspect> required) {
        super(inventory, slot);
        this.required = required;
    }

    /** The aspect this slot holds, for the self-test and for anything that has to name it. */
    public ResourceKey<IAspect> requiredAspect() {
        return required;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return super.mayPlace(stack) && SlotCrystalEssentia.isValidCrystal(stack, required);
    }
}
