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
 * 奥术组装机界面：把 {@link GuiLayout} 的各块合成到参考构建自己的美术上。
 * 那份美术不是一个矩形——前 102 行宽 197，其余宽 176——所以按窗口大小
 * blit 会画出透明的楔形，并把停在面板下方的条形贴图拖进视野。
 */
public class ScreenArcaneAssembler extends AbstractContainerScreen<MenuArcaneAssembler> {

    /** 界面美术。含扩展名的完整路径：纹理加载器不会追加任何东西。 */
    private static final ResourceLocation FALLBACK_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "textures/gui/arcane_assembler.png");

    /** 从方块实体读出：作为第二个字面量时，它与它所绘制的机器不一致。 */
    private static final int VIS_BAR_MAX = BlockEntityArcaneAssembler.visBufferTarget();

    /** 共享 {@link #VIS_BAR_MAX} 池的六分之一：按整个池缩放会把条形清空。 */
    private static final int VIS_BAR_MAX_PER_ASPECT =
            Math.max(1, VIS_BAR_MAX / GuiLayout.PRIMAL_COLUMNS);

    /** 回退窗口尺寸，与参考构建的常量一致。 */
    private static final int FB_WIDTH = 175;
    private static final int FB_HEIGHT = 231;

    /** 沿纹理底部延伸的填充条带第一行的 V。 */
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
        // 存储的样板来自知识核心，而槽井是只读的，所以先填充它们。
        menu.refreshPatternView();
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
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
        // 不调用 [drawPreview]：预览槽井现在是槽位，由原版绘制其内容，所以在这里绘制
        // 会把每个预览物品画两次，彼此相差一个像素。
        drawVisColumns(graphics);
    }

    private void drawUpgradeIcons(GuiGraphics graphics) {
        GuiLayout.Grid grid = layout.upgradeSlots();
        // 钳制到实际存在的槽位：布局是一个资源，而比机器更高的网格会在界面打开的
        // 每一帧从渲染循环里抛异常。
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
     * 某一列的填充比例：0 到 5 列各自通过 {@link MenuArcaneAssembler#getBarVis} 读取
     * 自己的要素；灵气 vis 是均分的，所以六列一起动没有问题。
     */
    private float columnRatio(int index) {
        if (index >= GuiLayout.PRIMAL_COLUMNS) {
            // 只用合成进度：若“只要缓冲了任何 vis 就算满”，空闲的组装机上会画出满格条形，
            // 并让被取消的任务看起来还在收尾。
            return menu.isCrafting() ? menu.getProgress() : 0.0F;
        }
        return Math.min(1.0F, menu.getBarVis(index) / (float) VIS_BAR_MAX_PER_ASPECT);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 只画参考构建绘制的那两个标签。
        super.renderLabels(graphics, mouseX, mouseY);
    }

    /**
     * 悬停卡片槽位时显示 AE2 的“可用升级”列表，取代原版的物品 tooltip：
     * 标题和条目都是 AE2 自己的，而它的面板控件无法挂在普通界面上。
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

    /** 被悬停槽位对应的那些行；其它槽位和完全没有悬停时为 null。 */
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
            // 白色标题和灰色条目：AE2 自己的 tooltip 渲染器所用的两种样式。
            lines.add(GuiText.CompatibleUpgrades.text().copy().withStyle(ChatFormatting.WHITE));
            lines.addAll(Upgrades.getTooltipLinesForMachine(ModItems.ARCANE_ASSEMBLER.get()));
            return lines;
        }
        return null;
    }
}
