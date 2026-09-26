package thaumicenergistics_ce.client;

import appeng.client.gui.Icon;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.ThaumicEnergistics;
import thaumicenergistics_ce.blockentity.BlockEntityArcaneAssembler;
import thaumicenergistics_ce.gui.GuiLayout;
import thaumicenergistics_ce.menu.MenuArcaneAssembler;

/**
 * Screen for the Arcane Assembler: the reference screen's composition over the reference's own art.
 *
 * <p>The art is not one rectangle - its first 102 rows are 197 wide, everything below is 176 - so a
 * window-sized blit would paint the transparent wedge and drag the bar sprites parked below the panel into
 * view. The pieces are described in {@link GuiLayout} and composited here.
 */
public class ScreenArcaneAssembler extends AbstractContainerScreen<MenuArcaneAssembler> {

    /** The GUI art. Full path including the extension: the texture loader appends nothing. */
    private static final ResourceLocation FALLBACK_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "textures/gui/arcane_assembler.png");

    /**
     * Read from the block entity, not written here: as a second literal it disagreed with the machine it draws.
     */
    private static final int VIS_BAR_MAX = BlockEntityArcaneAssembler.visBufferTarget();

    /**
     * A sixth of the pool, not the whole of it: the machine keeps one pool of {@link #VIS_BAR_MAX} shared by
     * six bars, so scaling against the pool would draw a full machine as six nearly empty columns.
     */
    private static final int VIS_BAR_MAX_PER_ASPECT =
            Math.max(1, VIS_BAR_MAX / GuiLayout.PRIMAL_COLUMNS);

    /** Fallback window size, matching the reference's constants. */
    private static final int FB_WIDTH = 175;
    private static final int FB_HEIGHT = 231;

    /** V of the first row of the fill strip that runs along the bottom of the texture. */
    private static final int FILL_TOP = 240;

    /** On only in the assembler's self-test; the column cannot be checked from the server. */
    private static final boolean TRACE_PROGRESS =
            "true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_ASSEMBLER_SELFTEST"));

    /** Last traced value, so a fifty-tick craft is fifty lines and not three thousand. */
    private float lastTracedProgress = -1.0F;

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

    /**
     * Draws AE2's "empty upgrade" icon into each empty upgrade slot, so the column reads as slots rather than
     * as four anonymous recesses. The icon comes from AE2's sprite sheet, not this panel's art.
     */
    private void drawUpgradeIcons(GuiGraphics graphics) {
        GuiLayout.Grid grid = layout.upgradeSlots();
        // Clamped to the slots that exist: the layout is a resource, and a grid taller than the machine
        // would throw out of the render loop every frame the screen is open.
        int rows = Math.min(grid.rows(),
                thaumicenergistics_ce.blockentity.BlockEntityArcaneAssembler.UPGRADE_SLOT_COUNT);
        for (int i = 0; i < rows; i++) {
            if (menu.getUpgradeSlot(i).hasItem()) {
                continue;
            }
            Icon.BACKGROUND_UPGRADE.getBlitter()
                    .dest(leftPos + grid.columnX(), topPos + grid.columnY(i))
                    .blit(graphics);
        }
    }

    /**
     * Fills the vis columns built into the art. The troughs are part of the panel, so only the fill is drawn,
     * cropped from the strip at the bottom of the texture and grown upwards. Each column reads its own source
     * column, which is what gives the primal aspects their colours.
     */
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
     * Fill fraction for a column. Columns 0-5 each read their own aspect through
     * {@link MenuArcaneAssembler#getBarVis}; reading the one buffered pool drew all six at the same height.
     *
     * <p>Aura vis has no aspect of its own and is spread evenly, so the six moving together on an aura-fed
     * machine is not a fault.
     */
    private float columnRatio(int index) {
        if (index >= GuiLayout.PRIMAL_COLUMNS) {
            // Craft progress, and nothing else: falling back to "full whenever any vis is buffered" drew a
            // complete bar for an idle assembler and made a cancelled job look like it was still finishing.
            float progress = menu.isCrafting() ? menu.getProgress() : 0.0F;
            if (TRACE_PROGRESS && Math.abs(progress - lastTracedProgress) > 0.001F) {
                lastTracedProgress = progress;
                ThaumicEnergistics.LOG.info("[asmtest] bar={} {}", progress, menu.progressForTest());
            }
            return progress;
        }
        return Math.min(1.0F, menu.getBarVis(index) / (float) VIS_BAR_MAX_PER_ASPECT);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Only the two labels the reference draws.
        super.renderLabels(graphics, mouseX, mouseY);
    }
}
