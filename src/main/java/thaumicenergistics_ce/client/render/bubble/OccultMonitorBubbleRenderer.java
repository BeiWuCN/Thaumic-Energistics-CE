package thaumicenergistics_ce.client.render.bubble;

import com.leclowndu93150.thaumaturge.api.aspect.AspectKnowledgeAccess;
import com.leclowndu93150.thaumaturge.api.client.AspectRendering;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.occultmonitor.BlockEntityOccultMonitor;
import thaumicenergistics_ce.client.render.bubble.BubbleCells.Cell;
import thaumicenergistics_ce.client.render.bubble.BubbleCells.ChipCell;
import thaumicenergistics_ce.client.render.bubble.BubbleCells.TextCell;
import thaumicenergistics_ce.util.ThELog;

public class OccultMonitorBubbleRenderer
        implements BlockEntityRenderer<BlockEntityOccultMonitor, OccultMonitorBubbleRenderState> {

    private static final double HEIGHT = 1.7;

    private static final float SCALE = 0.0125F;

    private static final float PADDING_X = 5.0F;
    private static final float PADDING_Y = 4.0F;

    private static final int CULL_RANGE = 8;

    private static final int SEE_THROUGH_TEXT = 553648127;

    private static final boolean TRACE =
            "true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_MONITOR_TRACE"));

    private static long lastTrace;

    private final BubbleCells cells = new BubbleCells();

    public OccultMonitorBubbleRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public OccultMonitorBubbleRenderState createRenderState() {
        return new OccultMonitorBubbleRenderState();
    }

    @Override
    public int getViewDistance() {
        return CULL_RANGE;
    }

    @Override
    public boolean shouldRender(BlockEntityOccultMonitor monitor, Vec3 cameraPos) {
        return Vec3.atCenterOf(monitor.getBlockPos()).closerThan(cameraPos, CULL_RANGE);
    }

    @Override
    public void extractRenderState(
            BlockEntityOccultMonitor monitor,
            OccultMonitorBubbleRenderState state,
            float partialTick,
            Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(monitor, state, partialTick, cameraPosition, breakProgress);
        state.rows = null;

        if (TRACE) {
            long now = System.currentTimeMillis();
            if (now - lastTrace > 2000) {
                lastTrace = now;
                ThELog.LOG.info(
                        "[bubble] at {} reporting={} tier={} instability={} crafting={} essentia={} light={}",
                        monitor.getBlockPos(), monitor.bubbleReporting(), monitor.bubbleTier(),
                        monitor.bubbleInstability(), monitor.bubbleCrafting(), monitor.bubbleEssentia(),
                        Integer.toHexString(state.lightCoords));
            }
        }
        if (!monitor.bubbleReporting()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
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
        state.rows = rows;
        state.panelWidth = width + PADDING_X * 2;
        state.panelHeight = height + PADDING_Y * 2;
    }

    @Override
    public void submit(
            OccultMonitorBubbleRenderState state,
            PoseStack pose,
            SubmitNodeCollector collector,
            CameraRenderState camera) {
        List<List<Cell>> rows = state.rows;
        if (rows == null) {
            return;
        }
        Font font = Minecraft.getInstance().font;

        pose.pushPose();
        pose.translate(0.5, HEIGHT, 0.5);
        pose.mulPose(camera.orientation);
        pose.scale(SCALE, -SCALE, SCALE);

        float left = -state.panelWidth / 2.0F;
        float top = -state.panelHeight / 2.0F;
        RoundedPanel.submit(collector, pose, left, top, left + state.panelWidth, top + state.panelHeight);

        float y = top + PADDING_Y;
        for (List<Cell> row : rows) {
            float rowWidth = BubbleCells.rowWidth(font, row);
            float x = -rowWidth / 2.0F;
            float textY = y + (BubbleCells.rowHeight(row) - font.lineHeight) / 2.0F;
            for (Cell cell : row) {
                drawCell(pose, collector, font, cell, x, y, textY, true);
                x += BubbleCells.stride(font, cell);
            }
            y += BubbleCells.rowHeight(row);
        }
        y = top + PADDING_Y;
        for (List<Cell> row : rows) {
            float rowWidth = BubbleCells.rowWidth(font, row);
            float x = -rowWidth / 2.0F;
            float textY = y + (BubbleCells.rowHeight(row) - font.lineHeight) / 2.0F;
            for (Cell cell : row) {
                if (cell instanceof TextCell text) {
                    drawText(pose, collector, text.text(), x, textY, false);
                }
                x += BubbleCells.stride(font, cell);
            }
            y += BubbleCells.rowHeight(row);
        }
        pose.popPose();
    }

    private static void drawCell(
            PoseStack pose,
            SubmitNodeCollector collector,
            Font font,
            Cell cell,
            float x,
            float rowTop,
            float textY,
            boolean solid) {
        switch (cell) {
            case TextCell text -> drawText(pose, collector, text.text(), x, textY, solid);
            case ChipCell chip -> {
                float size = BubbleCells.CHIP;
                pose.pushPose();
                pose.translate(x + size / 2.0F, rowTop + size / 2.0F, 0.0F);
                pose.scale(size, -size, 1.0F);
                var knowledge = AspectKnowledgeAccess.of(chip.aspect());
                collector.submitCustomGeometry(
                        pose,
                        AspectRendering.renderType(chip.aspect(), knowledge, AspectRendering.BlendMode.ALPHA),
                        (entry, buffer) -> AspectRendering.renderQuad(
                                entry,
                                buffer,
                                chip.aspect(),
                                knowledge,
                                1.0F,
                                false,
                                LightCoordsUtil.FULL_BRIGHT));
                pose.popPose();
            }
        }
    }

    private static void drawText(
            PoseStack pose,
            SubmitNodeCollector collector,
            Component text,
            float x,
            float y,
            boolean solid) {
        collector.submitText(
                pose,
                x,
                y,
                text.getVisualOrderText(),
                true,
                solid ? Font.DisplayMode.NORMAL : Font.DisplayMode.SEE_THROUGH,
                LightCoordsUtil.FULL_BRIGHT,
                solid ? 0xFFFFFFFF : SEE_THROUGH_TEXT,
                0,
                0);
    }
}
