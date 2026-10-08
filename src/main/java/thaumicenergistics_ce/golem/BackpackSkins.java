package thaumicenergistics_ce.golem;

import net.minecraft.resources.Identifier;
import thaumicenergistics_ce.ThEIds;

/**
 * 傀儡无线背包能穿的外观：参照实现里的十种，按材质分。每种是
 * {@code textures/model/golembackpack/<id>.png} 处的一张纹理，按 id 而非枚举名查，
 * 文件和常量才能对照着读；集合延迟构建，枚举常量的构造早于本 mod 的 id 定下来。
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
    private Identifier texture;

    BackpackSkins(String texId) {
        this.texId = texId;
    }

    public Identifier texture() {
        if (texture == null) {
            texture = Identifier.fromNamespaceAndPath(
                    ThEIds.MODID, "textures/model/golembackpack/" + texId + ".png");
        }
        return texture;
    }

    public static BackpackSkins fromOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= VALUES.length ? Thaumium : VALUES[ordinal];
    }
}
