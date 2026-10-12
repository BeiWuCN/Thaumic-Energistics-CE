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
            if ((id.getNamespace().equals("extendedae") || id.getNamespace().equals("extendedae_plus"))
                    && id.getPath().contains("pattern_provider")) {
                event.registerBlockEntity(EssentiaCapabilities.STORAGE, entry.getValue(),
                        (be, side) -> side != null && be instanceof PatternProviderLogicHost host
                                && host.getLogic() instanceof EssentiaPatternProvider provider
                                ? provider.essentiaReturnStorage() : null);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void registerParts(RegisterPartCapabilitiesEvent event) {
        if (!net.neoforged.fml.ModList.get().isLoaded("extendedae")) return;
        try {
            var type = Class.forName("com.glodblock.github.extendedae.common.parts.PartExPatternProvider");
            if (!IPart.class.isAssignableFrom(type)) throw new IllegalStateException("Invalid ExtendedAE part type");
            event.register(EssentiaCapabilities.STORAGE,
                    (IPart part, net.minecraft.core.Direction side) -> side != null
                            && part instanceof PatternProviderLogicHost host
                            && host.getLogic() instanceof EssentiaPatternProvider provider
                            ? provider.essentiaReturnStorage() : null, (Class) type);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("ExtendedAE pattern provider part is unavailable", e);
        }
    }
}
