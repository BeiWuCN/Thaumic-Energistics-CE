package thaumicenergistics_ce.golem;

import net.minecraft.resources.ResourceLocation;
import thaumicenergistics_ce.ThEIds;

/**
 * 傀儡的无线背包可以穿的外观：参照实现中的十种，按材质划分。每一种
 * 都是 {@code textures/model/golembackpack/<id>.png} 处的一张纹理，按 id 而不是
 * 按枚举名查询，这样文件和常量可以互相对照着读；该集合延迟构建，因为枚举常量
 * 的构造早于本 mod 的 id 确定。
 */
public enum BackpackSkins {

    Thaumium("thaum"),
    Stone("stone"),
    Straw("straw"),
    Wood("wood"),
    Flesh("flesh"),
    Clay("clay"),
    Iron("iron"),
    Tallow("tallow"),
    Gold("gold"),
    Diamond("diamond");

    public static final BackpackSkins[] VALUES = values();

    private final String texId;
    private ResourceLocation texture;

    BackpackSkins(String texId) {
        this.texId = texId;
    }

    public ResourceLocation texture() {
        if (texture == null) {
            texture = ResourceLocation.fromNamespaceAndPath(
                    ThEIds.MODID, "textures/model/golembackpack/" + texId + ".png");
        }
        return texture;
    }

    public static BackpackSkins fromOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= VALUES.length ? Thaumium : VALUES[ordinal];
    }
}
