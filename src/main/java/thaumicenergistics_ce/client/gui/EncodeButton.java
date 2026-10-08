package thaumicenergistics_ce.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * 蒸馏编码器的 Encode 按钮，来自参考构建自己的两态贴图。
 * 禁用态覆一层纱：图集里没有禁用帧，而“Encode”本身说明不了什么。
 * 绘制成 34x14，不用原生的 32x13：参考构建把它拉伸到面板条带。
 * 标签距顶部 3 像素居中，与铭刻机的标签一致。
 */
public class EncodeButton extends Button {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(
                    thaumicenergistics_ce.ThEIds.MODID, "textures/gui/button.png");

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
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int v = isHovered() && active ? HOVER_V : 0;
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                TEXTURE,
                getX(),
                getY(),
                0.0F,
                (float) v,
                WIDTH,
                HEIGHT,
                FRAME_W,
                FRAME_H,
                SHEET,
                SHEET);
        if (!active) {
            graphics.fill(getX(), getY(), getX() + WIDTH, getY() + HEIGHT, 0x8A000000);
        }
        renderLabel(graphics);
    }

    private void renderLabel(GuiGraphicsExtractor graphics) {
        String text = getMessage().getString();
        if (text.isEmpty()) {
            return;
        }
        var font = Minecraft.getInstance().font;
        // 颜色必须带 alpha：26.1.2 的 [GuiGraphicsExtractor#text] 第一句就是
        // `if (ARGB.alpha(color) != 0)`，alpha 为 0 的文字整句丢掉不画。
        // 1.21.1 的 [GuiGraphics#drawString] 没有这道闸门，同样的 0x000000 在那边是黑字。
        graphics.text(
                font,
                text,
                getX() + (WIDTH - font.width(text)) / 2,
                getY() + 3,
                0xFF000000,
                false);
    }
}
