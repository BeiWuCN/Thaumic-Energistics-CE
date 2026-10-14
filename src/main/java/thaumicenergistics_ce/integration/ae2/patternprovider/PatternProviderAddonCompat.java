package thaumicenergistics_ce.integration.ae2.patternprovider;

import appeng.api.parts.IPart;
import appeng.api.parts.RegisterPartCapabilitiesEvent;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public final class PatternProviderAddonCompat {
    private PatternProviderAddonCompat() {}

    public static void registerBlocks(RegisterCapabilitiesEvent event) {
        for (var entry : BuiltInRegistries.BLOCK_ENTITY_TYPE.entrySet()) {
            var id = entry.getKey().identifier();
            if ((id.getNamespace().equals("extendedae") || id.getNamespace().equals("extendedae_plus")
                    || id.getNamespace().equals("advanced_ae") || id.getNamespace().equals("ae2lt"))
                    && id.getPath().contains("pattern_provider")) {
                event.registerBlockEntity(EssentiaCapabilities.STORAGE, entry.getValue(),
                        (be, side) -> side != null && provider(be) != null ? provider(be).essentiaReturnStorage() : null);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void registerParts(RegisterPartCapabilitiesEvent event) {
        if (net.neoforged.fml.ModList.get().isLoaded("extendedae")) {
            registerPart(event, "com.glodblock.github.extendedae.common.parts.PartExPatternProvider");
        }
        if (net.neoforged.fml.ModList.get().isLoaded("advanced_ae")) {
            registerPart(event, "net.pedroksl.advanced_ae.common.parts.AdvPatternProviderPart");
            registerPart(event, "net.pedroksl.advanced_ae.common.parts.SmallAdvPatternProviderPart");
        }
    }

    public static EssentiaPatternProvider provider(Object host) {
        if (host instanceof EssentiaPatternProvider provider) return provider;
        if (host instanceof PatternProviderLogicHost nativeHost
                && nativeHost.getLogic() instanceof EssentiaPatternProvider provider) return provider;
        if (net.neoforged.fml.ModList.get().isLoaded("advanced_ae")
                && host instanceof net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost advancedHost
                && advancedHost.getLogic() instanceof EssentiaPatternProvider provider) return provider;
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerPart(RegisterPartCapabilitiesEvent event, String name) {
        try {
            var type = Class.forName(name);
            if (!IPart.class.isAssignableFrom(type)) throw new IllegalStateException("Invalid provider part: " + name);
            event.register(EssentiaCapabilities.STORAGE,
                    (IPart part, net.minecraft.core.Direction side) -> side != null && provider(part) != null
                            ? provider(part).essentiaReturnStorage() : null, (Class) type);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Provider part is unavailable: " + name, e);
        }
    }
}
