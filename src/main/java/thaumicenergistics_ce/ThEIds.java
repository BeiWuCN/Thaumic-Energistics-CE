package thaumicenergistics_ce;

import net.minecraft.resources.Identifier;

/**
 * Thaumic Energistics 的中心标识符。MODID 与上游 Thaumic Energistics 保持一致，
 * 复用的纹理、模型、方块状态与语言键都能照原样解析。
 */
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
