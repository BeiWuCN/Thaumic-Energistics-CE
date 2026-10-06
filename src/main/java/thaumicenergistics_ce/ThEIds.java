package thaumicenergistics_ce;

import net.minecraft.resources.ResourceLocation;

/**
 * Central identifiers for Thaumic Energistics. MODID matches upstream Thaumic Energistics, which
 * keeps the reused textures, models, blockstates and lang keys resolving as-is.
 */
public final class ThEIds {
    public static final String MODID = "thaumicenergistics_ce";

    public static final String THAUMATURGE = "thaumaturge";

    public static final String AE2 = "ae2";

    private ThEIds() {}

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    public static ResourceLocation thaumaturge(String path) {
        return ResourceLocation.fromNamespaceAndPath(THAUMATURGE, path);
    }
}
