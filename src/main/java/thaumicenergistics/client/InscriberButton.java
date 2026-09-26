package thaumicenergistics.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The Knowledge Inscriber's button, drawn from the reference build's own two-state sprite.
 *
 * <p>The sprite sheet is 32x32 and holds two 32x13 frames stacked vertically: the idle one at v=0 and
 * the hovered one at v=15. There is no disabled frame - the reference leaves the label to say what is
 * wrong (No Core, Invalid, Full) and greys nothing, so this draws the idle frame whenever the button is
 * not hovered, enabled or not.
 *
 * <p>The label is centred and drawn two pixels from the top, which is what the reference's own button
 * subclass does; the vanilla centring would sit too low for a 13-pixel-tall widget.
 */
public class InscriberButton extends Button {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    thaumicenergistics.ThEIds.MODID, "textures/gui/button.png");

    /** Sprite sheet size, needed so the source rectangle is read in the texture's own pixel space. */
    private static final int SHEET = 32;
    private static final int WIDTH = 32;
    private static final int HEIGHT = 13;
    /** V of the hovered frame. The idle frame starts at 0. */
    private static final int HOVER_V = 15;

    public InscriberButton(int x, int y, Component label, OnPress onPress) {
        super(x, y, WIDTH, HEIGHT, label, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int v = isHovered() ? HOVER_V : 0;
        graphics.blit(TEXTURE, getX(), getY(), 0.0F, (float) v, WIDTH, HEIGHT, SHEET, SHEET);
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
                getY() + 2,
                0x000000,
                false);
    }
}
