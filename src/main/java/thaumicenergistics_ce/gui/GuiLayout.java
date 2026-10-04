package thaumicenergistics_ce.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.ThaumicEnergistics;

/**
 * Geometry for the Arcane Assembler screen, read from {@code arcane_assembler_gui.json} off the classpath.
 * <ul>
 * <li>Coordinates are measured off the GUI art's slot wells, so image wells and menu slots land on the
 * same pixels.
 * <li>Read on both sides with no reload listener, and written by {@code tools/build_assembler_layout.js}.
 * </ul>
 */
public final class GuiLayout {

    private static final String RESOURCE = "/assets/thaumicenergistics_ce/gui/arcane_assembler_gui.json";

    /** Window size, matching the reference screen; used only when the layout file cannot be read. */
    private static final int FB_WIDTH = 175;
    private static final int FB_HEIGHT = 231;

    public record Anchor(int x, int y) {}

    public record Grid(int x, int y, int cols, int rows) {
        public int slots() {
            return cols * rows;
        }

        public int slotX(int index) {
            return x + (index % cols) * PITCH;
        }

        public int slotY(int index) {
            return y + (index / cols) * PITCH;
        }

        public int columnX() {
            return x;
        }

        public int columnY(int index) {
            return y + index * PITCH;
        }
    }

    public record VisBars(List<Column> columns) {

        public record Column(int sourceU, int x, int y) {}

        public int count() {
            return columns.size();
        }

        public Column column(int index) {
            return columns.get(index);
        }

        public static int fillHeight(float ratio) {
            return Math.max(0, Math.min(TROUGH_INTERIOR, Math.round(TROUGH_INTERIOR * ratio)));
        }
    }

    public record Region(int u, int v, int w, int h) {}

    public static final int PITCH = 18;

    public static final int BAR_WIDTH = 4;
    public static final int BAR_HEIGHT = 16;

    /**
     * Where a trough's recess starts, and how many rows it has. The art's first sprite row is the well's
     * shadow, so only rows 1..15 are recess; drawing all sixteen repainted the shadow at a full column.
     */
    public static final int TROUGH_INSET = 1;
    public static final int TROUGH_INTERIOR = BAR_HEIGHT - TROUGH_INSET;

    /** Number of primal columns; the vis column after them is craft progress. */
    public static final int PRIMAL_COLUMNS = 6;

    private final String texture;
    private final int imageWidth;
    private final int imageHeight;
    private final Anchor title;
    private final Anchor inventoryLabel;
    private final Grid patternGrid;
    private final Anchor targetSlot;
    private final Anchor coreSlot;
    private final Grid upgradeSlots;
    private final Grid armorSlots;
    private final VisBars visBars;
    private final Grid previewGrid;
    private final Anchor previewResult;
    private final Anchor playerInventory;
    public record PanelPiece(Region source, Anchor destination) {}

    private final Anchor hotbar;
    /** The opaque pieces of the background art, in paint order. Blitting one window-sized box instead would
     * paint the transparency right of the side slabs and drag the bar sprites parked below the panel in. */
    private final List<PanelPiece> panels;

    private GuiLayout(JsonObject root) {
        this.texture = root.has("texture")
                ? root.get("texture").getAsString()
                : "thaumicenergistics_ce:textures/gui/arcane_assembler.png";
        this.imageWidth = intAt(root, "imageWidth", FB_WIDTH);
        this.imageHeight = intAt(root, "imageHeight", FB_HEIGHT);
        this.title = anchor(root, "title");
        this.inventoryLabel = anchor(root, "inventoryLabel");
        this.patternGrid = grid(root, "patternGrid");
        this.targetSlot = anchor(root, "targetSlot");
        this.coreSlot = anchor(root, "coreSlot");
        this.upgradeSlots = grid(root, "upgradeSlots");
        this.armorSlots = grid(root, "armorSlots");
        this.visBars = visBars(root, "visBars");
        this.previewGrid = grid(root, "previewGrid");
        this.previewResult = anchor(root, "previewResult");
        this.playerInventory = anchor(root, "playerInventory");
        this.hotbar = anchor(root, "hotbar");

        JsonObject regions = root.getAsJsonObject("regions");
        JsonObject destinations = root.getAsJsonObject("regionDest");
        List<PanelPiece> pieces = new ArrayList<>();
        for (JsonElement name : root.getAsJsonArray("panelOrder")) {
            String key = name.getAsString();
            pieces.add(new PanelPiece(region(regions, key), anchor(destinations, key)));
        }
        this.panels = List.copyOf(pieces);
    }

    private static int[] ints(JsonObject root, String key) {
        if (root == null) {
            return new int[0];
        }
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonArray()) {
            return new int[0];
        }
        JsonArray array = element.getAsJsonArray();
        int[] values = new int[array.size()];
        for (int i = 0; i < array.size(); i++) {
            values[i] = array.get(i).getAsInt();
        }
        return values;
    }

    private static int at(int[] values, int index, int fallback) {
        return index < values.length ? values[index] : fallback;
    }

    private static int intAt(JsonObject root, String key, int fallback) {
        JsonElement element = root == null ? null : root.get(key);
        return element == null ? fallback : element.getAsInt();
    }

    private static Anchor anchor(JsonObject root, String key) {
        int[] v = ints(root, key);
        return new Anchor(at(v, 0, 0), at(v, 1, 0));
    }

    private static Grid grid(JsonObject root, String key) {
        int[] v = ints(root, key);
        return new Grid(at(v, 0, 0), at(v, 1, 0), Math.max(1, at(v, 2, 1)), Math.max(1, at(v, 3, 1)));
    }

    /**
     * Reads the vis columns and the craft progress column after them: one {@code [sourceU, x, y]} triple
     * per entry, with progress last so {@code PRIMAL_COLUMNS} indexes at it. No fallback on purpose.
     */
    private static VisBars visBars(JsonObject root, String key) {
        List<VisBars.Column> columns = new ArrayList<>();
        JsonElement element = root == null ? null : root.get(key);
        if (element != null && element.isJsonObject()) {
            JsonObject bars = element.getAsJsonObject();
            JsonElement list = bars.get("columns");
            if (list != null && list.isJsonArray()) {
                for (JsonElement entry : list.getAsJsonArray()) {
                    columns.add(column(entry));
                }
            }
            // Last, so that PRIMAL_COLUMNS indexes straight at it - see ScreenArcaneAssembler.columnRatio.
            JsonElement progress = bars.get("progress");
            if (progress != null && progress.isJsonArray()) {
                columns.add(column(progress));
            }
        }
        return new VisBars(List.copyOf(columns));
    }

    private static VisBars.Column column(JsonElement entry) {
        int[] v = new int[3];
        JsonArray triple = entry.getAsJsonArray();
        for (int i = 0; i < 3 && i < triple.size(); i++) {
            v[i] = triple.get(i).getAsInt();
        }
        return new VisBars.Column(v[0], v[1], v[2]);
    }

    private static Region region(JsonObject regions, String key) {
        int[] v = ints(regions, key);
        return new Region(at(v, 0, 0), at(v, 1, 0), at(v, 2, 0), at(v, 3, 0));
    }


    public static @Nullable GuiLayout load() {
        try (InputStream stream = GuiLayout.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                ThaumicEnergistics.LOG.error("Missing GUI layout {}", RESOURCE);
                return null;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return new GuiLayout(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception e) {
            ThaumicEnergistics.LOG.error("Could not read GUI layout {}", RESOURCE, e);
            return null;
        }
    }

    public int imageWidth() {
        return imageWidth;
    }

    public ResourceLocation texture() {
        ResourceLocation location = ResourceLocation.tryParse(texture);
        if (location == null) {
            ThaumicEnergistics.LOG.error("GUI layout declares an invalid texture path: {}", texture);
            return ResourceLocation.fromNamespaceAndPath(
                    "thaumicenergistics_ce", "textures/gui/arcane_assembler.png");
        }
        return location;
    }

    public int imageHeight() {
        return imageHeight;
    }

    public Anchor title() {
        return title;
    }

    public Anchor inventoryLabel() {
        return inventoryLabel;
    }

    public Grid patternGrid() {
        return patternGrid;
    }

    public Anchor targetSlot() {
        return targetSlot;
    }

    public Anchor coreSlot() {
        return coreSlot;
    }

    public Grid upgradeSlots() {
        return upgradeSlots;
    }

    public Grid armorSlots() {
        return armorSlots;
    }

    public VisBars visBars() {
        return visBars;
    }

    public Grid previewGrid() {
        return previewGrid;
    }

    /** Where the preview's result item is drawn, one pixel in from its well. */
    public Anchor previewResult() {
        return previewResult;
    }

    public Anchor playerInventory() {
        return playerInventory;
    }

    public Anchor hotbar() {
        return hotbar;
    }

    public List<PanelPiece> panels() {
        return panels;
    }
}
