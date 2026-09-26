package thaumicenergistics_ce.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.blockentity.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.menu.MenuKnowledgeInscriber;

/**
 * Screen for the Knowledge Inscriber: one panel blitted whole, with the knowledge core, the stored pattern
 * grid and the crafting preview placed by the menu at the reference build's coordinates. Status is carried
 * by the button's label rather than a line of text, as in the reference build.
 */
public class ScreenKnowledgeInscriber extends AbstractContainerScreen<MenuKnowledgeInscriber> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "textures/gui/knowledge_inscriber.png");

    /** Window size, matching the reference screen. */
    private static final int WIDTH = 175;
    private static final int HEIGHT = 244;

    /** The art is 208 wide; the window only shows the panel part of it. */
    private static final int PANEL_WIDTH = 208;

    private static final int TITLE_X = 6;
    private static final int TITLE_Y = 5;
    private static final int INVENTORY_LABEL_X = 8;
    private static final int INVENTORY_LABEL_Y = 150;

    /** The button's position and size, from the reference screen. */
    private static final int BUTTON_X = 109;
    private static final int BUTTON_Y = 90;

    /** Player inventory slots, which come before the machine's in the menu. */
    private static final int PLAYER_SLOTS = 36;

    private InscriberButton saveButton;

    public ScreenKnowledgeInscriber(MenuKnowledgeInscriber menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
        this.titleLabelX = TITLE_X;
        this.titleLabelY = TITLE_Y;
        this.inventoryLabelX = INVENTORY_LABEL_X;
        this.inventoryLabelY = INVENTORY_LABEL_Y;
    }

    @Override
    protected void init() {
        super.init();
        saveButton = addRenderableWidget(new InscriberButton(
                leftPos + BUTTON_X,
                topPos + BUTTON_Y,
                buttonLabel(),
                button -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        // A menu button click, not a custom packet: the id and the click travel in vanilla's
                        // own packet. Whether this is a delete is asked of the menu, not the selection - after
                        // a save the selection is empty and the grid cleared, so asking the selection sent a
                        // second Save instead of the Delete the button was showing.
                        minecraft.gameMode.handleInventoryButtonClick(
                                menu.containerId, menu.isDelete() ? 1 : 0);
                    }
                }));
        updateSaveButton();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        // The grid is a ghost grid, so both it and the answer are this side's own; the server redoes the lookup.
        menu.updatePreview();
        // The patterns are drawn from the core, and the wells are read-only slots, so this side fills them.
        menu.refreshMirrors();
        updateSaveButton();
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, PANEL_WIDTH, HEIGHT);
    }

    /**
     * Clicking a stored pattern is handled by the menu, not here: acting on the click means writing the grid,
     * and the grid is written through the slot so the payload goes out. See
     * {@code MenuKnowledgeInscriber.clicked}, which is where vanilla sends the click anyway.
     */

    /** Keeps the button's label and enabled state in step with the machine's last action. */
    private void updateSaveButton() {
        if (saveButton == null) {
            return;
        }
        Component next = buttonLabel();
        saveButton.setMessage(next);
        saveButton.active = menu.isActionable();
    }

    /**
     * The label the button should carry: "No Core", "Invalid" when the grid does not stand for a recipe the
     * machine could act on, "Full" when the core has no room, then Save or Delete. "No Core" and "Invalid" are
     * separate on purpose: they once shared a label, so inserting a core still read "No Core".
     */
    private Component buttonLabel() {
        if (!menu.hasCore()) {
            return label("no_core");
        }
        // Save and Delete are the same question asked of the same grid, read from the action, as the click will.
        if (menu.isDelete()) {
            return label("delete");
        }
        String key = switch (menu.buttonState()) {
            case BlockEntityKnowledgeInscriber.STATUS_ACTIONABLE -> "save";
            case BlockEntityKnowledgeInscriber.STATUS_CORE_FULL -> "full";
            case BlockEntityKnowledgeInscriber.STATUS_RESEARCH_LOCKED -> "locked";
            default -> "invalid";
        };
        return label(key);
    }

    private static Component label(String key) {
        return Component.translatable("thaumicenergistics_ce.gui.knowledge_inscriber." + key);
    }
}
