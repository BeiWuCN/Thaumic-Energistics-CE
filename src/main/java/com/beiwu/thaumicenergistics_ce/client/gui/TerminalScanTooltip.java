package com.beiwu.thaumicenergistics_ce.client.gui;

import com.beiwu.thaumicenergistics_ce.ThEIds;
import com.leclowndu93150.thaumaturge.api.aspect.AspectIndexAccess;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.capability.KnowledgeAccess;
import com.leclowndu93150.thaumaturge.api.research.scan.ScanKeys;
import com.leclowndu93150.thaumaturge.client.render.aspect.AspectTagRenderer;
import com.mojang.datafixers.util.Either;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

@EventBusSubscriber(modid = ThEIds.MODID, value = Dist.CLIENT)
public final class TerminalScanTooltip {
    private static ItemStack scannedStack = ItemStack.EMPTY;
    private static Aspects scannedAspects;

    private TerminalScanTooltip() {}

    public static void render(GuiGraphics graphics, Font font, ItemStack stack, int x, int y, LocalPlayer player) {
        if (!KnowledgeAccess.of(player).isResearchKnown(ScanKeys.item(stack.getItem()))) {
            graphics.renderTooltip(font, stack, x, y);
            return;
        }
        var entries = AspectIndexAccess.index().of(stack).entries();
        if (entries.isEmpty()) {
            graphics.renderTooltip(font, stack, x, y);
            return;
        }

        // Scope the extra row to Thaumaturge's scan tooltip, not ordinary item hovers.
        ItemStack previousStack = scannedStack;
        Aspects previousAspects = scannedAspects;
        scannedStack = stack;
        scannedAspects = new Aspects(List.copyOf(entries), Math.max(1, (graphics.guiWidth() - 24) / 18));
        try {
            graphics.renderTooltip(font, stack, x, y);
        } finally {
            scannedStack = previousStack;
            scannedAspects = previousAspects;
        }
    }

    public static boolean isRendering() {
        return scannedAspects != null;
    }

    @SubscribeEvent
    public static void appendAspects(RenderTooltipEvent.GatherComponents event) {
        if (scannedAspects != null && event.getItemStack() == scannedStack) {
            event.getTooltipElements().add(Either.right(scannedAspects));
        }
    }

    public record Aspects(List<AspectInstance> entries, int columns) implements TooltipComponent, ClientTooltipComponent {
        private static final int STRIDE = 18;

        @Override
        public int getHeight() {
            return 2 + ((entries.size() + columns - 1) / columns) * STRIDE;
        }

        @Override
        public int getWidth(Font font) {
            return Math.min(entries.size(), columns) * STRIDE;
        }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
            for (int i = 0; i < entries.size(); i++) {
                var entry = entries.get(i);
                AspectTagRenderer.render(graphics, font, x + (i % columns) * STRIDE,
                        y + 2 + (i / columns) * STRIDE, entry.aspect(), entry.amount());
            }
        }
    }
}
