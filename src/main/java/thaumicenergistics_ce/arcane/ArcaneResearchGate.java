package thaumicenergistics_ce.arcane;

import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.recipe.ResearchGate;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.util.ThELog;

/**
 * 样板所依赖的研究，以及它的晶体代表的实时要素条目。存进核心的样板只带要素 key，
 * 只有查注册表才能把它变成 holder。
 */
final class ArcaneResearchGate {

    private ArcaneResearchGate() {}

    static boolean isGated(@Nullable ResourceLocation research) {
        return research != null;
    }

    static Optional<ResearchGate> gate(
            @Nullable ResourceLocation research, @Nullable Integer researchStage) {
        if (research == null) {
            return Optional.empty();
        }
        return Optional.of(new ResearchGate(research, Optional.ofNullable(researchStage), false));
    }

    static int cellCount(List<ItemStack> grid) {
        return grid.size();
    }

    static AspectList resolveCrystals(AspectList crystals, HolderLookup.Provider registries) {
        HolderLookup.RegistryLookup<IAspect> lookup =
                registries.lookup(IAspect.REGISTRY_KEY).orElse(null);
        if (lookup == null) {
            return AspectList.EMPTY;
        }
        AspectList resolved = AspectList.EMPTY;
        for (AspectInstance entry : crystals.entries()) {
            Holder<IAspect> holder = lookup.get(entry.aspect().getKey()).orElse(null);
            if (holder == null) {
                ThELog.LOG.debug(
                        "Arcane pattern references unregistered aspect {}",
                        entry.aspect().getKey().location());
                continue;
            }
            resolved = resolved.add(holder, entry.amount());
        }
        return resolved;
    }

    static List<ResourceKey<IAspect>> crystalKeys(AspectList crystals) {
        return crystals.entries().stream().map(entry -> entry.aspect().getKey()).toList();
    }
}
