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
 * The arcane workbench as Thaumaturge composes it.
 *
 * <p>The crystal slot layout, the rule about what may sit in one, and the payment registry all live
 * in Thaumaturge's {@code content} package - the half of the mod that is free to move - rather than
 * in its {@code api} one. This mod's terminal copies the workbench rather than inventing its own
 * answer, so it has to follow those details wherever they go.
 */
public final class TcWorkbench {
    private TcWorkbench() {}

    /** The primal aspect Thaumaturge's {@code slot}-th crystal slot wants. */
    public static ResourceKey<IAspect> primalAt(int slot) {
        return MenuArcaneWorkbench.PRIMAL_ORDER.get(slot);
    }

    /** The workbench's own rule for a crystal slot, reused so the terminal accepts what it accepts. */
    public static boolean isValidCrystal(ItemStack stack, ResourceKey<IAspect> required) {
        return SlotCrystalEssentia.isValidCrystal(stack, required);
    }

    /** Registers an aura source the workbench may draw vis from; must run before a workbench opens. */
    public static void registerAuraSources(List<IWorkbenchAuraSource> sources) {
        WorkbenchPayment.registerAuraSources(sources);
    }
}
