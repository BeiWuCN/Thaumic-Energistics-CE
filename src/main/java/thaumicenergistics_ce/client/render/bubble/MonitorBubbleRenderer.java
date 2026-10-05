package thaumicenergistics_ce.client.render.bubble;

import com.leclowndu93150.thaumaturge.api.aspect.AspectKnowledgeAccess;
import com.leclowndu93150.thaumaturge.api.client.AspectRendering;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import thaumicenergistics_ce.blockentity.BlockEntityInfusionMonitor;
import thaumicenergistics_ce.client.render.bubble.BubbleCells.Cell;
import thaumicenergistics_ce.client.render.bubble.BubbleCells.ChipCell;
import thaumicenergistics_ce.client.render.bubble.BubbleCells.TextCell;
import thaumicenergistics_ce.util.ThELog;

/**
 * The bubble the Infusion Monitor floats above itself: how dangerous the altar is, what it is making,
 * whether the room can finish it, and that it is drawn rather than spawned - a {@code TextDisplay}
 * entity can be left behind by a crash. What it says is {@link BubbleCells}, the box it sits on
 * {@link RoundedPanel}; what is left here is the pose and the two ways a cell is drawn.
 */
public class MonitorBubbleRenderer implements BlockEntityRenderer<BlockEntityInfusionMonitor> {

    private static final double HEIGHT = 1.7;

    private static final float SCALE = 0.0125F;

    private static final float PADDING_X = 5.0F;
    private static final float PADDING_Y = 4.0F;

    /** The colour the through-wall copy of the text is drawn in - vanilla's, from a name tag. */
    private static final int SEE_THROUGH_TEXT = 553648127;

    /** Off unless {@code THAUMICENERGISTICS_MONITOR_TRACE=true}; the bubble is drawn, not sent. */
    private static final boolean TRACE =
            "true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_MONITOR_TRACE"));

    private static long lastTrace;

    private final BubbleCells cells = new BubbleCells();

    public MonitorBubbleRenderer(BlockEntityRendererProvider.Context context) {}

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
                ThELog.LOG.info(
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
        List<List<Cell>> rows = cells.rowsFor(font, monitor);
        if (rows.isEmpty()) {
            return;
        }

        float width = 0;
        float height = 0;
        for (List<Cell> row : rows) {
            width = Math.max(width, BubbleCells.rowWidth(font, row));
            height += BubbleCells.rowHeight(row);
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
        RoundedPanel.draw(buffers, matrix, left, top, left + panelWidth, top + panelHeight);

        float y = top + PADDING_Y;
        for (List<Cell> row : rows) {
            float rowWidth = BubbleCells.rowWidth(font, row);
            float x = -rowWidth / 2.0F;
            // Centred on the row: a chip row is taller than a text row.
            float textY = y + (BubbleCells.rowHeight(row) - font.lineHeight) / 2.0F;
            for (Cell cell : row) {
                drawCell(pose, buffers, font, cell, x, y, textY, true);
                x += BubbleCells.stride(font, cell);
            }
            y += BubbleCells.rowHeight(row);
        }
        // The text again with the depth test off, so the numbers read through walls; not the chips.
        y = top + PADDING_Y;
        for (List<Cell> row : rows) {
            float rowWidth = BubbleCells.rowWidth(font, row);
            float x = -rowWidth / 2.0F;
            float textY = y + (BubbleCells.rowHeight(row) - font.lineHeight) / 2.0F;
            for (Cell cell : row) {
                if (cell instanceof TextCell text) {
                    drawText(font, text.text(), x, textY, buffers, matrix, false);
                }
                x += BubbleCells.stride(font, cell);
            }
            y += BubbleCells.rowHeight(row);
        }
        pose.popPose();
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
                float size = BubbleCells.CHIP;
                pose.pushPose();
                pose.translate(x + size / 2.0F, rowTop + size / 2.0F, 0.0F);
                pose.scale(size, -size, 1.0F);
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
}
