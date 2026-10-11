package thaumicenergistics_ce.golem;

import net.minecraft.resources.Identifier;
import thaumicenergistics_ce.ThEIds;

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
