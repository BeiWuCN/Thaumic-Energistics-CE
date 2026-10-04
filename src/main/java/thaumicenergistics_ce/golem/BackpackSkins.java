package thaumicenergistics_ce.golem;

import net.minecraft.resources.ResourceLocation;
import thaumicenergistics_ce.ThEIds;

/**
 * The skins a golem's wireless backpack can wear: the reference build's ten, by material.
 * <ul>
 * <li>Each is a texture at {@code textures/model/golembackpack/<id>.png}, looked up by the id rather than
 * by the enum's name so the files and the constants can be read against each other.</li>
 * <li>Built lazily, because an enum constant is constructed before the mod's own id is settled.</li>
 * </ul>
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
