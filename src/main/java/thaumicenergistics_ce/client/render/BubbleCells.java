package thaumicenergistics_ce.client.render;

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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.blockentity.BlockEntityInfusionMonitor;

/**
 * What the bubble says, as rows of cells: a chip is an aspect drawn from Thaumaturge's own textures, a text
 * cell is a line of the font. The rows are rebuilt only when the monitor's report changes; the measures the
 * renderer lays them out with live here too, so one chip and one line are the same size everywhere.
 */
final class BubbleCells {

    /** Line spacing in text units; eleven is the floor: glyphs plus a drop shadow overlap at ten. */
    private static final int LINE_HEIGHT = 11;

    /** The aspect chip, the gap to its badge, and the gap between one chip and the next. */
    static final int CHIP = 12;
    private static final int CELL_GAP = 5;

    private static final int CHIP_ROW_HEIGHT = CHIP + 2;

    sealed interface Cell {}

    record TextCell(Component text) implements Cell {}

    record ChipCell(Holder<IAspect> aspect) implements Cell {}

    private record Built(int tier, String stability, boolean crafting, ItemStack craft,
            List<BlockEntityInfusionMonitor.EssentiaLine> essentia, List<List<Cell>> rows) {}

    private final Map<BlockEntityInfusionMonitor, Built> built = new WeakHashMap<>();

    List<List<Cell>> rowsFor(Font font, BlockEntityInfusionMonitor monitor) {
        List<BlockEntityInfusionMonitor.EssentiaLine> essentia = monitor.bubbleEssentia();
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

    private static List<List<Cell>> rows(Font font, BlockEntityInfusionMonitor monitor,
            List<BlockEntityInfusionMonitor.EssentiaLine> essentia, ItemStack craft) {
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

        // One aspect per row; a full one turns green.
        for (BlockEntityInfusionMonitor.EssentiaLine line : essentia) {
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
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null || minecraft.level == null) {
            return null;
        }
        return Aspects.resolve(
                minecraft.level.registryAccess(),
                ResourceKey.create(IAspect.REGISTRY_KEY, location));
    }

    // --- Layout ---

    /** One cell and the gap that follows it, which is how far the next cell starts to its right. */
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
