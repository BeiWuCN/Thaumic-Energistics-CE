package com.beiwu.thaumicenergistics_ce.mixin;

import appeng.api.stacks.AEItemKey;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.RepoSlot;
import com.beiwu.thaumicenergistics_ce.client.gui.TerminalScanTooltip;
import com.beiwu.thaumicenergistics_ce.network.TerminalScanPayload;
import com.leclowndu93150.thaumaturge.client.item.InventoryScanHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = InventoryScanHandler.class, remap = false)
public abstract class TerminalInventoryScanMixin {
    @Unique private static final int TECE_REPO_TARGET = -2;
    @Unique private static AEItemKey tece$scanKey;
    @Unique private static int tece$menuId;
    @Shadow private static int target;
    @Shadow private static int ticks;

    @Inject(method = "targetUnderMouse", at = @At("HEAD"), cancellable = true)
    private static void tece$findNetworkItem(AbstractContainerScreen<?> screen, LocalPlayer player,
            int mouseX, int mouseY, CallbackInfoReturnable<Integer> callback) {
        if (!(screen instanceof MEStorageScreen<?>) || !(screen.getSlotUnderMouse() instanceof RepoSlot slot)) {
            tece$scanKey = null;
            return;
        }

        var entry = slot.getEntry();
        if (entry == null || entry.getStoredAmount() <= 0 || !(entry.getWhat() instanceof AEItemKey item)) {
            tece$scanKey = null;
            callback.setReturnValue(Integer.MIN_VALUE);
            return;
        }

        if (!item.equals(tece$scanKey) || tece$menuId != player.containerMenu.containerId) {
            ticks = 0;
        }
        tece$scanKey = item;
        tece$menuId = player.containerMenu.containerId;
        callback.setReturnValue(TECE_REPO_TARGET);
    }

    @Inject(method = "hoveredSlot", at = @At("HEAD"), cancellable = true)
    private static void tece$resolveNetworkSlot(LocalPlayer player, CallbackInfoReturnable<Slot> callback) {
        if (target == TECE_REPO_TARGET) {
            callback.setReturnValue(tece$networkSlot(player));
        }
    }

    @Unique
    private static RepoSlot tece$networkSlot(LocalPlayer player) {
        if (player.containerMenu.containerId == tece$menuId
                && Minecraft.getInstance().screen instanceof MEStorageScreen<?> screen
                && screen.getMenu() == player.containerMenu
                && screen.getSlotUnderMouse() instanceof RepoSlot slot) {
            var entry = slot.getEntry();
            if (entry != null && entry.getStoredAmount() > 0 && entry.getWhat().equals(tece$scanKey)) {
                return slot;
            }
        }
        return null;
    }

    @Inject(method = "renderKnownAspects", at = @At("HEAD"), cancellable = true)
    private static void tece$renderInventoryStyleTooltip(GuiGraphics graphics, Minecraft minecraft,
            LocalPlayer player, ItemStack stack, int centreX, int y,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
        if (minecraft.screen instanceof MEStorageScreen<?> screen && screen.getSlotUnderMouse() != null) {
            int left = screen.getGuiLeft();
            int top = screen.getGuiTop();
            // Thaumaturge 1.1.1 draws during the container foreground pass.
            // The tooltip positioner expects screen-space coordinates.
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(-left, -top, 0);
                TerminalScanTooltip.render(graphics, minecraft.font, stack,
                        left + centreX, top + y + 28, player);
            } finally {
                graphics.pose().popPose();
            }
            callback.cancel();
        }
    }

    @Inject(method = "onTooltipRender", require = 0, at = @At("HEAD"), cancellable = true)
    private static void tece$allowScanTooltip(net.neoforged.neoforge.client.event.RenderTooltipEvent.Pre event,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
        if (TerminalScanTooltip.isRendering()) {
            callback.cancel();
        }
    }

    @Redirect(method = "onScreenRender", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderTooltip(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V"))
    private static void tece$renderScanTooltip(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof MEStorageScreen<?> && minecraft.player != null && ticks == 0) {
            TerminalScanTooltip.render(graphics, font, stack, x, y, minecraft.player);
        } else {
            graphics.renderTooltip(font, stack, x, y);
        }
    }

    @Redirect(method = "onScreenRender", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"))
    private static int tece$drawScanText(GuiGraphics graphics, Font font, Component text,
            int x, int y, int color, boolean shadow) {
        if (!(Minecraft.getInstance().screen instanceof MEStorageScreen<?> screen)) {
            return graphics.drawString(font, text, x, y, color, shadow);
        }
        int left = Math.max(2, Math.min(x, screen.width - font.width(text) - 2));
        int top = Math.max(2, y - 14);
        // Item tooltips are drawn at z=400 by Minecraft.
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(0, 0, 450);
            return graphics.drawString(font, text, left, top, color, shadow);
        } finally {
            graphics.pose().popPose();
        }
    }

    @Redirect(method = "onClientTick", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/network/PacketDistributor;sendToServer(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;[Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V"))
    private static void tece$sendScan(CustomPacketPayload packet, CustomPacketPayload[] additional) {
        if (target != TECE_REPO_TARGET) {
            PacketDistributor.sendToServer(packet, additional);
            return;
        }
        var player = Minecraft.getInstance().player;
        if (player != null && tece$networkSlot(player) != null) {
            PacketDistributor.sendToServer(new TerminalScanPayload(tece$menuId, tece$scanKey));
        }
    }
}
