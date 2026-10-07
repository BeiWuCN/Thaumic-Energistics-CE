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
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import thaumicenergistics_ce.blockentity.occultmonitor.BlockEntityOccultMonitor;
import thaumicenergistics_ce.client.render.bubble.BubbleCells.Cell;
import thaumicenergistics_ce.client.render.bubble.BubbleCells.ChipCell;
import thaumicenergistics_ce.client.render.bubble.BubbleCells.TextCell;
import thaumicenergistics_ce.util.ThELog;

/**
 * 神秘监控器悬浮在自己上方的气泡：祭坛有多危险、
 * 它正在制作什么、房间能否完成它，以及它是被绘制而非被生成的
 * ——崩溃可能留下一个 {@code TextDisplay} 实体。它显示的内容是
 * {@link BubbleCells}，承载它的框是 {@link RoundedPanel}；留在这里的是姿态、
 * 绘制单元格的两种方式，以及绘制该面板所及的几个方块范围。
 */
public class OccultMonitorBubbleRenderer implements BlockEntityRenderer<BlockEntityOccultMonitor> {

    private static final double HEIGHT = 1.7;

    private static final float SCALE = 0.0125F;

    private static final float PADDING_X = 5.0F;
    private static final float PADDING_Y = 4.0F;

    /** 气泡绘制的距离：8 个方块。超过这个距离玩家已远离机器，
     * 所以面板不构建、不测量也不绘制，根本不会传到此处。 */
    private static final int CULL_RANGE = 8;

    /** 穿透墙壁的那份文本绘制时所用的颜色——取自命名牌的原版颜色。 */
    private static final int SEE_THROUGH_TEXT = 553648127;

    /** 除非 {@code THAUMICENERGISTICS_MONITOR_TRACE=true}，否则关闭；气泡是绘制的，不是发送的。 */
    private static final boolean TRACE =
            "true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_MONITOR_TRACE"));

    private static long lastTrace;

    private final BubbleCells cells = new BubbleCells();

    public OccultMonitorBubbleRenderer(BlockEntityRendererProvider.Context context) {}

    /** 同样是这 8 个方块，在 {@link #shouldRender} 之前询问：机器直接从客户端
     * 遍历的列表中剔除，而不是等遍历到它才跳过。 */
    @Override
    public int getViewDistance() {
        return CULL_RANGE;
    }

    /** 剔除的近距离一半：阅读距离，从机器自身所在方块起算。 */
    @Override
    public boolean shouldRender(BlockEntityOccultMonitor monitor, Vec3 cameraPos) {
        return Vec3.atCenterOf(monitor.getBlockPos()).closerThan(cameraPos, CULL_RANGE);
    }

    @Override
    public void render(
            BlockEntityOccultMonitor monitor,
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

        // 以锚点为中心，所以面板向两侧展开，而不是从方块表面向下延伸。
        float left = -panelWidth / 2.0F;
        float top = -panelHeight / 2.0F;
        RoundedPanel.draw(buffers, matrix, left, top, left + panelWidth, top + panelHeight);

        float y = top + PADDING_Y;
        for (List<Cell> row : rows) {
            float rowWidth = BubbleCells.rowWidth(font, row);
            float x = -rowWidth / 2.0F;
            // 在行内居中：芯片行比文本行更高。
            float textY = y + (BubbleCells.rowHeight(row) - font.lineHeight) / 2.0F;
            for (Cell cell : row) {
                drawCell(pose, buffers, font, cell, x, y, textY, true);
                x += BubbleCells.stride(font, cell);
            }
            y += BubbleCells.rowHeight(row);
        }
        // 关闭深度测试再画一遍文本，让数字能穿墙阅读；芯片不这样处理。
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

    /** 在 {@code x}、{@code y} 处绘制一个单元格——即该行的左上角。 */
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
                // 由 Thaumaturge 自身的世界渲染器绘制，这样纹理、混合和未发现要素
                // 遮罩都正确。垂直方向取负缩放：面板姿态是 (x, -y)，会翻转纹理。
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
