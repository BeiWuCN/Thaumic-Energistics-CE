package thaumicenergistics_ce.compat.thaumaturge;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.recipe.IWorkbenchAuraSource;
import com.leclowndu93150.thaumaturge.content.workbench.MenuArcaneWorkbench;
import com.leclowndu93150.thaumaturge.content.workbench.SlotCrystalEssentia;
import com.leclowndu93150.thaumaturge.content.workbench.WorkbenchPayment;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;

/**
 * The crystal slots, crystal rule and payment registry of Thaumaturge's arcane workbench.
 *
 * <ul>
 *   <li>All three live in its {@code content} package rather than its {@code api} one, so they move.
 *   <li>This mod's terminal copies the workbench, so it follows those details wherever they go.
 * </ul>
 */
public final class TcWorkbench {
    private TcWorkbench() {}

    public static ResourceKey<IAspect> primalAt(int slot) {
        return MenuArcaneWorkbench.PRIMAL_ORDER.get(slot);
    }

    public static boolean isValidCrystal(ItemStack stack, ResourceKey<IAspect> required) {
        return SlotCrystalEssentia.isValidCrystal(stack, required);
    }

    /** Registers an aura source the workbench may draw vis from; must run before a workbench opens. */
    public static void registerAuraSources(List<IWorkbenchAuraSource> sources) {
        WorkbenchPayment.registerAuraSources(sources);
    }
}
