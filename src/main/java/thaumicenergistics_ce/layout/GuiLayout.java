package thaumicenergistics_ce.layout;

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
import thaumicenergistics_ce.util.ThELog;

/**
 * 奥术组装机界面的几何数据，从 classpath 上的 {@code arcane_assembler_gui.json} 读取。
 * 坐标是照着 GUI 素材的槽位凹槽量出来的，所以图像凹槽与菜单槽位落在
 * 相同的像素上。布局在两侧都会读取，没有重载监听器，由
 * {@code tools/build_assembler_layout.js} 写入。
 */
public final class GuiLayout {

    private static final String RESOURCE = "/assets/thaumicenergistics_ce/gui/arcane_assembler_gui.json";

    /** 窗口尺寸，与参考界面一致；仅在布局文件无法读取时使用。 */
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
     * 槽的凹槽从哪里开始，以及它有多少行。素材的第一行精灵是凹槽的
     * 阴影，所以只有第 1..15 行是凹槽；画满十六行会在满列上重绘阴影。
     */
    public static final int TROUGH_INSET = 1;
    public static final int TROUGH_INTERIOR = BAR_HEIGHT - TROUGH_INSET;

    /** 元初要素列的数量；它们之后的 vis 列是合成进度。 */
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
    /** 背景素材中不透明的那些片段，按绘制顺序。若改成一次性贴一个窗口大小的矩形，会
     * 把侧板右侧的透明区域也画上，并把停在面板下方的条形精灵一并拖进来。 */
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
     * 读取 vis 列以及它们之后的合成进度列：每个条目一个 {@code [sourceU, x, y]} 三元组，
     * 进度放在最后，好让 {@code PRIMAL_COLUMNS} 直接索引到它。刻意不做回退。
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
            // 放在最后，使 PRIMAL_COLUMNS 直接索引到它——见 ScreenArcaneAssembler.columnRatio。
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

    /** mod 自带的布局，文件无法读取时则用内置的数字：没有槽位
     * 位置的菜单不成其为菜单，所以文件缺失是一个值得记录日志的打包错误。 */
    public static GuiLayout load() {
        try (InputStream stream = GuiLayout.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                ThELog.LOG.error("Missing GUI layout {}", RESOURCE);
                return builtIn();
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return new GuiLayout(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception e) {
            ThELog.LOG.error("Could not read GUI layout {}", RESOURCE, e);
            return builtIn();
        }
    }

    /** 生成文件所携带的槽位几何数据，以代码形式写在这里，用于 load() 无法读取它的情形：仅素材
     * 使用的字段留空，因为菜单不读取其中任何一个。 */
    private static GuiLayout builtIn() {
        JsonObject root = new JsonObject();
        root.add("patternGrid", array(26, 15, 7, 3));
        root.add("coreSlot", array(175, 6));
        root.add("upgradeSlots", array(175, 24, 1, 4));
        root.add("armorSlots", array(152, 73, 1, 4));
        root.add("previewGrid", array(26, 81, 3, 3));
        root.add("previewResult", array(115, 98));
        root.add("playerInventory", array(8, 147));
        root.add("hotbar", array(8, 205));
        root.add("panelOrder", new JsonArray());
        return new GuiLayout(root);
    }

    private static JsonArray array(int... values) {
        JsonArray array = new JsonArray();
        for (int value : values) {
            array.add(value);
        }
        return array;
    }

    public int imageWidth() {
        return imageWidth;
    }

    public ResourceLocation texture() {
        ResourceLocation location = ResourceLocation.tryParse(texture);
        if (location == null) {
            ThELog.LOG.error("GUI layout declares an invalid texture path: {}", texture);
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

    /** 预览的结果物品绘制在哪里，从其凹槽向内一个像素。 */
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
