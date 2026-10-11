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

public class ScreenEssentiaResonanceChamber extends AbstractContainerScreen<MenuEssentiaVibrationChamber> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(ThEIds.MODID, "textures/gui/essentia_vibration_chamber.png");

    private static final int WIDTH = 176;
    private static final int HEIGHT = 168;

    private static final int TEXTURE_SIZE = 256;

    private static final int TANK_X = 64;
    private static final int TANK_Y = 11;
    private static final int TANK_W = 12;
    private static final int TANK_H = 58;
    private static final int TANK_U = 177;
    private static final int TANK_V = 33;
    private static final int TANK_SH = 58;

    private static final int ENERGY_X = 100;
    private static final int ENERGY_Y = 37;
    private static final int ENERGY_W = 4;
    private static final int ENERGY_H = 16;
    private static final int ENERGY_U = 177;
    private static final int ENERGY_V = 15;
    private static final int ENERGY_SH = 16;

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

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        barTooltips(graphics, mouseX, mouseY);
    }

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
            MutableComponent reason = heldBackLine();
            if (reason != null) {
                lines.add(reason.withStyle(ChatFormatting.GRAY));
            }
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

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

    private static String formatEnergy(int ae) {
        return ae >= 1000 ? String.format("%.1fkAE", ae / 1000.0) : ae + "AE";
    }
}
