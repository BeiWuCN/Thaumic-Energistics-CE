package thaumicenergistics_ce.client.gui;

import appeng.menu.slot.ResizableSlot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.Slot;

public final class Ae2SlotHighlight {
    private static final int FRAME_COLOUR = 0xFFDAFFFF;
    private static final int FILL_COLOUR = 0x669CD3FF;

    private Ae2SlotHighlight() {
    }

    public static void render(GuiGraphicsExtractor graphics, Slot slot) {
        if (!slot.isHighlightable()) {
            return;
        }
        int width = 16;
        int height = 16;
        if (slot instanceof ResizableSlot resizable) {
            width = resizable.getWidth();
            height = resizable.getHeight();
        }
        int x = slot.x;
        int y = slot.y;
        graphics.horizontalLine(x, x + width, y - 1, FRAME_COLOUR);
        graphics.horizontalLine(x - 1, x + width, y + height, FRAME_COLOUR);
        graphics.verticalLine(x - 1, y - 2, y + height, FRAME_COLOUR);
        graphics.verticalLine(x + width, y - 2, y + height, FRAME_COLOUR);
        graphics.fillGradient(x, y, x + width, y + height, FILL_COLOUR, FILL_COLOUR);
    }
}
