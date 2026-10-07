package thaumicenergistics_ce.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 知识铭刻机的按钮，绘制自参考构建自己的两态贴图。
 * 一张 32x32 的图集放两个 32x13 的帧，空闲在 v=0、悬停在 v=15，没有禁用帧：
 * 标签说明哪里不对（“No Core”、“Invalid”、“Full”），所以只要没悬停就画空闲态。
 * 标签向下居中两像素，与参考构建的做法一致：原版居中位置太低。
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
