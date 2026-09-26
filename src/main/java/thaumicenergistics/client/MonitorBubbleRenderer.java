package thaumicenergistics.client;

import com.leclowndu93150.thaumaturge.api.aspect.AspectKnowledgeAccess;
import com.leclowndu93150.thaumaturge.api.aspect.Aspects;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.client.AspectRendering;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import thaumicenergistics.blockentity.BlockEntityInfusionMonitor;

/**
 * The bubble the Infusion Monitor floats above itself: how dangerous the altar it watches is, what that
 * altar is making, and whether the room can finish it.
 *
 * <p>Drawn, not spawned: the reference's {@code TextDisplay} entity could be left behind by a crash and had
 * to be found again after a reload.
 */
public class MonitorBubbleRenderer implements BlockEntityRenderer<BlockEntityInfusionMonitor> {

    /** How far above the block the panel floats - clear of the model's own top face. */
    private static final double HEIGHT = 1.7;

    /** Text scale. Half a name tag's: this is a label on a machine, not a label on a distant entity. */
    private static final float SCALE = 0.0125F;

    /** Line spacing in text units; eleven is the floor: nine pixel glyphs plus a drop shadow overlap at ten. */
    private static final int LINE_HEIGHT = 11;

    /** The aspect chip, the gap to its badge, and the gap between one chip and the next. */
    private static final int CHIP = 12;
    private static final int CELL_GAP = 5;

    private static final int CHIP_ROW_HEIGHT = CHIP + 2;

    /** The panel's fill, the two colours of its border, and how round its corners are. */
    private static final int PANEL_FILL = 0xF0100010;
    private static final int BORDER_TOP = 0x505000FF;
    private static final int BORDER_BOTTOM = 0x5028007F;
    /** The corner radius, in the panel's own units - two, the same as Jade's box. Ten read as far too round. */
    private static final float CORNER_RADIUS = 2.0F;

    /** Border and fill sit at different depths: coplanar quads fight for the same depth and flicker. */
    private static final float BORDER_Z = -0.08F;
    private static final float FILL_Z = -0.06F;

    /** How much room the panel leaves around its contents, in pixels of the text's own units. */
    private static final float PADDING_X = 5.0F;
    private static final float PADDING_Y = 4.0F;

    /** The colour the through-wall copy of the text is drawn in - vanilla's, from a name tag. */
    private static final int SEE_THROUGH_TEXT = 553648127;

    /** Off unless {@code THAUMICENERGISTICS_MONITOR_TRACE=true}; the bubble is drawn, not sent. */
    private static final boolean TRACE =
            "true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_MONITOR_TRACE"));

    private static long lastTrace;

    public MonitorBubbleRenderer(BlockEntityRendererProvider.Context context) {}

    // --- Contents ---

    private sealed interface Cell {}

    private record TextCell(Component text) implements Cell {}

    private record ChipCell(Holder<IAspect> aspect) implements Cell {}

    @Override
    public void render(
            BlockEntityInfusionMonitor monitor,
            float partialTick,
            PoseStack pose,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay) {
        if (TRACE) {
            long now = System.currentTimeMillis();
            if (now - lastTrace > 2000) {
                lastTrace = now;
                thaumicenergistics.ThaumicEnergistics.LOG.info(
                        "[bubble] at {} reporting={} tier={} instability={} crafting={} essentia={} light={}",
                        monitor.getBlockPos(), monitor.bubbleReporting(), monitor.bubbleTier(),
                        monitor.bubbleInstability(), monitor.bubbleCrafting(), monitor.bubbleEssentia(),
                        Integer.toHexString(packedLight));
            }
        }
        if (!monitor.bubbleReporting()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        List<List<Cell>> rows = rowsFor(font, monitor);
        if (rows.isEmpty()) {
            return;
        }

        float width = 0;
        float height = 0;
        for (List<Cell> row : rows) {
            width = Math.max(width, rowWidth(font, row));
            height += rowHeight(row);
        }
        float panelWidth = width + PADDING_X * 2;
        float panelHeight = height + PADDING_Y * 2;

        pose.pushPose();
        pose.translate(0.5, HEIGHT, 0.5);
        pose.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(SCALE, -SCALE, SCALE);
        Matrix4f matrix = pose.last().pose();

        // Centred on the anchor, so the panel grows both ways rather than downwards from the block's face.
        float left = -panelWidth / 2.0F;
        float top = -panelHeight / 2.0F;
        roundedPanel(buffers, matrix, left, top, left + panelWidth, top + panelHeight);

        float y = top + PADDING_Y;
        for (List<Cell> row : rows) {
            float rowWidth = rowWidth(font, row);
            float x = -rowWidth / 2.0F;
            // Centred on the row: a chip row is taller than a text row.
            float textY = y + (rowHeight(row) - font.lineHeight) / 2.0F;
            for (Cell cell : row) {
                drawCell(pose, buffers, font, cell, x, y, textY, true);
                x += cellWidth(font, cell) + CELL_GAP;
            }
            y += rowHeight(row);
        }
        // The text again with the depth test off, so the numbers read through walls; not the chips.
        y = top + PADDING_Y;
        for (List<Cell> row : rows) {
            float rowWidth = rowWidth(font, row);
            float x = -rowWidth / 2.0F;
            float textY = y + (rowHeight(row) - font.lineHeight) / 2.0F;
            for (Cell cell : row) {
                if (cell instanceof TextCell text) {
                    drawText(font, text.text(), x, textY, buffers, matrix, false);
                }
                x += cellWidth(font, cell) + CELL_GAP;
            }
            y += rowHeight(row);
        }
        pose.popPose();
    }

    /** Rows already built, per monitor, with the state they were built from. Weakly keyed. */
    private final Map<BlockEntityInfusionMonitor, Built> built = new WeakHashMap<>();

    private record Built(int tier, String stability, boolean crafting, ItemStack craft,
            List<BlockEntityInfusionMonitor.EssentiaLine> essentia, List<List<Cell>> rows) {}

    /** Rebuilt only when the synced state changes; drawn every frame, so compared by value. */
    private List<List<Cell>> rowsFor(Font font, BlockEntityInfusionMonitor monitor) {
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

    /** What the bubble says, top to bottom. */
    private static List<List<Cell>> rows(Font font, BlockEntityInfusionMonitor monitor,
            List<BlockEntityInfusionMonitor.EssentiaLine> essentia, ItemStack craft) {
        List<List<Cell>> rows = new ArrayList<>();
        int tier = monitor.bubbleTier();
        rows.add(List.of(new TextCell(Component.translatable(
                        "thaumicenergistics.monitor.bubble.risk",
                        Component.translatable("thaumicenergistics.jade.monitor.risk." + tier))
                .withStyle(style -> style.withColor(colourOf(tier))))));
        rows.add(List.of(new TextCell(Component.translatable(
                "thaumicenergistics.monitor.bubble.instability", monitor.bubbleStability()))));

        if (monitor.bubbleCrafting() && !craft.isEmpty()) {
            rows.add(List.of(new TextCell(Component.translatable(
                            "thaumicenergistics.monitor.bubble.crafting",
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

    /** The aspect behind an id from the sync tag, or {@code null} if this client has never seen it. */
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

    private static float cellWidth(Font font, Cell cell) {
        return switch (cell) {
            case TextCell text -> font.width(text.text());
            case ChipCell ignored -> CHIP;
        };
    }

    private static float rowWidth(Font font, List<Cell> row) {
        float width = 0;
        for (int i = 0; i < row.size(); i++) {
            width += cellWidth(font, row.get(i));
            if (i < row.size() - 1) {
                width += CELL_GAP;
            }
        }
        return width;
    }

    private static float rowHeight(List<Cell> row) {
        return row.get(0) instanceof ChipCell ? CHIP_ROW_HEIGHT : LINE_HEIGHT;
    }

    /** Draws one cell at {@code x}, {@code y} - the row's top-left corner. */
    private static void drawCell(
            PoseStack pose,
            MultiBufferSource buffers,
            Font font,
            Cell cell,
            float x,
            float rowTop,
            float textY,
            boolean solid) {
        switch (cell) {
            case TextCell text -> drawText(font, text.text(), x, textY, buffers, pose.last().pose(), solid);
            case ChipCell chip -> {
                // Drawn by Thaumaturge's own world renderer, so texture, blend and the undiscovered-aspect
                // mask are right. Negative vertical scale: the panel's pose is (x, -y), which flips textures.
                pose.pushPose();
                pose.translate(x + CHIP / 2.0F, rowTop + CHIP / 2.0F, 0.0F);
                pose.scale(CHIP, -CHIP, 1.0F);
                var knowledge = AspectKnowledgeAccess.of(chip.aspect());
                AspectRendering.renderQuad(
                        pose,
                        buffers.getBuffer(AspectRendering.renderType(
                                chip.aspect(), knowledge, AspectRendering.BlendMode.ALPHA)),
                        chip.aspect(),
                        1.0F,
                        false,
                        LightTexture.FULL_BRIGHT);
                pose.popPose();
            }
        }
    }

    /** One line of text. Drawn lit deliberately: the block's own light would leave it dark on dark. */
    private static void drawText(
            Font font,
            Component text,
            float x,
            float y,
            MultiBufferSource buffers,
            Matrix4f matrix,
            boolean solid) {
        font.drawInBatch(
                text,
                x,
                y,
                solid ? 0xFFFFFFFF : SEE_THROUGH_TEXT,
                true,
                matrix,
                buffers,
                solid ? Font.DisplayMode.NORMAL : Font.DisplayMode.SEE_THROUGH,
                0,
                LightTexture.FULL_BRIGHT);
    }

    // --- The panel itself ---

    /**
     * The box: a filled rounded rectangle with a one-pixel gradient border, the way Jade draws tooltips.
     * {@code debugQuads} takes position and colour only, which is all the panel has to give.
     */
    private static void roundedPanel(
            MultiBufferSource buffers, Matrix4f matrix, float left, float top, float right, float bottom) {
        VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
        // The border is the whole shape in the gradient, the fill the same shape one pixel in, so the radius
        // is one less. Separate depths because sprites in one plane fight for it and flicker.
        roundedFill(quads, matrix, left, top, right, bottom, CORNER_RADIUS, BORDER_TOP, BORDER_BOTTOM, BORDER_Z);
        roundedFill(
                quads,
                matrix,
                left + 1,
                top + 1,
                right - 1,
                bottom - 1,
                CORNER_RADIUS - 1,
                PANEL_FILL,
                PANEL_FILL,
                FILL_Z);
    }

    /** A rounded rectangle filled with a vertical gradient; corners are one strip per unit of radius. */
    private static void roundedFill(
            VertexConsumer quads,
            Matrix4f matrix,
            float left,
            float top,
            float right,
            float bottom,
            float radius,
            int topColour,
            int bottomColour,
            float z) {
        float r = Math.max(0.0F, Math.min(radius, Math.min((right - left) / 2.0F, (bottom - top) / 2.0F)));
        if (r < 1.0F) {
            fill(quads, matrix, left, top, right, bottom, topColour, bottomColour, z);
            return;
        }
        strip(quads, matrix, left + r, top, right - r, top + r, top, bottom, topColour, bottomColour, z);
        strip(quads, matrix, left, top + r, right, bottom - r, top, bottom, topColour, bottomColour, z);
        strip(quads, matrix, left + r, bottom - r, right - r, bottom, top, bottom, topColour, bottomColour, z);

        int steps = (int) Math.ceil(r);
        for (int i = 0; i < steps; i++) {
            float dy = r - i - 0.5F;
            float dx = r - (float) Math.sqrt(Math.max(0.0F, r * r - dy * dy));
            float yTop = top + i;
            float yBottom = bottom - i - 1;
            strip(quads, matrix, left + dx, yTop, left + r, yTop + 1, top, bottom, topColour, bottomColour, z);
            strip(quads, matrix, right - r, yTop, right - dx, yTop + 1, top, bottom, topColour, bottomColour, z);
            strip(quads, matrix, left + dx, yBottom, left + r, yBottom + 1, top, bottom, topColour, bottomColour, z);
            strip(
                    quads,
                    matrix,
                    right - r,
                    yBottom,
                    right - dx,
                    yBottom + 1,
                    top,
                    bottom,
                    topColour,
                    bottomColour,
                    z);
        }
    }

    /** A rectangle whose colour is taken from the panel's gradient at its own height. */
    private static void strip(
            VertexConsumer quads,
            Matrix4f matrix,
            float x0,
            float y0,
            float x1,
            float y1,
            float panelTop,
            float panelBottom,
            int topColour,
            int bottomColour,
            float z) {
        float span = Math.max(1.0F, panelBottom - panelTop);
        fill(
                quads,
                matrix,
                x0,
                y0,
                x1,
                y1,
                mix(topColour, bottomColour, (y0 - panelTop) / span),
                mix(topColour, bottomColour, (y1 - panelTop) / span),
                z);
    }

    /** One quad, from the top-left to the bottom-right, coloured from the top edge to the bottom edge. */
    private static void fill(
            VertexConsumer quads,
            Matrix4f matrix,
            float x0,
            float y0,
            float x1,
            float y1,
            int topColour,
            int bottomColour,
            float z) {
        quads.addVertex(matrix, x0, y0, z).setColor(topColour);
        quads.addVertex(matrix, x0, y1, z).setColor(bottomColour);
        quads.addVertex(matrix, x1, y1, z).setColor(bottomColour);
        quads.addVertex(matrix, x1, y0, z).setColor(topColour);
    }

    /** A colour a fraction of the way from one to the other, alpha included. */
    private static int mix(int from, int to, float t) {
        float f = Math.max(0.0F, Math.min(1.0F, t));
        int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * f);
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * f);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * f);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Green through red, the same five colours the tooltip uses. */
    private static int colourOf(int tier) {
        return switch (tier) {
            case 1 -> 0xFF55FF55;
            case 2 -> 0xFFAAFF55;
            case 3 -> 0xFFFFFF55;
            case 4 -> 0xFFFFAA55;
            default -> 0xFFFF5555;
        };
    }
}
