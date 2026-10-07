package thaumicenergistics_ce;

import net.minecraft.resources.ResourceLocation;

/**
 * Thaumic Energistics 的中心标识符。MODID 与上游 Thaumic Energistics 保持一致，
 * 复用的纹理、模型、方块状态与语言键都能照原样解析。
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
