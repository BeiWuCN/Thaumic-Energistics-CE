package thaumicenergistics_ce.menu.slot;

import appeng.api.inventories.InternalInventory;
import appeng.menu.slot.AppEngSlot;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.compat.thaumaturge.TcWorkbench;

public class CrystalSlot extends AppEngSlot {

    private final ResourceKey<IAspect> required;

    public CrystalSlot(InternalInventory inventory, int slot, ResourceKey<IAspect> required) {
        super(inventory, slot);
        this.required = required;
        setEmptyTooltip(() -> shiftHeld()
                ? List.of(Component.translatable(
                        "gui.thaumicenergistics_ce.arcane_terminal.crystal_required", aspectName(required)))
                : List.of());
    }

    private static boolean shiftHeld() {
        return Minecraft.getInstance().hasShiftDown();
    }

    private static Component aspectName(ResourceKey<IAspect> aspect) {
        Identifier id = aspect.identifier();
        return Component.translatable("aspect." + id.getNamespace() + "." + id.getPath());
    }

    public ResourceKey<IAspect> requiredAspect() {
        return required;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return super.mayPlace(stack) && TcWorkbench.isValidCrystal(stack, required);
    }

}
