package thaumicenergistics_ce.mixin;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.core.network.clientbound.MEInventoryUpdatePacket;
import appeng.menu.me.common.IncrementalUpdateHelper;
import appeng.menu.me.common.MEStorageMenu;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.menu.EssentiaVisibility;

@Mixin(value = MEStorageMenu.class, remap = false)
public abstract class TerminalVisibilityMixin {
    @Shadow @Final private IncrementalUpdateHelper updateHelper;
    @Shadow @Final protected MEStorage storage;
    @Shadow protected abstract Set<AEKey> getCraftablesFromGrid();
    @Unique private Boolean thaumicenergistics$lastServerVisibility;

    @Inject(method = "broadcastChanges", at = @At("RETURN"))
    private void refreshEssentiaInventory(CallbackInfo callback) {
        var menu = (MEStorageMenu) (Object) this;
        if (menu.isClientSide()) {
            return;
        }
        boolean visible = EssentiaVisibility.allowed(menu);
        if (thaumicenergistics$lastServerVisibility != null
                && thaumicenergistics$lastServerVisibility != visible) {
            updateHelper.clear();
            var builder = MEInventoryUpdatePacket.builder(menu.containerId, true, menu.getPlayer().registryAccess());
            builder.setFilter(menu::isKeyVisible);
            builder.addFull(updateHelper, storage.getAvailableStacks(), getCraftablesFromGrid(), new KeyCounter());
            var packets = builder.build();
            if (packets.isEmpty()) {
                ((ServerPlayer) menu.getPlayer()).connection.send(
                        new MEInventoryUpdatePacket(true, menu.containerId, List.of(), 0, null));
            } else {
                packets.forEach(((ServerPlayer) menu.getPlayer()).connection::send);
            }
            updateHelper.commitChanges();
        }
        thaumicenergistics$lastServerVisibility = visible;
    }

    @Inject(method = "isKeyVisible", at = @At("HEAD"), cancellable = true)
    private void filterEssentia(AEKey key, CallbackInfoReturnable<Boolean> cir) {
        if (key instanceof AEssentiaKey && !EssentiaVisibility.allowed((MEStorageMenu) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "handleNetworkInteraction", at = @At("HEAD"), cancellable = true)
    private void tce$denyHidden(ServerPlayer player, AEKey key, appeng.helpers.InventoryAction action,
            CallbackInfo ci) {
        if (key instanceof AEssentiaKey && !EssentiaVisibility.allowed((MEStorageMenu) (Object) this)) ci.cancel();
    }
}
