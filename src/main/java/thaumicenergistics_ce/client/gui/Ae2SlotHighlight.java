package thaumicenergistics_ce.client.gui;

import appeng.menu.slot.ResizableSlot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.Slot;

/**
 * AE2 的悬停框：一像素浅青细框加半透明蓝底，画在物品之上。
 * <p>26.1.2 的原版把槽位高亮搬进 {@code AbstractContainerScreen} 的两个私有方法里贴白方块 sprite，
 * 模组既覆写不了也拦不住；AE2 自己的 {@code AEBaseScreen.renderSlotHighlight} 在这一代也没接上钩子
 * （它的 jar 里只有声明，没有任何调用点），所以本模组照它的画法与配色自己画一遍。
 * 颜色就是 {@code AEBaseScreen} 里那两个常量。
 */
public final class Ae2SlotHighlight {
    /** 框色：AE2 的 {@code -2424833}。 */
    private static final int FRAME_COLOUR = 0xFFDAFFFF;
    /** 底色：AE2 的 {@code 1721553919}。 */
    private static final int FILL_COLOUR = 0x669CD3FF;

    private Ae2SlotHighlight() {
    }

    /** 坐标按调用方已经平移过的坐标系算，就是槽位自己的 {@code x}/{@code y}。 */
    public static void render(GuiGraphicsExtractor graphics, Slot slot) {
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
        graphics.horizontalLine(x, x + width, y - 1, FRAME_COLOUR);
        graphics.horizontalLine(x - 1, x + width, y + height, FRAME_COLOUR);
        graphics.verticalLine(x - 1, y - 2, y + height, FRAME_COLOUR);
        graphics.verticalLine(x + width, y - 2, y + height, FRAME_COLOUR);
        graphics.fillGradient(x, y, x + width, y + height, FILL_COLOUR, FILL_COLOUR);
    }
}
