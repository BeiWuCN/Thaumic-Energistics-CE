package thaumicenergistics_ce.client.gui;

import com.leclowndu93150.thaumaturge.api.aspect.AspectComponents;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.client.AspectRendering;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.menu.MenuDistillationEncoder;
import thaumicenergistics_ce.menu.slot.AspectSelectSlot;
import thaumicenergistics_ce.network.EncoderActionPayload;

/**
 * The Distillation Encoder's screen: this mod's own art blitted whole, with the slots placed by the menu at
 * the coordinates the art draws them. Drawn directly rather than through AE2's screen-style system, which
 * resolves a style document inside AE2's namespace only, so an addon's texture cannot be named by one.
 */
public class ScreenDistillationEncoder extends AbstractContainerScreen<MenuDistillationEncoder> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "textures/gui/distillation_encoder.png");

    private static final int WIDTH = 176;

    /** 234 rows, not 229: the art's opaque pixels run y=0..233, the last five being the bottom bevel. */
    private static final int HEIGHT = 234;

    private static final int TITLE_X = 8;
    private static final int TITLE_Y = 6;
    private static final int INVENTORY_LABEL_X = 8;
    private static final int INVENTORY_LABEL_Y = HEIGHT - 94;

    /** How large an aspect is drawn in a well. Matches Thaumaturge's own GUI aspect size. */
    private static final int ASPECT_SIZE = 16;

    /** The Encode button's top-left, in panel pixels: the 34x14 band of bare panel between the wells. */
    private static final int BUTTON_X = 140;
    private static final int BUTTON_Y = 94;

    private EncodeButton encodeButton;

    public ScreenDistillationEncoder(
            MenuDistillationEncoder menu, Inventory inventory, Component title) {
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The row comes from the source item, and a client menu is never told when it arrives.
        menu.ensureAspects();
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderAspectTooltip(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, WIDTH, HEIGHT);

        // An item whose aspects are all undiscovered has an empty row and nothing explains it, so the screen
        // looks broken. The row is emptied rather than drawn faintly, so nothing covers this notice.
        if (menu.sourceRevealsNothing()) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable("thaumicenergistics_ce.gui.distillation_encoder.not_scanned"),
                    leftPos + 73,
                    topPos + 78,
                    0xFFAAAAAA);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);

        List<Holder<IAspect>> aspects = menu.aspects();
        for (Slot slot : menu.slots) {
            if (slot instanceof AspectSelectSlot aspectSlot && aspectSlot.aspectIndex() < 0) {
                // The picked well: its slot holds nothing, because what goes here is a choice, not a stack.
                Holder<IAspect> picked = menu.pickedAspect();
                if (picked != null) {
                    // No amount on purpose: this well is the choice itself, the source well shows the number.
                    AspectRendering.renderGui(graphics, font, slot.x, slot.y, picked, 0.0F);
                }
                continue;
            }
            if (slot instanceof AspectSelectSlot aspectSlot && aspectSlot.isFilled()) {
                int index = aspectSlot.aspectIndex();
                // Undiscovered aspects keep their place in the row but are not drawn - an icon would claim
                // knowledge the player does not have.
                if (index < 0 || index >= aspects.size() || !menu.isAspectRevealed(index)) {
                    continue;
                }
                // Slot coordinates, not screen: renderLabels already runs inside the panel-offset pose,
                // and adding leftPos again put every icon at twice its distance.
                int x = slot.x;
                int y = slot.y;
                // The amount goes to the renderer, which draws it in the well's corner: the pattern's output.
                AspectRendering.renderGui(graphics, font, x, y, aspects.get(index), menu.aspectAmountFor(index));

                if (aspectSlot.isSelected()) {
                    // A frame around the picked well, inset by one to sit on the border; drawn, so no art.
                    graphics.renderOutline(x - 1, y - 1, ASPECT_SIZE + 2, ASPECT_SIZE + 2, 0xFFFFD700);
                }
            }
        }
    }

    private void renderAspectTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hoveredSlot instanceof AspectSelectSlot aspectSlot) {
            int index = aspectSlot.aspectIndex();
            List<Holder<IAspect>> aspects = menu.aspects();
            if (index >= 0 && index < aspects.size() && menu.isAspectRevealed(index)) {
                var name = AspectComponents.trueName(aspects.get(index));
                graphics.renderTooltip(font, name, mouseX, mouseY);
            }
            return;
        }
        // The two pattern slots are ordinary item slots and get their tooltips from vanilla.
    }

}
