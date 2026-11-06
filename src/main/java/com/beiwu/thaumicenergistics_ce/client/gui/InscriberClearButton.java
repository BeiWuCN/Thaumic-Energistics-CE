package com.beiwu.thaumicenergistics_ce.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** The small x painted in the inscriber panel: clearing the grid has no label of its own. */
public class InscriberClearButton extends Button {

    private static final int SIZE = 8;

    public InscriberClearButton(int x, int y, Component tooltip, OnPress onPress) {
        super(x, y, SIZE, SIZE, Component.empty(), onPress, DEFAULT_NARRATION);
        setTooltip(Tooltip.create(tooltip));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (isHovered()) {
            graphics.fill(getX() + 1, getY() + 1, getX() + SIZE - 1, getY() + SIZE - 1, 0x55FFFFFF);
        }
    }
}
