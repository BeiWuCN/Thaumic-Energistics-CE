package thaumicenergistics_ce.compat.thaumaturge;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.recipe.IWorkbenchAuraSource;
import com.leclowndu93150.thaumaturge.content.workbench.MenuArcaneWorkbench;
import com.leclowndu93150.thaumaturge.content.workbench.SlotCrystalEssentia;
import com.leclowndu93150.thaumaturge.registry.TTWorkbenchSources;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;

public final class TcWorkbench {
    private TcWorkbench() {}

    public static ResourceKey<IAspect> primalAt(int slot) {
        return MenuArcaneWorkbench.PRIMAL_ORDER.get(slot);
    }

    public static boolean isValidCrystal(ItemStack stack, ResourceKey<IAspect> required) {
        return SlotCrystalEssentia.isValidCrystal(stack, required);
    }

    public static void registerAuraSources(List<IWorkbenchAuraSource> sources) {
        TTWorkbenchSources.registerAuraSources(sources);
    }
}
