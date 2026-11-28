package com.beiwu.thaumicenergistics_ce.mixin;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.beiwu.thaumicenergistics_ce.integration.ae2.patternprovider.*;
import com.beiwu.thaumicenergistics_ce.integration.ae2.patternprovider.EssentiaReturnGate;

@Pseudo
@Mixin(targets = "com.moakiee.ae2lt.menu.PigmeePatternProviderMenu", remap = false)
public abstract class PigmeeProviderMenuEssentiaMixin extends AEBaseMenu implements EssentiaProviderMenuState {
    @appeng.menu.guisync.GuiSync(11745) public boolean tce$essentiaSupportEnabled;
    @Unique private net.minecraft.world.inventory.Slot tce$essentiaSlot;
    protected PigmeeProviderMenuEssentiaMixin(MenuType<?> type, int id, Inventory inv, Object host) {
        super(type, id, inv, host);
    }
    @Override public boolean essentiaSupportEnabled() { return tce$essentiaSupportEnabled; }
    @Override public void broadcastChanges() {
        if (!isClientSide()) {
            var provider = PatternProviderAddonCompat.provider(getTarget());
            tce$essentiaSupportEnabled = provider != null && provider.essentiaSupportEnabled();
        }
        super.broadcastChanges();
    }
    @Override protected boolean isValidQuickMoveDestination(net.minecraft.world.inventory.Slot slot,
            net.minecraft.world.item.ItemStack stack, boolean fromPlayerSide) {
        if (fromPlayerSide && stack.is(com.beiwu.thaumicenergistics_ce.registry.TECEItems.ESSENTIA_ACCESS_CARD.get())
                && slot != tce$essentiaSlot) return false;
        return super.isValidQuickMoveDestination(slot, stack, fromPlayerSide);
    }
    @Inject(method = "<init>", at = @At("TAIL"))
    private void tce$slot(CallbackInfo ci) {
        var provider = PatternProviderAddonCompat.provider(getTarget());
        if (provider == null) {
            // 宿主不是 Pigmee 时两端都不加槽，索引才不会错位。
            return;
        }
        if (!isClientSide()) tce$essentiaSupportEnabled = provider.essentiaSupportEnabled();
        var slot = new AppEngSlot(provider.essentiaCardInventory(), 0);
        slot.setIcon(appeng.client.gui.Icon.BACKGROUND_UPGRADE);
        tce$essentiaSlot = addSlot(slot, SlotSemantics.UPGRADE);
    }
}
