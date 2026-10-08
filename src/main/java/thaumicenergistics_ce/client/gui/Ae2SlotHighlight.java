package thaumicenergistics_ce.client.gui;

import appeng.menu.slot.ResizableSlot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;

/**
 * AE2 的悬停框：一像素浅青细框加半透明蓝底，画在物品之上。
 * 原版基类的 {@code renderSlotHighlight(GuiGraphics, Slot, int, int, float)} 默认贴半透明白方块，
 * AE2 的 {@code AEBaseScreen} 把它整个换成下面这套画法；不继承 AE2 界面的那几个界面
 * 只能自己覆写这个钩子来对齐。颜色就是 AE2 里那两个常量。
 */
public final class Ae2SlotHighlight {
    /** 框色：AE2 的 {@code -2424833}。 */
    private static final int FRAME_COLOUR = 0xFFDAFFFF;
    /** 底色：AE2 的 {@code 1721553919}。 */
    private static final int FILL_COLOUR = 0x669CD3FF;

    private Ae2SlotHighlight() {
    }

    /** 坐标按调用方已经平移过的坐标系算，就是槽位自己的 {@code x}/{@code y}。 */
    public static void render(GuiGraphics graphics, Slot slot) {
        if (!slot.isHighlightable()) {
            return;
        }
        int width = 16;
        int height = 16;
        // AE2 自己的槽位可用样式改尺寸，框跟着槽走。
        if (slot instanceof ResizableSlot resizable) {
            width = resizable.getWidth();
            height = resizable.getHeight();
        }
        int x = slot.x;
        int y = slot.y;
        graphics.hLine(x, x + width, y - 1, FRAME_COLOUR);
        graphics.hLine(x - 1, x + width, y + height, FRAME_COLOUR);
        graphics.vLine(x - 1, y - 2, y + height, FRAME_COLOUR);
        graphics.vLine(x + width, y - 2, y + height, FRAME_COLOUR);
        graphics.fillGradient(x, y, x + width, y + height, FILL_COLOUR, FILL_COLOUR);
    }
}
