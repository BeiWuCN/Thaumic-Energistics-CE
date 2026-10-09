package thaumicenergistics_ce.client.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.menu.MenuEssentiaVibrationChamber;

/**
 * 源质振动室的界面：源质缓冲、能量槽和燃烧进度，版面照 {@code textures/gui/essentia_vibration_chamber.png}。
 * 窗口占贴图 (0,0)-(175,167)，贴图右侧 176 起另画着三条同尺寸的满格精灵，本身不属于窗口。
 * 窗口里那条高条是源质槽、那条条纹条是能量槽、那三簇火苗是燃烧条，三条都按比例把精灵的一段贴进来。
 * 读数不进图标区，只在三条仪表的悬浮提示里给；窗口顶部那行机器名由原版容器自己画。
 */
public class ScreenEssentiaResonanceChamber extends AbstractContainerScreen<MenuEssentiaVibrationChamber> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(ThEIds.MODID, "textures/gui/essentia_vibration_chamber.png");

    private static final int WIDTH = 176;
    private static final int HEIGHT = 168;

    /** 贴图是 256x256，绘制的 UV 直接用图上的像素坐标，所以长宽也报同一个数。 */
    private static final int TEXTURE_SIZE = 256;

    // 三条仪表：窗口里画的是空槽，贴图右侧 176 起画的是同尺寸的满格精灵。
    // 每条的 [X,Y,W,H] 是空槽（也是悬浮提示的命中框），[U,V,SH] 是从精灵底部往上数的那一段。
    /**
     * 源质：窗口里那条高条，精灵是右侧最下面那条淡色竖条。
     * 空槽从 y11 起算，把槽自己那个深色封顶也圈进来：满格时整条精灵（含它自己的封顶）正好盖住空槽，不会两个顶叠着。
     */
    private static final int TANK_X = 64;
    private static final int TANK_Y = 11;
    private static final int TANK_W = 12;
    private static final int TANK_H = 58;
    private static final int TANK_U = 177;
    private static final int TANK_V = 33;
    private static final int TANK_SH = 58;

    /** 能量：窗口里那条条纹条，精灵是右侧火苗下面那列紫条。两边内部都是 4x16。 */
    private static final int ENERGY_X = 100;
    private static final int ENERGY_Y = 37;
    private static final int ENERGY_W = 4;
    private static final int ENERGY_H = 16;
    private static final int ENERGY_U = 177;
    private static final int ENERGY_V = 15;
    private static final int ENERGY_SH = 16;

    /** 燃烧：窗口里那三簇火苗，精灵是右侧最顶上那三簇。 */
    private static final int BURN_X = 81;
    private static final int BURN_Y = 39;
    private static final int BURN_W = 13;
    private static final int BURN_H = 13;
    private static final int BURN_U = 177;
    private static final int BURN_V = 0;
    private static final int BURN_SH = 14;

    private static final int TITLE_X = 8;
    private static final int TITLE_Y = 6;
    private static final int INVENTORY_LABEL_X = 8;
    private static final int INVENTORY_LABEL_Y = 72;

    public ScreenEssentiaResonanceChamber(
            MenuEssentiaVibrationChamber menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        this.titleLabelX = TITLE_X;
        this.titleLabelY = TITLE_Y;
        this.inventoryLabelX = INVENTORY_LABEL_X;
        this.inventoryLabelY = INVENTORY_LABEL_Y;
    }

    /**
     * 悬浮提示在物品之后画：这一版一帧拆成状态、背景、内容三段，
     * [extractContents] 里已经有槽里的物品和悬停槽位，提示再叠在它上面。
     */
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        barTooltips(graphics, mouseX, mouseY);
    }

    /**
     * 面板和三条仪表归 [extractBackground]，不归 [extractContents]：这一版把一帧拆成两半，
     * 槽里的物品、悬停的那个槽位和 tooltip 都在 [extractContents] 里，占住它又不调 super，
     * 物品和 tooltip 就一起没了。画进背景也就画在物品下面，正是这几样要的层。
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                TEXTURE,
                leftPos,
                topPos,
                0.0F,
                0.0F,
                WIDTH,
                HEIGHT,
                TEXTURE_SIZE,
                TEXTURE_SIZE);

        reveal(graphics, leftPos + TANK_X, topPos + TANK_Y, TANK_W, TANK_H,
                TANK_U, TANK_V, TANK_SH, menu.essentiaFill());
        reveal(graphics, leftPos + ENERGY_X, topPos + ENERGY_Y, ENERGY_W, ENERGY_H,
                ENERGY_U, ENERGY_V, ENERGY_SH, menu.energyFill());
        reveal(graphics, leftPos + BURN_X, topPos + BURN_Y, BURN_W, BURN_H,
                BURN_U, BURN_V, BURN_SH, menu.burnProgress());
    }

    /**
     * 按比例从下往上把满格精灵的一段贴进空槽。贴图里的精灵本身就是这些仪表的花纹，用纯色填会把它盖掉。
     * 取的是精灵底部那 [length] 行：空槽多高决定显示多长，精灵自己的高度决定从那一段的哪一行开始取。
     */
    private static void reveal(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int u,
            int v,
            int sourceHeight,
            float amount) {
        int length = Math.round(height * Math.max(0.0F, Math.min(1.0F, amount)));
        if (length <= 0) {
            return;
        }
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                TEXTURE,
                x,
                y + height - length,
                (float) u,
                (float) (v + sourceHeight - length),
                width,
                length,
                TEXTURE_SIZE,
                TEXTURE_SIZE);
    }

    private void barTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (inside(mouseX, mouseY, TANK_X, TANK_Y, TANK_W, TANK_H)) {
            graphics.setTooltipForNextFrame(
                    font,
                    Component.translatable(
                            "thaumicenergistics_ce.gui.vibration_chamber.essentia.tip",
                            menu.reading(MenuEssentiaVibrationChamber.DATA_ESSENTIA),
                            menu.reading(MenuEssentiaVibrationChamber.DATA_ESSENTIA_MAX)),
                    mouseX,
                    mouseY);
        } else if (inside(mouseX, mouseY, ENERGY_X, ENERGY_Y, ENERGY_W, ENERGY_H)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(
                    "thaumicenergistics_ce.gui.vibration_chamber.energy.tip",
                    formatEnergy(menu.reading(MenuEssentiaVibrationChamber.DATA_ENERGY)),
                    formatEnergy(menu.reading(MenuEssentiaVibrationChamber.DATA_ENERGY_MAX))));
            lines.add(Component.translatable("thaumicenergistics_ce.gui.vibration_chamber.energy.limit")
                    .withStyle(ChatFormatting.GRAY));
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        } else if (inside(mouseX, mouseY, BURN_X, BURN_Y, BURN_W, BURN_H)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(
                    "thaumicenergistics_ce.gui.vibration_chamber.burn.tip",
                    Math.round(menu.burnProgress() * 100.0F)));
            // 进度条为何停住：只看百分比分不出燃烧是卡住还是变慢。
            MutableComponent reason = heldBackLine();
            if (reason != null) {
                lines.add(reason.withStyle(ChatFormatting.GRAY));
            }
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    /**
     * 机器为什么没在燃烧，否则是 null（正在燃烧或没东西可烧）。
     * 判定由方块实体做出，不是根据读数算出来的满溢状态。
     */
    private @Nullable MutableComponent heldBackLine() {
        return switch (menu.state()) {
            case NO_NETWORK -> Component.translatable("thaumicenergistics_ce.jade.no_network");
            case PAUSED_FULL -> Component.translatable("thaumicenergistics_ce.jade.tank_full");
            case BURNING, IDLE -> null;
        };
    }

    private boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) {
        int localX = mouseX - leftPos;
        int localY = mouseY - topPos;
        return localX >= x && localX < x + width && localY >= y && localY < y + height;
    }

    /** 能量数值，超过一千写成 kAE，机器自己的 tooltip 也用这个单位。 */
    private static String formatEnergy(int ae) {
        return ae >= 1000 ? String.format("%.1fkAE", ae / 1000.0) : ae + "AE";
    }
}
