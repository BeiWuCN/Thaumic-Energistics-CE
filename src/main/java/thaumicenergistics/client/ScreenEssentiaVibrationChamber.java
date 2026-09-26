package thaumicenergistics.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;
import thaumicenergistics.menu.MenuEssentiaVibrationChamber;

/**
 * The Essentia Vibration Chamber's screen: how much fuel is buffered, how full the energy slot is, and how
 * far through the current unit of fuel the machine is.
 *
 * <p>Drawn, not blitted: the machine's texture is a 60x100 widget - the machine's face, not a window - and
 * the reference build blits it as a 176-pixel panel, leaving two thirds of the window empty. This screen
 * draws its own window out of fills in the game's container colours and paints the readings into it.
 *
 * <p>The tank is tinted by the aspect inside it, the energy slot in AE2's red, and the burn bar in a colour
 * of its own so progress is not read as a third tank.
 */
public class ScreenEssentiaVibrationChamber extends AbstractContainerScreen<MenuEssentiaVibrationChamber> {

    private static final int WIDTH = 176;
    private static final int HEIGHT = 166;

    /** The window's own colours, which are the ones every container in the game is drawn in. */
    private static final int PANEL = 0xFFC6C6C6;
    private static final int PANEL_LIGHT = 0xFFFFFFFF;
    private static final int PANEL_DARK = 0xFF555555;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int SLOT_DARK = 0xFF373737;

    /** The energy slot's colour, AE2's own, and the burn bar's. */
    private static final int ENERGY = 0xFFAA0000;
    private static final int BURN = 0xFFFFAA00;
    private static final int TANK_EMPTY = 0xFF4B4B4B;

    /** The two gauges and the progress bar, in window coordinates. */
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

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        // The window, with the two-pixel border every container in the game has.
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, PANEL);
        graphics.fill(x, y, x + WIDTH, y + 1, PANEL_LIGHT);
        graphics.fill(x, y, x + 1, y + HEIGHT, PANEL_LIGHT);
        graphics.fill(x + WIDTH - 1, y, x + WIDTH, y + HEIGHT, PANEL_DARK);
        graphics.fill(x, y + HEIGHT - 1, x + WIDTH, y + HEIGHT, PANEL_DARK);

        // Every well is drawn one pixel up and to the left of its slot, which is where the game draws a well
        // in its own textures; on the slot's own coordinates every item sits a pixel down and right of centre.
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                slot(graphics, x + 8 + column * 18 - 1, y + 84 + row * 18 - 1);
            }
        }
        for (int column = 0; column < 9; column++) {
            slot(graphics, x + 8 + column * 18 - 1, y + 142 - 1);
        }

        // Every bar is framed, so an empty one is still a bar. The first version drew the recess only and
        // painted the fill over it, which left a machine with nothing in it showing three dark rectangles.
        int aspectColour = menu.reading(MenuEssentiaVibrationChamber.DATA_ASPECT_COLOUR);
        bar(graphics, x + TANK_X, y + GAUGE_Y, GAUGE_W, GAUGE_H, menu.essentiaFill(),
                aspectColour == 0 ? TANK_EMPTY : aspectColour, true);
        bar(graphics, x + ENERGY_X, y + GAUGE_Y, GAUGE_W, GAUGE_H, menu.energyFill(), ENERGY, true);
        bar(graphics, x + BURN_X, y + BURN_Y, BURN_W, BURN_H, menu.burnProgress(), BURN, false);

        // The numbers go in a column beside the gauges, not under them: under them is the window's own
        // "Inventory" label, which the first version printed "Essentia 26 / 64" straight through.
        Component essentia = Component.translatable(
                "thaumicenergistics.gui.vibration_chamber.essentia",
                menu.reading(MenuEssentiaVibrationChamber.DATA_ESSENTIA),
                menu.reading(MenuEssentiaVibrationChamber.DATA_ESSENTIA_MAX));
        Component energy = Component.translatable(
                "thaumicenergistics.gui.vibration_chamber.energy",
                formatEnergy(menu.reading(MenuEssentiaVibrationChamber.DATA_ENERGY)),
                formatEnergy(menu.reading(MenuEssentiaVibrationChamber.DATA_ENERGY_MAX)));
        graphics.drawString(font, essentia, x + TEXT_X, y + TEXT_Y, 0x404040, false);
        graphics.drawString(font, energy, x + TEXT_X, y + TEXT_Y + 12, 0x404040, false);

        // The reason comes first: a full slot pauses the burn rather than ending it, and a machine with no
        // cable is not idle either - a line that did not say which would read as having run out of fuel.
        Component state = heldBackLine();
        if (state == null) {
            state = menu.isBurning()
                    ? Component.translatable(
                            "thaumicenergistics.gui.vibration_chamber.rate",
                            String.format(
                                    "%.1f",
                                    menu.reading(MenuEssentiaVibrationChamber.DATA_AE_PER_TICK) / 10.0))
                    : Component.translatable("thaumicenergistics.gui.vibration_chamber.idle");
        }
        graphics.drawString(font, state, x + TEXT_X, y + TEXT_Y + 34, 0x404040, false);
    }

    /**
     * One bar: a dark frame, a recess inside it, and the fill at the end of the recess. The frame is the
     * point - a recess alone is a dark rectangle whether the machine is empty or full. Vertical bars fill
     * upwards, like every tank in the game; the progress bar fills to the right.
     */
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

    /** One player slot, drawn the way the game draws its own: dark top and left, light bottom and right. */
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
                            "thaumicenergistics.gui.vibration_chamber.essentia.tip",
                            menu.reading(MenuEssentiaVibrationChamber.DATA_ESSENTIA),
                            menu.reading(MenuEssentiaVibrationChamber.DATA_ESSENTIA_MAX)),
                    mouseX,
                    mouseY);
        } else if (inside(mouseX, mouseY, ENERGY_X, GAUGE_Y, GAUGE_W, GAUGE_H)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(
                    "thaumicenergistics.gui.vibration_chamber.energy.tip",
                    formatEnergy(menu.reading(MenuEssentiaVibrationChamber.DATA_ENERGY)),
                    formatEnergy(menu.reading(MenuEssentiaVibrationChamber.DATA_ENERGY_MAX))));
            lines.add(Component.translatable("thaumicenergistics.gui.vibration_chamber.energy.limit")
                    .withStyle(ChatFormatting.GRAY));
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        } else if (inside(mouseX, mouseY, BURN_X, BURN_Y, BURN_W, BURN_H)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(
                    "thaumicenergistics.gui.vibration_chamber.burn.tip",
                    Math.round(menu.burnProgress() * 100.0F)));
            // Why the bar stopped: a percentage alone cannot tell a stalled burn from a slow one.
            MutableComponent reason = heldBackLine();
            if (reason != null) {
                lines.add(reason.withStyle(ChatFormatting.GRAY));
            }
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    /**
     * The reason the machine is not burning, or null while it is or has nothing to burn: the state the block
     * entity decided, never a fullness worked out from the readings.
     */
    private @Nullable MutableComponent heldBackLine() {
        return switch (menu.state()) {
            case NO_NETWORK -> Component.translatable("thaumicenergistics.jade.no_network");
            case PAUSED_FULL -> Component.translatable("thaumicenergistics.jade.tank_full");
            case BURNING, IDLE -> null;
        };
    }

    private boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) {
        int localX = mouseX - leftPos;
        int localY = mouseY - topPos;
        return localX >= x && localX < x + width && localY >= y && localY < y + height;
    }

    /** An energy figure, in kAE past a thousand - the unit the machine's own tooltip already uses. */
    private static String formatEnergy(int ae) {
        return ae >= 1000 ? String.format("%.1fkAE", ae / 1000.0) : ae + "AE";
    }
}
