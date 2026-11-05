package com.beiwu.thaumicenergistics_ce.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import com.beiwu.thaumicenergistics_ce.ThEIds;
import com.beiwu.thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;
import com.beiwu.thaumicenergistics_ce.menu.MenuKnowledgeInscriber;
import com.beiwu.thaumicenergistics_ce.network.InscriberCostPayload;
import com.beiwu.thaumicenergistics_ce.recipe.arcane.EssentiaCrystals;
import com.leclowndu93150.thaumaturge.api.aspect.Aspects;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import net.minecraft.resources.ResourceKey;

public class ScreenKnowledgeInscriber extends AbstractContainerScreen<MenuKnowledgeInscriber> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(ThEIds.MODID, "textures/gui/knowledge_inscriber.png");

    private static final int WIDTH = 175;
    private static final int HEIGHT = 244;

    private static final int PANEL_WIDTH = 176;

    private static final int TITLE_X = 6;
    private static final int TITLE_Y = 5;
    private static final int INVENTORY_LABEL_X = 8;
    private static final int INVENTORY_LABEL_Y = 150;

    private static final int BUTTON_X = 137;
    private static final int BUTTON_Y = 101;

    private static final int CLEAR_X = 71;
    private static final int CLEAR_Y = 89;


    private InscriberButton saveButton;

    private InscriberClearButton clearButton;

    private InscriberCostPayload recipeCost;

    public void acceptCost(InscriberCostPayload payload) {
        if (payload.containerId() == menu.containerId) {
            recipeCost = payload;
        }
    }

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

        clearButton = addRenderableWidget(new InscriberClearButton(
                leftPos + CLEAR_X,
                topPos + CLEAR_Y,
                label("clear"),
                button -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 2);
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
        graphics.pose().pushMatrix();
        graphics.pose().translate(leftPos, topPos);
        drawRecipeCost(graphics);
        graphics.pose().popMatrix();
        Slot hovered = this.hoveredSlot;
        if (hovered == null) {
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(leftPos, topPos);
        Ae2SlotHighlight.render(graphics, hovered);
        graphics.pose().popMatrix();
    }

    private void drawRecipeCost(GuiGraphicsExtractor graphics) {
        if (recipeCost == null || !recipeCost.hasRecipe() || minecraft == null || minecraft.level == null) {
            return;
        }
        String vis = recipeCost.vis() + " Vis";
        float scale = Math.min(0.75F, 24.0F / Math.max(1, font.width(vis)));
        graphics.pose().pushMatrix();
        graphics.pose().translate(113, 94);
        graphics.pose().scale(scale, scale);
        graphics.text(font, vis, -font.width(vis) / 2, 0, 0xFF60477F, false);
        graphics.pose().popMatrix();

        int count = recipeCost.crystals().size();
        if (count == 0) return;
        float chip = Math.min(10.0F, 50.0F / count);
        float x = 124.0F - chip;
        for (var crystal : recipeCost.crystals()) {
            var aspect = Aspects.resolve(minecraft.level,
                    ResourceKey.create(IAspect.REGISTRY_KEY, crystal.aspect()));
            if (aspect != null) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(x, 132);
                graphics.pose().scale(chip / 16.0F, chip / 16.0F);
                graphics.item(EssentiaCrystals.create(aspect, 1), 0, 0);
                String amount = Integer.toString(crystal.count());
                graphics.text(font, amount, 17 - font.width(amount), 10, 0xFFFFFFFF, true);
                graphics.pose().popMatrix();
            }
            x -= chip;
        }
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
        if (clearButton != null) {
            clearButton.active = !menu.isGridEmpty();
        }
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
