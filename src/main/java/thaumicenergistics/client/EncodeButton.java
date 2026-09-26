package thaumicenergistics.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The Distillation Encoder's Encode button, drawn from the reference build's own two-state sprite.
 *
 * <p>The same 32x32 sheet the Knowledge Inscriber's button uses, with its two 32x13 frames stacked
 * vertically - the idle one at v=0 and the hovered one at v=15.
 *
 * <p><b>The veil, and why this button has one where the inscriber's does not.</b> The sheet has no disabled
 * frame, and the inscriber leaves it that way on purpose: its label spells out what is wrong ("No Core",
 * "Invalid", "Full"), so a dimmed sprite would only repeat it. This label is just "Encode", and what is
 * missing is written nowhere on the panel - so without a veil, a button that cannot be pressed is
 * indistinguishable from one that can, and clicking it reads as the machine ignoring the player.
 *
 * <p><b>Why this is 34x14 rather than the sprite's own 32x13.</b> The reference stretches the frame to this
 * size at this position, and the panel is drawn for it: the art leaves a 34-wide, 14-tall band of bare
 * panel between the blank pattern well (which ends at y=91) and the written pattern well (which starts at
 * y=110). Drawing the sprite at its native size would sit it a pixel inside that band on every side and
 * read as misplaced against the wells above and below.
 *
 * <p>The label is centred and drawn three pixels from the top. Vanilla's own centring would sit it too low
 * for a 14-pixel-tall widget, the same correction the inscriber's button makes for its 13-pixel one.
 */
public class EncodeButton extends Button {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    thaumicenergistics.ThEIds.MODID, "textures/gui/button.png");

    /** Sprite sheet size, needed so the source rectangle is read in the texture's own pixel space. */
    private static final int SHEET = 32;
    private static final int WIDTH = 34;
    private static final int HEIGHT = 14;
    /** V of the hovered frame. The idle frame starts at 0. */
    private static final int HOVER_V = 15;
    /** One frame's size inside the sheet: the source rectangle, before the stretch. */
    private static final int FRAME_W = 32;
    private static final int FRAME_H = 13;

    public EncodeButton(int x, int y, Component label, OnPress onPress) {
        super(x, y, WIDTH, HEIGHT, label, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int v = isHovered() && active ? HOVER_V : 0;
        // The frame is stretched to the widget: see the class note on why this one is not native size.
        graphics.blit(TEXTURE, getX(), getY(), WIDTH, HEIGHT, 0.0F, (float) v, FRAME_W, FRAME_H, SHEET, SHEET);
        if (!active) {
            // A veil rather than a tinted blit: the sprite has to keep its own shading to still read as
            // the same button, and only the whole thing needs to look switched off.
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
