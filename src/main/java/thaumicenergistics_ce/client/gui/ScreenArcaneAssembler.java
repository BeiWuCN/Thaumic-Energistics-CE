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
import thaumicenergistics_ce.util.layout.GuiLayout;
import thaumicenergistics_ce.registry.ModItems;
import thaumicenergistics_ce.menu.MenuArcaneAssembler;
import thaumicenergistics_ce.util.ThELog;

public class ScreenArcaneAssembler extends AbstractContainerScreen<MenuArcaneAssembler> {

    private static final Identifier FALLBACK_TEXTURE =
            Identifier.fromNamespaceAndPath(ThEIds.MODID, "textures/gui/arcane_assembler.png");

    private static final int VIS_BAR_MAX = BlockEntityArcaneAssembler.visBufferTarget();

    private static final int VIS_BAR_MAX_PER_ASPECT =
            Math.max(1, VIS_BAR_MAX / GuiLayout.PRIMAL_COLUMNS);

    private static final int FB_WIDTH = 175;
    private static final int FB_HEIGHT = 231;

    private static final int FILL_TOP = 240;

    private static final boolean TRACE_PROGRESS =
            "true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_ASSEMBLER_SELFTEST"));

    private float lastTracedProgress = -1.0F;

    private final @Nullable GuiLayout layout;

    private Identifier texture() {
        return layout == null ? FALLBACK_TEXTURE : layout.texture();
    }

    public ScreenArcaneAssembler(MenuArcaneAssembler menu, Inventory inventory, Component title) {
        this(menu, inventory, title, GuiLayout.load());
    }

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
        menu.refreshPatternView();
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractContents(graphics, mouseX, mouseY, partialTick);
        Slot hovered = this.hoveredSlot;
        if (hovered == null) {
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(leftPos, topPos);
        Ae2SlotHighlight.render(graphics, hovered);
        graphics.pose().popMatrix();
    }

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
        drawVisColumns(graphics);
    }

    private void drawUpgradeIcons(GuiGraphicsExtractor graphics) {
        GuiLayout.Grid grid = layout.upgradeSlots();
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

    private float columnRatio(int index) {
        if (index >= GuiLayout.PRIMAL_COLUMNS) {
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
        super.extractLabels(graphics, mouseX, mouseY);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<Component> upgrades = hoveredUpgradeLines();
        if (upgrades == null) {
            super.extractTooltip(graphics, mouseX, mouseY);
            return;
        }
        graphics.setTooltipForNextFrame(this.font, upgrades, Optional.empty(), mouseX, mouseY);
    }

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
            lines.add(GuiText.CompatibleUpgrades.text().copy().withStyle(ChatFormatting.WHITE));
            lines.addAll(Upgrades.getTooltipLinesForMachine(ModItems.ARCANE_ASSEMBLER.get()));
            return lines;
        }
        return null;
    }
}
