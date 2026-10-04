package thaumicenergistics_ce.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The Knowledge Inscriber's button, drawn from the reference build's own two-state sprite.
 * <ul>
 * <li>32x32 sheet, two 32x13 frames: idle at v=0, hovered at v=15. No disabled frame - the label
 * says what is wrong (No Core, Invalid, Full), so idle is drawn whenever not hovered.
 * <li>Label is centred two pixels down, as the reference does; vanilla centring sits too low.
 * </ul>
 */
public class InscriberButton extends Button {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    thaumicenergistics_ce.ThEIds.MODID, "textures/gui/button.png");

    private static final int SHEET = 32;
    private static final int WIDTH = 32;
    private static final int HEIGHT = 13;
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
