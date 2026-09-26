package thaumicenergistics;

import net.minecraft.resources.ResourceLocation;

/**
 * Central identifiers for Thaumic Energistics.
 *
 * <p>The mod id matches upstream Thaumic Energistics so the reused textures, models, blockstates and
 * lang keys resolve without rewriting every asset path.
 */
public final class ThEIds {
    public static final String MODID = "thaumicenergistics";

    /** Thaumaturge - the Thaumcraft backport this addon extends. */
    public static final String THAUMATURGE = "thaumaturge";

    /** Applied Energistics 2. */
    public static final String AE2 = "ae2";

    private ThEIds() {}

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    public static ResourceLocation thaumaturge(String path) {
        return ResourceLocation.fromNamespaceAndPath(THAUMATURGE, path);
    }
}
