package thaumicenergistics_ce;

import net.minecraft.resources.ResourceLocation;

/**
 * Central identifiers for Thaumic Energistics.
 *
 * <ul>
 * <li>MODID matches upstream Thaumic Energistics.</li>
 * <li>That keeps the reused textures, models, blockstates and lang keys resolving as-is.</li>
 * </ul>
 */
public final class ThEIds {
    public static final String MODID = "thaumicenergistics_ce";

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
