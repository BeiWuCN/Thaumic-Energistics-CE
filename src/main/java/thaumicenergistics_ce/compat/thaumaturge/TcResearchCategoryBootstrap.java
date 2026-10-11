package thaumicenergistics_ce.compat.thaumaturge;

import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TTAspects;
import com.leclowndu93150.thaumaturge.api.research.IResearchCategory;
import com.leclowndu93150.thaumaturge.content.research.ResearchCategory;
import com.leclowndu93150.thaumaturge.data.worldgen.aspect.AspectBootstrap;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderGetter;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import thaumicenergistics_ce.ThEIds;

public final class TcResearchCategoryBootstrap {
    public static final ResourceKey<IResearchCategory> CATEGORY =
            ResourceKey.create(IResearchCategory.REGISTRY_KEY, ThEIds.id(ThEIds.MODID));

    private TcResearchCategoryBootstrap() {}

    public static void bootstrapAspects(BootstrapContext<IAspect> context) {
        AspectBootstrap.bootstrap(context);
    }

    public static void bootstrap(BootstrapContext<IResearchCategory> context) {
        HolderGetter<IAspect> aspects = context.lookup(IAspect.REGISTRY_KEY);
        AspectList formula = AspectList.ofEntries(List.of(
                entry(aspects, TTAspects.MACHINA, 15),
                entry(aspects, TTAspects.FABRICO, 15),
                entry(aspects, TTAspects.POTENTIA, 20),
                entry(aspects, TTAspects.PERMUTATIO, 20),
                entry(aspects, TTAspects.PRAECANTATIO, 15),
                entry(aspects, TTAspects.METALLUM, 5)));
        context.register(CATEGORY, new ResearchCategory(
                Optional.of(ThEIds.thaumaturge("unlock_infusion")), formula,
                ThEIds.id("textures/research/tab_icon.png"),
                ThEIds.id("textures/research/research_background.png"),
                Optional.of(ThEIds.thaumaturge("textures/gui/gui_research_back_over.png")), 7));
    }

    private static AspectInstance entry(HolderGetter<IAspect> aspects, ResourceKey<IAspect> key, int amount) {
        return new AspectInstance(aspects.getOrThrow(key), amount);
    }
}
