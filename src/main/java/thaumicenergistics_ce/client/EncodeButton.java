package thaumicenergistics_ce.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The Distillation Encoder's Encode button, from the reference build's own two-state sprite.
 * <ul>
 *   <li>Disabled is veiled: the sheet has no disabled frame and "Encode" says nothing on its own.
 *   <li>Drawn 34x14, not the native 32x13, because the reference stretches it to the panel band.
 *   <li>The label is centred, 3 pixels from the top, as the inscriber's label is.
 * </ul>
 */
public class EncodeButton extends Button {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    thaumicenergistics_ce.ThEIds.MODID, "textures/gui/button.png");

    /** Sheet size, widget size, and one frame inside the sheet: the source rectangle, before the stretch. */
    private static final int SHEET = 32;
    private static final int WIDTH = 34;
    private static final int HEIGHT = 14;
    private static final int HOVER_V = 15;
    private static final int FRAME_W = 32;
    private static final int FRAME_H = 13;

    public EncodeButton(int x, int y, Component label, OnPress onPress) {
        super(x, y, WIDTH, HEIGHT, label, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int v = isHovered() && active ? HOVER_V : 0;
        // The frame is stretched to the widget: the class note says why this one is not at native size.
        graphics.blit(TEXTURE, getX(), getY(), WIDTH, HEIGHT, 0.0F, (float) v, FRAME_W, FRAME_H, SHEET, SHEET);
        if (!active) {
            // A veil, not a tinted blit: the sprite must keep its own shading, and only the whole thing
            // needs to read as switched off.
            graphics.fill(getX(), getY(), getX() + WIDTH, getY() + HEIGHT, 0x8A000000);
        }
        renderLabel(graphics);
    }

    private void renderLabel(GuiGraphics graphics) {
        String text = getMessage().getString();
        if (text.isEmpty()) {
            return;
        }
        var font = Minecraft.getInstance().font;
        graphics.drawString(
                font,
                text,
                getX() + (WIDTH - font.width(text)) / 2,
                getY() + 3,
                0x000000,
                false);
    }
}
