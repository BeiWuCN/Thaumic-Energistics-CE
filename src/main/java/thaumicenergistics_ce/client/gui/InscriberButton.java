package thaumicenergistics_ce.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * 知识铭刻机的按钮，贴图来自参考构建的两态图集。
 * 一张 32x32 的图集放两个 32x13 的帧，空闲在 v=0、悬停在 v=15，没有禁用帧：
 * 标签说明哪里不对（"No Core"、"Invalid"、"Full"），只要没悬停就画空闲态。
 * 标签向下居中两像素，与参考构建一致：原版居中位置太低。
 */
public class InscriberButton extends Button {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(
                    thaumicenergistics_ce.ThEIds.MODID, "textures/gui/button.png");

    private static final int SHEET = 32;
    private static final int WIDTH = 32;
    private static final int HEIGHT = 13;
    private static final int HOVER_V = 15;

    public InscriberButton(int x, int y, Component label, OnPress onPress) {
        super(x, y, WIDTH, HEIGHT, label, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int v = isHovered() ? HOVER_V : 0;
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                TEXTURE,
                getX(),
                getY(),
                0.0F,
                (float) v,
                WIDTH,
                HEIGHT,
                SHEET,
                SHEET);
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
                getY() + 2,
                0xFF000000,
                false);
    }
}
