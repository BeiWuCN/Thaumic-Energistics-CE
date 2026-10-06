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
 * The Arcane Assembler screen: pieces from {@link GuiLayout} composited over the reference's own art.
 * That art is not one rectangle - its first 102 rows are 197 wide and the rest 176 - so a window-sized
 * blit would paint the transparent wedge and drag the bar sprites parked below the panel into view.
 */
public class ScreenArcaneAssembler extends AbstractContainerScreen<MenuArcaneAssembler> {

    /** The GUI art. Full path including the extension: the texture loader appends nothing. */
    private static final ResourceLocation FALLBACK_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "textures/gui/arcane_assembler.png");

    /** Read from the block entity: as a second literal it disagreed with the machine it draws. */
    private static final int VIS_BAR_MAX = BlockEntityArcaneAssembler.visBufferTarget();

    /** A sixth of the shared {@link #VIS_BAR_MAX} pool: scaling against the pool would empty the bars. */
    private static final int VIS_BAR_MAX_PER_ASPECT =
            Math.max(1, VIS_BAR_MAX / GuiLayout.PRIMAL_COLUMNS);

    /** Fallback window size, matching the reference's constants. */
    private static final int FB_WIDTH = 175;
    private static final int FB_HEIGHT = 231;

    /** V of the first row of the fill strip that runs along the bottom of the texture. */
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
        // Stored patterns come from the knowledge core and the wells are read-only, so fill them first.
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
        // No drawPreview: the preview wells are slots now and vanilla paints their contents, so painting here
        // would draw every preview item twice, one pixel apart.
        drawVisColumns(graphics);
    }

    private void drawUpgradeIcons(GuiGraphics graphics) {
        GuiLayout.Grid grid = layout.upgradeSlots();
        // Clamped to the slots that exist: the layout is a resource, and a grid taller than the machine
        // would throw out of the render loop every frame the screen is open.
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
     * Fill fraction for a column: columns 0-5 each read their own aspect through
     * {@link MenuArcaneAssembler#getBarVis}; aura vis is spread evenly, so all six moving together is fine.
     */
    private float columnRatio(int index) {
        if (index >= GuiLayout.PRIMAL_COLUMNS) {
            // Craft progress only: "full whenever any vis is buffered" would draw a complete bar on an
            // idle assembler and make a cancelled job look like it was still finishing.
            return menu.isCrafting() ? menu.getProgress() : 0.0F;
        }
        return Math.min(1.0F, menu.getBarVis(index) / (float) VIS_BAR_MAX_PER_ASPECT);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Only the two labels the reference draws.
        super.renderLabels(graphics, mouseX, mouseY);
    }

    /**
     * AE2's "available upgrades" list while a card slot is hovered, replacing the vanilla item tooltip:
     * the header and the entries are AE2's own, and its panel widget cannot hang on a plain screen.
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

    /** Those lines for the hovered slot, null for any other slot and for no hover at all. */
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
            // White header and grey entries: the two styles AE2's own tooltip renderer applies.
            lines.add(GuiText.CompatibleUpgrades.text().copy().withStyle(ChatFormatting.WHITE));
            lines.addAll(Upgrades.getTooltipLinesForMachine(ModItems.ARCANE_ASSEMBLER.get()));
            return lines;
        }
        return null;
    }
}
