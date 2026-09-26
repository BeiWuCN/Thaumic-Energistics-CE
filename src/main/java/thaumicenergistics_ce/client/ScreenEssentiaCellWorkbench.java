package thaumicenergistics_ce.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.menu.MenuEssentiaCellWorkbench;

/**
 * The Essentia Cell Workbench's screen.
 *
 * <p>Over this mod's own art, blitted whole, with the cell and the 63 partition wells placed by the menu
 * at the coordinates the art draws them - the same arrangement AE2's own cell workbench uses.
 *
 * <p>Drawn directly rather than through AE2's screen-style system, as the Knowledge Inscriber is. That
 * system resolves a style document inside AE2's namespace only, so an addon's own art cannot be named by
 * one; a screen that owns its texture draws it itself.
 */
public class ScreenEssentiaCellWorkbench extends AbstractContainerScreen<MenuEssentiaCellWorkbench> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "textures/gui/essentia_cell_workbench.png");

    /** The panel the art draws. The texture is 256 square; the window shows the top-left of it. */
    private static final int WIDTH = 176;
    private static final int HEIGHT = 253;

    private static final int TITLE_X = 8;
    private static final int TITLE_Y = 6;
    private static final int INVENTORY_LABEL_X = 8;
    private static final int INVENTORY_LABEL_Y = 157;

    public ScreenEssentiaCellWorkbench(
            MenuEssentiaCellWorkbench menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
        this.titleLabelX = TITLE_X;
        this.titleLabelY = TITLE_Y;
        this.inventoryLabelX = INVENTORY_LABEL_X;
        this.inventoryLabelY = INVENTORY_LABEL_Y;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, WIDTH, HEIGHT);
    }
}
