package com.beiwu.thaumicenergistics_ce.client.gui;

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
import com.beiwu.thaumicenergistics_ce.ThEIds;
import com.beiwu.thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;
import com.beiwu.thaumicenergistics_ce.util.layout.GuiLayout;
import com.beiwu.thaumicenergistics_ce.registry.TECEItems;
import com.beiwu.thaumicenergistics_ce.menu.MenuArcaneAssembler;

public class ScreenArcaneAssembler extends AbstractContainerScreen<MenuArcaneAssembler> {

    private static final Identifier FALLBACK_TEXTURE =
            Identifier.fromNamespaceAndPath(ThEIds.MODID, "textures/gui/arcane_assembler.png");

    private static final int VIS_BAR_MAX = BlockEntityArcaneAssembler.visBufferTarget();

    private static final String[] ASPECT_NAMES = {"air", "water", "fire", "order", "entropy", "earth"};

    private static final int FB_WIDTH = 175;
    private static final int FB_HEIGHT = 231;

    private static final int FILL_TOP = 240;

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
                com.beiwu.thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler.UPGRADE_SLOT_COUNT);
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
            if (i < GuiLayout.PRIMAL_COLUMNS && menu.getBarVis(i) > 0) fill = Math.max(1, fill);
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

            return progress;
        }
        return Math.clamp(menu.getBarVis(index) / (float) VIS_BAR_MAX, 0.0F, 1.0F);
    }


    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (layout != null) {
            for (int i = 0; i < GuiLayout.PRIMAL_COLUMNS; i++) {
                var column = layout.visBars().column(i);
                int x = leftPos + column.x(), y = topPos + column.y();
                if (mouseX >= x && mouseX < x + GuiLayout.BAR_WIDTH
                        && mouseY >= y && mouseY < y + GuiLayout.BAR_HEIGHT) {
                    graphics.setTooltipForNextFrame(font, List.of(
                            Component.translatable("thaumicenergistics_ce.gui.vis." + ASPECT_NAMES[i], menu.getBarVis(i))),
                            Optional.empty(), mouseX, mouseY);
                    return;
                }
            }
        }
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
            lines.addAll(Upgrades.getTooltipLinesForMachine(TECEItems.ARCANE_ASSEMBLER.get()));
            return lines;
        }
        return null;
    }
}
