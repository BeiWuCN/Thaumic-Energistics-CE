package thaumicenergistics_ce.client.gui;

import appeng.api.upgrades.Upgrades;

import appeng.client.gui.style.Blitter;
import appeng.core.localization.GuiText;
import appeng.util.Icon;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;
import thaumicenergistics_ce.layout.GuiLayout;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.menu.MenuArcaneAssembler;
import thaumicenergistics_ce.util.ThELog;

/**
 * 奥术组装机界面：把 {@link GuiLayout} 的各块贴到参考实现自己的美术上。
 * 那份美术不是一个矩形，前 102 行宽 197，其余宽 176；按窗口大小整块 blit 会把
 * 透明楔形和停在面板下方的条形贴图一起画出来。
 */
public class ScreenArcaneAssembler extends AbstractContainerScreen<MenuArcaneAssembler> {

    /** 界面美术的完整路径，含扩展名：纹理加载器不补后缀。 */
    private static final Identifier FALLBACK_TEXTURE =
            Identifier.fromNamespaceAndPath(ThEIds.MODID, "textures/gui/arcane_assembler.png");

    /** 从方块实体读。写成第二个字面量时，会和它画的机器对不上。 */
    private static final int VIS_BAR_MAX = BlockEntityArcaneAssembler.visBufferTarget();

    /** 共享 {@link #VIS_BAR_MAX} 池的六分之一：按整池缩放，柱子会被压空。 */
    private static final int VIS_BAR_MAX_PER_ASPECT =
            Math.max(1, VIS_BAR_MAX / GuiLayout.PRIMAL_COLUMNS);

    /** 回退窗口尺寸，与参考实现的常量一致。 */
    private static final int FB_WIDTH = 175;
    private static final int FB_HEIGHT = 231;

    /** 纹理底部那条填充条第一行的 V。 */
    private static final int FILL_TOP = 240;

    /** 只在组装机自检时打开；这一列在服务端查不了。 */
    private static final boolean TRACE_PROGRESS =
            "true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_ASSEMBLER_SELFTEST"));

    /** 上一次记录的数值：五十 tick 的合成只该有五十行，不是三千行。 */
    private float lastTracedProgress = -1.0F;

    private final @Nullable GuiLayout layout;

    private Identifier texture() {
        return layout == null ? FALLBACK_TEXTURE : layout.texture();
    }

    public ScreenArcaneAssembler(MenuArcaneAssembler menu, Inventory inventory, Component title) {
        this(menu, inventory, title, GuiLayout.load());
    }

    // 图像尺寸现在交给超类：26.1.2 把 imageWidth/imageHeight 改成了 final，
    // 布局只能在 super() 之前解析好，而这个字段只能在它之后赋值。
    private ScreenArcaneAssembler(
            MenuArcaneAssembler menu, Inventory inventory, Component title, @Nullable GuiLayout layout) {
        super(menu, inventory, title,
                layout == null ? FB_WIDTH : layout.imageWidth(),
                layout == null ? FB_HEIGHT : layout.imageHeight());
        this.layout = layout;
        if (layout != null) {
            this.titleLabelX = layout.title().x();
            this.titleLabelY = layout.title().y();
            this.inventoryLabelX = layout.inventoryLabel().x();
            this.inventoryLabelY = layout.inventoryLabel().y();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // 存储的样板从知识核心来，槽井只读，先填它们。
        menu.refreshPatternView();
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * 面板、升级图标和 vis 柱归 [extractBackground]，不归 [extractContents]：这一版把一帧拆成两半，
     * 槽里的物品、悬停的那个槽位和 tooltip 都在 [extractContents] 里，占住它又不调 super，
     * 物品和 tooltip 就一起没了。画进背景也就画在物品下面，正是这几样要的层。
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        if (layout == null) {
            return;
        }

        for (GuiLayout.PanelPiece piece : layout.panels()) {
            GuiLayout.Region source = piece.source();
            GuiLayout.Anchor destination = piece.destination();
            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    texture(),
                    leftPos + destination.x(),
                    topPos + destination.y(),
                    source.u(),
                    source.v(),
                    source.w(),
                    source.h(),
                    256,
                    256);
        }

        drawUpgradeIcons(graphics);
        // 不调 [drawPreview]：预览井现在是槽位，原版会画内容，这里再画每个预览物品就重叠一次，差一个像素。
        drawVisColumns(graphics);
    }

    private void drawUpgradeIcons(GuiGraphicsExtractor graphics) {
        GuiLayout.Grid grid = layout.upgradeSlots();
        // 钳到真实存在的槽位：布局是资源文件，网格比机器高会让界面开着的每一帧从渲染循环抛出去。
        int rows = Math.min(grid.rows(),
                thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler.UPGRADE_SLOT_COUNT);
        for (int i = 0; i < rows; i++) {
            if (menu.getUpgradeSlot(i).hasItem()) {
                continue;
            }
            Blitter.icon(Icon.BACKGROUND_UPGRADE)
                    .dest(leftPos + grid.columnX(), topPos + grid.columnY(i))
                    .blit(graphics);
        }
    }

    private void drawVisColumns(GuiGraphicsExtractor graphics) {
        GuiLayout.VisBars bars = layout.visBars();
        for (int i = 0; i < bars.count(); i++) {
            int fill = GuiLayout.VisBars.fillHeight(columnRatio(i));
            if (fill <= 0) {
                continue;
            }
            GuiLayout.VisBars.Column column = bars.column(i);
            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    texture(),
                    leftPos + column.x(),
                    topPos + column.y() + GuiLayout.TROUGH_INSET + (GuiLayout.TROUGH_INTERIOR - fill),
                    column.sourceU(),
                    FILL_TOP + GuiLayout.TROUGH_INSET + (GuiLayout.TROUGH_INTERIOR - fill),
                    GuiLayout.BAR_WIDTH,
                    fill,
                    256,
                    256);
        }
    }

    /**
     * 某一列的填充比例：0 到 5 列各自经 {@link MenuArcaneAssembler#getBarVis} 读自己的要素；
     * 灵气 vis 均分，六列一起动没问题。
     */
    private float columnRatio(int index) {
        if (index >= GuiLayout.PRIMAL_COLUMNS) {
            // 只画合成进度：「有 vis 缓冲就画满」会让闲置的组装机画出满条，
            // 被取消的任务看起来还在收尾。
            float progress = menu.isCrafting() ? menu.getProgress() : 0.0F;
            if (TRACE_PROGRESS && Math.abs(progress - lastTracedProgress) > 0.001F) {
                lastTracedProgress = progress;
                ThELog.LOG.info("[asmtest] bar={} {}", progress, menu.progressTrace());
            }
            return progress;
        }
        return Math.min(1.0F, menu.getBarVis(index) / (float) VIS_BAR_MAX_PER_ASPECT);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // 只画参考实现画的那两个标签。
        super.extractLabels(graphics, mouseX, mouseY);
    }

    /**
     * 悬停卡片槽位时显示 AE2 的「可用升级」列表，取代原版物品 tooltip：
     * 表头和条目都是 AE2 自己的，它的面板控件挂不到普通界面上。
     */
    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<Component> upgrades = hoveredUpgradeLines();
        if (upgrades == null) {
            super.extractTooltip(graphics, mouseX, mouseY);
            return;
        }
        graphics.setTooltipForNextFrame(this.font, upgrades, Optional.empty(), mouseX, mouseY);
    }

    /** 被悬停槽位的那几行，它它槽位和没有悬停时为 null。 */
    private @Nullable List<Component> hoveredUpgradeLines() {
        Slot hovered = getSlotUnderMouse();
        if (hovered == null) {
            return null;
        }
        for (int i = 0; i < BlockEntityArcaneAssembler.UPGRADE_SLOT_COUNT; i++) {
            if (hovered != menu.getUpgradeSlot(i)) {
                continue;
            }
            List<Component> lines = new ArrayList<>();
            // 白标题、灰条目，AE2 自己的 tooltip 渲染器就是这两种样式。
            lines.add(GuiText.CompatibleUpgrades.text().copy().withStyle(ChatFormatting.WHITE));
            lines.addAll(Upgrades.getTooltipLinesForMachine(ModItems.ARCANE_ASSEMBLER.get()));
            return lines;
        }
        return null;
    }
}
