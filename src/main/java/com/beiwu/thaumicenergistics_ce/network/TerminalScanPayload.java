package com.beiwu.thaumicenergistics_ce.network;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.menu.me.common.MEStorageMenu;
import com.beiwu.thaumicenergistics_ce.ThEIds;
import com.leclowndu93150.thaumaturge.api.research.scan.ScanTarget;
import com.leclowndu93150.thaumaturge.api.research.scan.ScanningManager;
import com.leclowndu93150.thaumaturge.content.item.ThaumometerItem;
import com.leclowndu93150.thaumaturge.network.ServerboundScanSlotPayload;
import com.leclowndu93150.thaumaturge.registry.TTAttachments;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public record TerminalScanPayload(int containerId, AEKey key) implements CustomPacketPayload {
    public static final Type<TerminalScanPayload> TYPE = new Type<>(ThEIds.id("terminal_scan"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalScanPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, TerminalScanPayload::containerId,
                    AEKey.STREAM_CODEC, TerminalScanPayload::key, TerminalScanPayload::new);

    @Override
    public Type<TerminalScanPayload> type() {
        return TYPE;
    }

    public void handle(Player player) {
        if (!(player instanceof ServerPlayer server) || server.isSpectator()
                || !(server.containerMenu instanceof MEStorageMenu menu)
                || menu.containerId != containerId || !menu.stillValid(server)
                || !menu.isValidMenu() || !menu.getHost().getLinkStatus().connected()
                || !(menu.getCarried().getItem() instanceof ThaumometerItem)
                || !(key instanceof AEItemKey item)) {
            return;
        }

        long now = server.level().getGameTime();
        // Share Thaumaturge's inventory scan cooldown.
        if (now - server.getData(TTAttachments.SLOT_SCAN_TIME) < ServerboundScanSlotPayload.CHANNEL_TICKS - 5) {
            return;
        }
        server.setData(TTAttachments.SLOT_SCAN_TIME, now);

        // A recipe-only entry or spoofed key cannot be scanned. Simulating also respects storage filters.
        if (menu.getHost().getInventory().extract(item, 1, Actionable.SIMULATE, menu.getActionSource()) > 0) {
            ScanningManager.scan(server, ScanTarget.stack(item.toStack()));
        }
    }
}
