package thaumicenergistics_ce.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.menu.MenuKnowledgeInscriber;

public class ScreenKnowledgeInscriber extends AbstractContainerScreen<MenuKnowledgeInscriber> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(ThEIds.MODID, "textures/gui/knowledge_inscriber.png");

    private static final int WIDTH = 175;
    private static final int HEIGHT = 244;

    private static final int PANEL_WIDTH = 208;

    private static final int TITLE_X = 6;
    private static final int TITLE_Y = 5;
    private static final int INVENTORY_LABEL_X = 8;
    private static final int INVENTORY_LABEL_Y = 150;

    private static final int BUTTON_X = 109;
    private static final int BUTTON_Y = 90;


    private InscriberButton saveButton;

    public ScreenKnowledgeInscriber(MenuKnowledgeInscriber menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
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
                        minecraft.gameMode.handleInventoryButtonClick(
                                menu.containerId, menu.isDelete() ? 1 : 0);
                    }
                }));
        updateSaveButton();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        menu.updatePreview();
        menu.refreshMirrors();
        updateSaveButton();
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
        graphics.blit(
                RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0.0F, 0.0F, PANEL_WIDTH, HEIGHT, 256, 256);
    }


    private void updateSaveButton() {
        if (saveButton == null) {
            return;
        }
        Component next = buttonLabel();
        saveButton.setMessage(next);
        saveButton.active = menu.isActionable();
    }

    private Component buttonLabel() {
        if (!menu.hasCore()) {
            return label("no_core");
        }
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
