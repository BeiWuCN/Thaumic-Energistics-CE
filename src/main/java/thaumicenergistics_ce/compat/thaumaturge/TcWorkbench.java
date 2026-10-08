package thaumicenergistics_ce.compat.thaumaturge;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.recipe.IWorkbenchAuraSource;
import com.leclowndu93150.thaumaturge.content.workbench.MenuArcaneWorkbench;
import com.leclowndu93150.thaumaturge.content.workbench.SlotCrystalEssentia;
import com.leclowndu93150.thaumaturge.registry.TTWorkbenchSources;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;

/**
 * Thaumaturge 奥术工作台的水晶槽位、水晶规则和支付注册表。
 * 这三者都在 {@code content} 包里，不在 {@code api} 包，会变动；
 * 本 mod 的终端复刻了工作台，这些细节挪到哪它就跟到哪。
 */
public final class TcWorkbench {
    private TcWorkbench() {}

    public static ResourceKey<IAspect> primalAt(int slot) {
        return MenuArcaneWorkbench.PRIMAL_ORDER.get(slot);
    }

    public static boolean isValidCrystal(ItemStack stack, ResourceKey<IAspect> required) {
        return SlotCrystalEssentia.isValidCrystal(stack, required);
    }

    /** 注册工作台可从中抽取 vis 的灵气源；要在工作台打开之前运行。 */
    public static void registerAuraSources(List<IWorkbenchAuraSource> sources) {
        TTWorkbenchSources.registerAuraSources(sources);
    }
}
