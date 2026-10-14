package thaumicenergistics_ce.mixin;

import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;

import appeng.menu.slot.AppEngSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import thaumicenergistics_ce.integration.ae2.patternprovider.EssentiaPatternProvider;

@org.spongepowered.asm.mixin.Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.gui.advpatternprovider.AdvPatternProviderMenu", priority = 900, remap = false)
public abstract class AdvancedProviderMenuEssentiaMixin extends AEBaseMenu
        implements thaumicenergistics_ce.integration.ae2.patternprovider.EssentiaProviderMenuState {
    @appeng.menu.guisync.GuiSync(11745)
    public boolean tce$essentiaSupportEnabled;

    @Override
    public boolean essentiaSupportEnabled() { return tce$essentiaSupportEnabled; }

    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void tce$syncSupport(CallbackInfo ci) {
        if (!isClientSide() && getTarget() instanceof AdvPatternProviderLogicHost host) {
            tce$essentiaSupportEnabled = ((EssentiaPatternProvider) host.getLogic()).essentiaSupportEnabled();
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private net.minecraft.world.inventory.Slot tce$essentiaSlot;
    protected AdvancedProviderMenuEssentiaMixin(MenuType<?> type, int id, Inventory inv, Object host) {
        super(type, id, inv, host);
    }

    @Override
    protected boolean isValidQuickMoveDestination(net.minecraft.world.inventory.Slot slot,
            net.minecraft.world.item.ItemStack stack, boolean fromPlayerSide) {
        if (fromPlayerSide && stack.is(thaumicenergistics_ce.registry.TECEItems.ESSENTIA_ACCESS_CARD.get())
                && slot != tce$essentiaSlot) {
            return false;
        }
        return super.isValidQuickMoveDestination(slot, stack, fromPlayerSide);
    }

    @Inject(method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lnet/pedroksl/advanced_ae/common/logic/AdvPatternProviderLogicHost;)V", at = @At("TAIL"))
    private void tce$slot(MenuType<?> type, int id, Inventory inv, AdvPatternProviderLogicHost host, CallbackInfo ci) {
        if (!isClientSide()) {
            tce$essentiaSupportEnabled = ((EssentiaPatternProvider) host.getLogic()).essentiaSupportEnabled();
        }
        var slot = new AppEngSlot(((EssentiaPatternProvider) host.getLogic()).essentiaCardInventory(), 0);
        slot.setIcon(appeng.util.Icon.BACKGROUND_UPGRADE);
        tce$essentiaSlot = addSlot(slot, SlotSemantics.UPGRADE);
    }
}
