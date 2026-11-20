package com.beiwu.thaumicenergistics_ce.client.gui;

import appeng.api.config.ActionItems;
import appeng.client.gui.widgets.ActionButton;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class ArcaneGridReturnButton extends ActionButton {
    private static final int SIZE = 8;
    private final Component tooltip;

    ArcaneGridReturnButton(int x, int y, boolean toNetwork, Component tooltip, Runnable action) {
        super(toNetwork ? ActionItems.S_STASH : ActionItems.S_STASH_TO_PLAYER_INV, action);
        this.tooltip = tooltip;
        setPosition(x, y);
        width = SIZE;
        height = SIZE;
        setHalfSize(true);
        setDisableBackground(true);
    }

    @Override
    public List<Component> getTooltipMessage() {
        return List.of(tooltip);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        graphics.fill(x, y, x + SIZE, y + SIZE, 0xFF34354A);
        graphics.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, 0xFF696C86);
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        if (isHovered()) {
            graphics.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, 0x55FFFFFF);
        }
    }
}
