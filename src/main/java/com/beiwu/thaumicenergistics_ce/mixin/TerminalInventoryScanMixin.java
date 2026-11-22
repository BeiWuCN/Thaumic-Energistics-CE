package com.beiwu.thaumicenergistics_ce.mixin;

import appeng.api.stacks.AEItemKey;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.RepoSlot;
import com.beiwu.thaumicenergistics_ce.client.gui.TerminalScanTooltip;
import com.beiwu.thaumicenergistics_ce.network.TerminalScanPayload;
import com.leclowndu93150.thaumaturge.client.item.InventoryScanHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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

    @Redirect(method = "onScreenRender", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V"))
    private static void tece$renderScanTooltip(GuiGraphicsExtractor graphics, Font font, ItemStack stack, int x, int y) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof MEStorageScreen<?> && minecraft.player != null && ticks == 0) {
            TerminalScanTooltip.render(graphics, font, stack, x, y, minecraft.player);
        } else {
            graphics.setTooltipForNextFrame(font, stack, x, y);
        }
    }

    @Inject(method = "renderKnownAspects", at = @At("HEAD"), cancellable = true)
    private static void tece$hideFloatingAspects(GuiGraphicsExtractor graphics, Minecraft minecraft, LocalPlayer player,
            ItemStack stack, int centreX, int y, CallbackInfo callback) {
        if (minecraft.screen instanceof MEStorageScreen<?>) {
            callback.cancel();
        }
    }

    @Redirect(method = "onScreenRender", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"))
    private static void tece$drawScanText(GuiGraphicsExtractor graphics, Font font, Component text,
            int x, int y, int color, boolean shadow) {
        if (!(Minecraft.getInstance().screen instanceof MEStorageScreen<?> screen)) {
            graphics.text(font, text, x, y, color, shadow);
            return;
        }
        int left = Math.max(2, Math.min(x, screen.width - font.width(text) - 2));
        int top = Math.max(2, y - 14);
        graphics.text(font, text, left, top, color, shadow);
    }

    @Redirect(method = "onClientTick", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/client/network/ClientPacketDistributor;sendToServer(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;[Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V"))
    private static void tece$sendScan(CustomPacketPayload packet, CustomPacketPayload[] additional) {
        if (target != TECE_REPO_TARGET) {
            ClientPacketDistributor.sendToServer(packet, additional);
            return;
        }
        var player = Minecraft.getInstance().player;
        if (player != null && tece$networkSlot(player) != null) {
            ClientPacketDistributor.sendToServer(new TerminalScanPayload(tece$menuId, tece$scanKey));
        }
    }
}
