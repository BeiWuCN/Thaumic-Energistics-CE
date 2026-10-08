package thaumicenergistics_ce.client.gui;

import appeng.api.upgrades.Upgrades;
import appeng.client.gui.Icon;
import appeng.core.localization.GuiText;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;
import thaumicenergistics_ce.layout.GuiLayout;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.menu.MenuArcaneAssembler;

/**
 * 奥术组装机界面：把 {@link GuiLayout} 的各块贴到参考实现自己的美术上。
 * 那份美术不是一个矩形，前 102 行宽 197，其余宽 176；按窗口大小整块 blit 会把
 * 透明楔形和停在面板下方的条形贴图一起画出来。
 */
public class ScreenArcaneAssembler extends AbstractContainerScreen<MenuArcaneAssembler> {

    /** 界面美术的完整路径，含扩展名：纹理加载器不补后缀。 */
    private static final ResourceLocation FALLBACK_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "textures/gui/arcane_assembler.png");

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

    private final @Nullable GuiLayout layout = GuiLayout.load();

    private ResourceLocation texture() {
        return layout == null ? FALLBACK_TEXTURE : layout.texture();
    }

    public ScreenArcaneAssembler(MenuArcaneAssembler menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        if (layout != null) {
            this.imageWidth = layout.imageWidth();
            this.imageHeight = layout.imageHeight();
            this.titleLabelX = layout.title().x();
            this.titleLabelY = layout.title().y();
            this.inventoryLabelX = layout.inventoryLabel().x();
            this.inventoryLabelY = layout.inventoryLabel().y();
        } else {
            this.imageWidth = FB_WIDTH;
            this.imageHeight = FB_HEIGHT;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        // 存储的样板从知识核心来，槽井只读，先填它们。
        menu.refreshPatternView();
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    /**
     * 悬停框换成 AE2 的配色，见 {@link Ae2SlotHighlight}。
     * 不调 super：基类那版先贴一层半透明白方块，框底会发白。
     */
    @Override
    protected void renderSlotHighlight(GuiGraphics graphics, Slot slot, int mouseX, int mouseY, float partialTick) {
        Ae2SlotHighlight.render(graphics, slot);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        if (layout == null) {
            return;
        }

        for (GuiLayout.PanelPiece piece : layout.panels()) {
            GuiLayout.Region source = piece.source();
            GuiLayout.Anchor destination = piece.destination();
            graphics.blit(
                    texture(),
                    leftPos + destination.x(),
                    topPos + destination.y(),
                    source.u(),
                    source.v(),
                    source.w(),
                    source.h());
        }

        drawUpgradeIcons(graphics);
        // 不调 [drawPreview]：预览井现在是槽位，原版会画内容，这里再画每个预览物品就重叠一次，差一个像素。
        drawVisColumns(graphics);
    }

    private void drawUpgradeIcons(GuiGraphics graphics) {
        GuiLayout.Grid grid = layout.upgradeSlots();
        // 钳到真实存在的槽位：布局是资源文件，网格比机器高会让界面开着的每一帧从渲染循环抛出去。
        int rows = Math.min(grid.rows(),
                thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler.UPGRADE_SLOT_COUNT);
        for (int i = 0; i < rows; i++) {
            if (menu.getUpgradeSlot(i).hasItem()) {
                continue;
            }
            Icon.BACKGROUND_UPGRADE.getBlitter()
                    .dest(leftPos + grid.columnX(), topPos + grid.columnY(i))
                    .blit(graphics);
        }
    }

    private void drawVisColumns(GuiGraphics graphics) {
        GuiLayout.VisBars bars = layout.visBars();
        for (int i = 0; i < bars.count(); i++) {
            int fill = GuiLayout.VisBars.fillHeight(columnRatio(i));
            if (fill <= 0) {
                continue;
            }
            GuiLayout.VisBars.Column column = bars.column(i);
            graphics.blit(
                    texture(),
                    leftPos + column.x(),
                    topPos + column.y() + GuiLayout.TROUGH_INSET + (GuiLayout.TROUGH_INTERIOR - fill),
                    column.sourceU(),
                    FILL_TOP + GuiLayout.TROUGH_INSET + (GuiLayout.TROUGH_INTERIOR - fill),
                    GuiLayout.BAR_WIDTH,
                    fill);
        }
    }

    /**
     * 某一列的填充比例：0 到 5 列各自经 {@link MenuArcaneAssembler#getBarVis} 读自己的要素；
     * 灵气 vis 均分，六列一起动没问题。
     */
    private float columnRatio(int index) {
        if (index >= GuiLayout.PRIMAL_COLUMNS) {
            // 只算合成进度：「缓冲到一点 vis 就算满」，空闲组装机会画出满格，被取消的合成看着像还在收尾。
            return menu.isCrafting() ? menu.getProgress() : 0.0F;
        }
        return Math.min(1.0F, menu.getBarVis(index) / (float) VIS_BAR_MAX_PER_ASPECT);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 只画参考实现画的那两个标签。
        super.renderLabels(graphics, mouseX, mouseY);
    }

    /**
     * 悬停卡片槽时画 AE2 的「可用升级」列表，顶掉原版物品 tooltip：标题和条目都是 AE2 的，
     * 它的面板控件挂不到普通界面上。
     */
    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        List<Component> upgrades = hoveredUpgradeLines();
        if (upgrades == null) {
            super.renderTooltip(graphics, mouseX, mouseY);
            return;
        }
        graphics.renderTooltip(this.font, upgrades, Optional.empty(), mouseX, mouseY);
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
