package thaumicenergistics.golem;

import net.minecraft.resources.ResourceLocation;
import thaumicenergistics.ThEIds;

/**
 * The skins a golem's wireless backpack can wear.
 *
 * <p>The reference build's ten, named for their materials - a golem in a stone room should be able to wear
 * stone.
 *
 * <p>Each is a texture at {@code textures/model/golembackpack/<id>.png}, looked up by the id rather than by
 * the enum's name so the files and the constants can be read against each other. Built lazily, because an
 * enum constant is constructed before the mod's own id is settled.
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

    /** Where this skin's texture lives. */
    public ResourceLocation texture() {
        if (texture == null) {
            texture = ResourceLocation.fromNamespaceAndPath(
                    ThEIds.MODID, "textures/model/golembackpack/" + texId + ".png");
        }
        return texture;
    }

    /**
     * The skin with this ordinal, or the default when the number is out of range.
     *
     * <p>Out of range is not an error here: the ordinal travels in the golem's own data, and a golem saved
     * by a build with more skins than this one should come back wearing the default rather than throwing
     * while it is being loaded.
     */
    public static BackpackSkins fromOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= VALUES.length ? Thaumium : VALUES[ordinal];
    }
}
