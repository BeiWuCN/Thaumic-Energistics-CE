package thaumicenergistics_ce.mixin;

import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.implementations.PatternProviderMenu;
import appeng.menu.slot.AppEngSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import thaumicenergistics_ce.integration.ae2.patternprovider.EssentiaPatternProvider;
import thaumicenergistics_ce.registry.TECEItems;

@Mixin(value = AppEngSlot.class, remap = false)
public abstract class PatternProviderCardSlotMixin implements thaumicenergistics_ce.client.gui.ArcaneUpgradeSlotBinding {
    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final @org.spongepowered.asm.mixin.Mutable
    private appeng.api.inventories.InternalInventory inventory;

    @Override
    public void bindSharedInventory(appeng.api.inventories.InternalInventory shared) { inventory = shared; }

    @org.spongepowered.asm.mixin.Shadow
    protected abstract appeng.menu.AEBaseMenu getMenu();
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void tce$dedicatedSlot(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        var slot = (AppEngSlot) (Object) this;
        if (getMenu() instanceof thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal sharedMenu
                && sharedMenu.usesSharedUpgrades()
                && sharedMenu.getSlots(appeng.menu.SlotSemantics.UPGRADE).contains(slot)
                && (stack.is(TECEItems.ESSENTIA_ACCESS_CARD.get()) || stack.is(TECEItems.VIS_CONNECTION_CARD.get()))
                && !(slot.getInventory() instanceof thaumicenergistics_ce.item.SharedArcaneUpgradeInventory)) {
            cir.setReturnValue(false);
            return;
        }
        if (!stack.is(TECEItems.ESSENTIA_ACCESS_CARD.get())) return;
        if (getMenu() instanceof thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal arcane
                && arcane.getSlots(appeng.menu.SlotSemantics.UPGRADE).contains(slot)
                && !thaumicenergistics_ce.config.ThEConfig.ARCANE_ESSENTIA_ACCESS.get()) {
            cir.setReturnValue(false);
            return;
        }
        if (getMenu() instanceof PatternProviderMenu menu
                && menu.getSlots(appeng.menu.SlotSemantics.UPGRADE).contains(slot)
                && menu.getTarget() instanceof PatternProviderLogicHost host
                && slot.getInventory() != ((EssentiaPatternProvider) host.getLogic()).essentiaCardInventory()) {
            cir.setReturnValue(false);
        }
    }
}
