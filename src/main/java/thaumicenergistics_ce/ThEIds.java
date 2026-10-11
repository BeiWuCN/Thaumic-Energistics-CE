package thaumicenergistics_ce;

import net.minecraft.resources.Identifier;

public final class ThEIds {
    public static final String MODID = "thaumicenergistics_ce";

    public static final String THAUMATURGE = "thaumaturge";

    public static final String AE2 = "ae2";

    private ThEIds() {}

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    public static Identifier thaumaturge(String path) {
        return Identifier.fromNamespaceAndPath(THAUMATURGE, path);
    }
}
