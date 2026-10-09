package thaumicenergistics_ce.client.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.blockentity.vibrationchamber.BlockEntityEssentiaVibrationChamber;
import thaumicenergistics_ce.menu.MenuEssentiaVibrationChamber;

/**
 * 源质振动室的界面：源质缓冲、能量槽和燃烧进度，版面照 {@code textures/gui/essentia_vibration_chamber.png}。
 * 窗口占贴图 (0,0)-(175,167)，贴图右侧 176 起另画着三条同尺寸的满格精灵，本身不属于窗口。
 * 窗口里那条高条是源质槽、那条条纹条是能量槽、那三簇火苗是燃烧条，三条都按比例把精灵的一段贴进来。
 * 读数不进图标区，只在三条仪表的悬浮提示里给；窗口顶部那行机器名由原版容器自己画。
 */
public class ScreenEssentiaVibrationChamber extends AbstractContainerScreen<MenuEssentiaVibrationChamber> {

    private static final ResourceLocation TEXTURE = ThEIds.id("textures/gui/essentia_vibration_chamber.png");

    private static final int WIDTH = 176;
    private static final int HEIGHT = 168;

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

    public ScreenEssentiaVibrationChamber(
            MenuEssentiaVibrationChamber menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
        this.titleLabelX = TITLE_X;
        this.titleLabelY = TITLE_Y;
        this.inventoryLabelX = INVENTORY_LABEL_X;
        this.inventoryLabelY = INVENTORY_LABEL_Y;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        barTooltips(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }

    /**
     * 悬停框换成 AE2 的配色，见 {@link Ae2SlotHighlight}。
     * 不调 super：基类那版先贴一层半透明白方块，框底会发白。
     */
    @Override
    protected void renderSlotHighlight(GuiGraphics graphics, Slot slot, int mouseX, int mouseY, float partialTick) {
        Ae2SlotHighlight.render(graphics, slot);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        graphics.blit(TEXTURE, x, y, 0, 0, WIDTH, HEIGHT);

        reveal(graphics, x + TANK_X, y + TANK_Y, TANK_W, TANK_H,
                TANK_U, TANK_V, TANK_SH, menu.essentiaFill());
        reveal(graphics, x + ENERGY_X, y + ENERGY_Y, ENERGY_W, ENERGY_H,
                ENERGY_U, ENERGY_V, ENERGY_SH, menu.energyFill());
        reveal(graphics, x + BURN_X, y + BURN_Y, BURN_W, BURN_H,
                BURN_U, BURN_V, BURN_SH, menu.burnProgress());
    }

    /**
     * 按比例从下往上把满格精灵的一段贴进空槽。贴图里的精灵本身就是这些仪表的花纹，用纯色填会把它盖掉。
     * 取的是精灵底部那 [length] 行：空槽多高决定显示多长，精灵自己的高度决定从那一段的哪一行开始取。
     */
    private static void reveal(
            GuiGraphics graphics,
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
        graphics.blit(TEXTURE, x, y + height - length, u, v + sourceHeight - length, width, length);
    }

    private void barTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (inside(mouseX, mouseY, TANK_X, TANK_Y, TANK_W, TANK_H)) {
            graphics.renderTooltip(
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
            lines.add(Component.translatable(
                            "thaumicenergistics_ce.gui.vibration_chamber.energy.limit",
                            String.format("%.0f", BlockEntityEssentiaVibrationChamber.MAX_OUTPUT_PER_TICK))
                    .withStyle(ChatFormatting.GRAY));
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
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
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
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
