package thaumicenergistics_ce.datagen;

import com.leclowndu93150.thaumaturge.api.research.IResearchCategory;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import net.minecraft.core.RegistrySetBuilder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.compat.thaumaturge.TcResearchCategoryBootstrap;

@EventBusSubscriber(modid = ThEIds.MODID)
public final class ThEDataGenerators {
    private ThEDataGenerators() {}

    @SubscribeEvent
    public static void gather(GatherDataEvent.Client event) {
        event.createDatapackRegistryObjects(new RegistrySetBuilder()
                .add(IAspect.REGISTRY_KEY, TcResearchCategoryBootstrap::bootstrapAspects)
                .add(IResearchCategory.REGISTRY_KEY, TcResearchCategoryBootstrap::bootstrap));
    }
}
