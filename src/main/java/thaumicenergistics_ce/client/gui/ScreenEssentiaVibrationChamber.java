package thaumicenergistics_ce.client.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.menu.MenuEssentiaVibrationChamber;

/**
 * 源质振动室的界面：燃料缓冲、能量槽和燃烧进度。
 * 机器的贴图是正面 60x100 的控件，不是窗口，本界面拿游戏容器的颜色自己填一个窗口。
 * 储罐按所属要素着色，能量槽用 AE2 的红色，燃烧条用自己的颜色。
 */
public class ScreenEssentiaVibrationChamber extends AbstractContainerScreen<MenuEssentiaVibrationChamber> {

    private static final int WIDTH = 176;
    private static final int HEIGHT = 166;

    /** 窗口自身的颜色，游戏里每个容器画窗口都用它。 */
    private static final int PANEL = 0xFFC6C6C6;
    private static final int PANEL_LIGHT = 0xFFFFFFFF;
    private static final int PANEL_DARK = 0xFF555555;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int SLOT_DARK = 0xFF373737;

    /** 能量槽的颜色取自 AE2，燃烧条同色。 */
    private static final int ENERGY = 0xFFAA0000;
    private static final int BURN = 0xFFFFAA00;
    private static final int TANK_EMPTY = 0xFF4B4B4B;

    private static final int GAUGE_Y = 20;
    private static final int GAUGE_H = 46;
    private static final int GAUGE_W = 14;
    private static final int TANK_X = 22;
    private static final int ENERGY_X = 44;
    private static final int BURN_X = 70;
    private static final int BURN_Y = 46;
    private static final int BURN_W = 84;
    private static final int BURN_H = 6;

    private static final int TEXT_X = 70;
    private static final int TEXT_Y = 20;

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

        // 窗口，带游戏里每个容器都有的两像素边框。
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, PANEL);
        graphics.fill(x, y, x + WIDTH, y + 1, PANEL_LIGHT);
        graphics.fill(x, y, x + 1, y + HEIGHT, PANEL_LIGHT);
        graphics.fill(x + WIDTH - 1, y, x + WIDTH, y + HEIGHT, PANEL_DARK);
        graphics.fill(x, y + HEIGHT - 1, x + WIDTH, y + HEIGHT, PANEL_DARK);

        // 凹格画在各槽位左上偏一像素，和游戏自身的画法一致；
        // 按槽位自身坐标画，物品会偏到中心右下各一像素。
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                slot(graphics, x + 8 + column * 18 - 1, y + 84 + row * 18 - 1);
            }
        }
        for (int column = 0; column < 9; column++) {
            slot(graphics, x + 8 + column * 18 - 1, y + 142 - 1);
        }

        // 每条进度条都带边框，空着也还是一条条；
        // 只画一个凹槽再往上涂填充，空机器的三条会变成黑块。
        int aspectColour = menu.reading(MenuEssentiaVibrationChamber.DATA_ASPECT_COLOUR);
        bar(graphics, x + TANK_X, y + GAUGE_Y, GAUGE_W, GAUGE_H, menu.essentiaFill(),
                aspectColour == 0 ? TANK_EMPTY : aspectColour, true);
        bar(graphics, x + ENERGY_X, y + GAUGE_Y, GAUGE_W, GAUGE_H, menu.energyFill(), ENERGY, true);
        bar(graphics, x + BURN_X, y + BURN_Y, BURN_W, BURN_H, menu.burnProgress(), BURN, false);

        // 数字排在仪表旁边一列，不排在下面：下面那行是窗口自带的「Inventory」标签，
        // 读数打在那里会压穿它。
        Component essentia = Component.translatable(
                "thaumicenergistics_ce.gui.vibration_chamber.essentia",
                menu.reading(MenuEssentiaVibrationChamber.DATA_ESSENTIA),
                menu.reading(MenuEssentiaVibrationChamber.DATA_ESSENTIA_MAX));
        Component energy = Component.translatable(
                "thaumicenergistics_ce.gui.vibration_chamber.energy",
                formatEnergy(menu.reading(MenuEssentiaVibrationChamber.DATA_ENERGY)),
                formatEnergy(menu.reading(MenuEssentiaVibrationChamber.DATA_ENERGY_MAX)));
        graphics.drawString(font, essentia, x + TEXT_X, y + TEXT_Y, 0x404040, false);
        graphics.drawString(font, energy, x + TEXT_X, y + TEXT_Y + 12, 0x404040, false);

        // 先给原因：槽满只是暂停燃烧，没接线的机器也不算空闲；
        // 不说清是哪一种，这行会被读成燃料耗尽。
        Component state = heldBackLine();
        if (state == null) {
            state = menu.isBurning()
                    ? Component.translatable(
                            "thaumicenergistics_ce.gui.vibration_chamber.rate",
                            String.format(
                                    "%.1f",
                                    menu.reading(MenuEssentiaVibrationChamber.DATA_AE_PER_TICK) / 10.0))
                    : Component.translatable("thaumicenergistics_ce.gui.vibration_chamber.idle");
        }
        graphics.drawString(font, state, x + TEXT_X, y + TEXT_Y + 34, 0x404040, false);
    }

    private static void bar(
            GuiGraphics graphics, int x, int y, int width, int height, float fill, int colour, boolean vertical) {
        graphics.fill(x, y, x + width, y + height, SLOT_DARK);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, TANK_EMPTY);
        int inner = (vertical ? height : width) - 2;
        int length = Math.round(inner * Math.max(0.0F, Math.min(1.0F, fill)));
        if (length <= 0) {
            return;
        }
        if (vertical) {
            graphics.fill(x + 1, y + height - 1 - length, x + width - 1, y + height - 1, colour);
        } else {
            graphics.fill(x + 1, y + 1, x + 1 + length, y + height - 1, colour);
        }
    }

    private static void slot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, SLOT_DARK);
        graphics.fill(x + 1, y + 1, x + 18, y + 18, PANEL_LIGHT);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT);
    }

    private void barTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (inside(mouseX, mouseY, TANK_X, GAUGE_Y, GAUGE_W, GAUGE_H)) {
            graphics.renderTooltip(
                    font,
                    Component.translatable(
                            "thaumicenergistics_ce.gui.vibration_chamber.essentia.tip",
                            menu.reading(MenuEssentiaVibrationChamber.DATA_ESSENTIA),
                            menu.reading(MenuEssentiaVibrationChamber.DATA_ESSENTIA_MAX)),
                    mouseX,
                    mouseY);
        } else if (inside(mouseX, mouseY, ENERGY_X, GAUGE_Y, GAUGE_W, GAUGE_H)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(
                    "thaumicenergistics_ce.gui.vibration_chamber.energy.tip",
                    formatEnergy(menu.reading(MenuEssentiaVibrationChamber.DATA_ENERGY)),
                    formatEnergy(menu.reading(MenuEssentiaVibrationChamber.DATA_ENERGY_MAX))));
            lines.add(Component.translatable("thaumicenergistics_ce.gui.vibration_chamber.energy.limit")
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
