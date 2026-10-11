package thaumicenergistics_ce.client.gui;

import com.leclowndu93150.thaumaturge.api.aspect.AspectComponents;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.client.AspectRendering;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.menu.MenuDistillationEncoder;
import thaumicenergistics_ce.menu.slot.AspectSelectSlot;
import thaumicenergistics_ce.network.EncoderActionPayload;

public class ScreenDistillationEncoder extends AbstractContainerScreen<MenuDistillationEncoder> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(ThEIds.MODID, "textures/gui/distillation_encoder.png");

    private static final int WIDTH = 176;

    private static final int HEIGHT = 234;

    private static final int TITLE_X = 8;
    private static final int TITLE_Y = 6;
    private static final int INVENTORY_LABEL_X = 8;
    private static final int INVENTORY_LABEL_Y = HEIGHT - 94;

    private static final int ASPECT_SIZE = 16;

    private static final int BUTTON_X = 140;
    private static final int BUTTON_Y = 94;

    private EncodeButton encodeButton;

    public ScreenDistillationEncoder(
            MenuDistillationEncoder menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        this.titleLabelX = TITLE_X;
        this.titleLabelY = TITLE_Y;
        this.inventoryLabelX = INVENTORY_LABEL_X;
        this.inventoryLabelY = INVENTORY_LABEL_Y;
    }

    @Override
    protected void init() {
        super.init();
        encodeButton = addRenderableWidget(new EncodeButton(
                leftPos + BUTTON_X,
                topPos + BUTTON_Y,
                Component.translatable("thaumicenergistics_ce.gui.encode"),
                button -> menu.sendAction(EncoderActionPayload.ACTION_ENCODE, 0)));
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (encodeButton != null) {
            encodeButton.active = menu.canEncode();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        menu.ensureAspects();
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        renderAspectTooltip(graphics, mouseX, mouseY);
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
                RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0.0F, 0.0F, WIDTH, HEIGHT, 256, 256);

        if (menu.sourceRevealsNothing()) {
            graphics.centeredText(
                    font,
                    Component.translatable("thaumicenergistics_ce.gui.distillation_encoder.not_scanned"),
                    leftPos + 73,
                    topPos + 78,
                    0xFFAAAAAA);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        List<Holder<IAspect>> aspects = menu.aspects();
        for (Slot slot : menu.slots) {
            if (slot instanceof AspectSelectSlot aspectSlot && aspectSlot.aspectIndex() < 0) {
                Holder<IAspect> picked = menu.pickedAspect();
                if (picked != null) {
                    AspectRendering.renderGui(graphics, font, slot.x, slot.y, picked, 0.0F);
                }
                continue;
            }
            if (slot instanceof AspectSelectSlot aspectSlot && aspectSlot.isFilled()) {
                int index = aspectSlot.aspectIndex();
                if (index < 0 || index >= aspects.size() || !menu.isAspectRevealed(index)) {
                    continue;
                }
                int x = slot.x;
                int y = slot.y;
                AspectRendering.renderGui(graphics, font, x, y, aspects.get(index), menu.aspectAmountFor(index));

                if (aspectSlot.isSelected()) {
                    graphics.outline(x - 1, y - 1, ASPECT_SIZE + 2, ASPECT_SIZE + 2, 0xFFFFD700);
                }
            }
        }
    }

    private void renderAspectTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (hoveredSlot instanceof AspectSelectSlot aspectSlot) {
            int index = aspectSlot.aspectIndex();
            List<Holder<IAspect>> aspects = menu.aspects();
            if (index >= 0 && index < aspects.size() && menu.isAspectRevealed(index)) {
                var name = AspectComponents.trueName(aspects.get(index));
                graphics.setTooltipForNextFrame(font, name, mouseX, mouseY);
            }
            return;
        }
    }

}
