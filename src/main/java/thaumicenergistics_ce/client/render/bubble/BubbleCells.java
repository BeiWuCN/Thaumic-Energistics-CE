package thaumicenergistics_ce.client.render.bubble;

import com.leclowndu93150.thaumaturge.api.aspect.Aspects;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.blockentity.occultmonitor.BlockEntityOccultMonitor;

/**
 * 气泡显示的内容，以单元格的行表示：芯片是用 Thaumaturge 自身纹理绘制的要素，
 * 文本单元格是一行字体。只有监控器的报告变化时才重建各行；
 * 渲染器排版用的量度也放在这里，芯片和一行文本在任何地方尺寸都一样。
 */
final class BubbleCells {

    /** 行间距，单位文本单位；11 是下限：字形加投影在 10 时会重叠。 */
    private static final int LINE_HEIGHT = 11;

    /** 要素芯片的尺寸、到徽章的间距、芯片之间的间距。 */
    static final int CHIP = 12;
    private static final int CELL_GAP = 5;

    private static final int CHIP_ROW_HEIGHT = CHIP + 2;

    sealed interface Cell {}

    record TextCell(Component text) implements Cell {}

    record ChipCell(Holder<IAspect> aspect) implements Cell {}

    private record Built(int tier, String stability, boolean crafting, ItemStack craft,
            List<BlockEntityOccultMonitor.EssentiaLine> essentia, List<List<Cell>> rows) {}

    private final Map<BlockEntityOccultMonitor, Built> built = new WeakHashMap<>();

    List<List<Cell>> rowsFor(Font font, BlockEntityOccultMonitor monitor) {
        List<BlockEntityOccultMonitor.EssentiaLine> essentia = monitor.bubbleEssentia();
        ItemStack craft = monitor.bubbleCraft();
        Built previous = built.get(monitor);
        if (previous != null
                && previous.tier() == monitor.bubbleTier()
                && previous.stability().equals(monitor.bubbleStability())
                && previous.crafting() == monitor.bubbleCrafting()
                && ItemStack.matches(previous.craft(), craft)
                && previous.essentia().equals(essentia)) {
            return previous.rows();
        }
        List<List<Cell>> rows = rows(font, monitor, essentia, craft);
        built.put(monitor, new Built(monitor.bubbleTier(), monitor.bubbleStability(),
                monitor.bubbleCrafting(), craft, essentia, rows));
        return rows;
    }

    private static List<List<Cell>> rows(Font font, BlockEntityOccultMonitor monitor,
            List<BlockEntityOccultMonitor.EssentiaLine> essentia, ItemStack craft) {
        List<List<Cell>> rows = new ArrayList<>();
        int tier = monitor.bubbleTier();
        rows.add(List.of(new TextCell(Component.translatable(
                        "thaumicenergistics_ce.monitor.bubble.risk",
                        Component.translatable("thaumicenergistics_ce.jade.monitor.risk." + tier))
                .withStyle(style -> style.withColor(RoundedPanel.colourOf(tier))))));
        rows.add(List.of(new TextCell(Component.translatable(
                "thaumicenergistics_ce.monitor.bubble.instability", monitor.bubbleStability()))));

        if (monitor.bubbleCrafting() && !craft.isEmpty()) {
            rows.add(List.of(new TextCell(Component.translatable(
                            "thaumicenergistics_ce.monitor.bubble.crafting",
                            monitor.bubbleCraft().getHoverName())
                    .withStyle(ChatFormatting.WHITE))));
        }

        // 每行一个要素；已满的那行变绿。
        for (BlockEntityOccultMonitor.EssentiaLine line : essentia) {
            Holder<IAspect> aspect = aspectOf(line.aspect());
            if (aspect == null) {
                continue;
            }
            boolean complete = line.drawn() >= line.total();
            rows.add(List.of(
                    new ChipCell(aspect),
                    new TextCell(Component.literal(line.drawn() + " / " + line.total())
                            .withStyle(complete ? ChatFormatting.GREEN : ChatFormatting.WHITE))));
        }
        return rows;
    }

    private static Holder<IAspect> aspectOf(String id) {
        Minecraft minecraft = Minecraft.getInstance();
        Identifier location = Identifier.tryParse(id);
        if (location == null || minecraft.level == null) {
            return null;
        }
        return Aspects.resolve(
                minecraft.level.registryAccess(),
                ResourceKey.create(IAspect.REGISTRY_KEY, location));
    }

    // --- 布局 ---

    /** 一个单元格到下一个单元格起点的距离，含间距。 */
    static float stride(Font font, Cell cell) {
        return cellWidth(font, cell) + CELL_GAP;
    }

    static float rowWidth(Font font, List<Cell> row) {
        float width = 0;
        for (int i = 0; i < row.size(); i++) {
            width += cellWidth(font, row.get(i));
            if (i < row.size() - 1) {
                width += CELL_GAP;
            }
        }
        return width;
    }

    static float rowHeight(List<Cell> row) {
        return row.get(0) instanceof ChipCell ? CHIP_ROW_HEIGHT : LINE_HEIGHT;
    }

    private static float cellWidth(Font font, Cell cell) {
        return switch (cell) {
            case TextCell text -> font.width(text.text());
            case ChipCell ignored -> CHIP;
        };
    }
}
