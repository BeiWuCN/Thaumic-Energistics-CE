package thaumicenergistics_ce.mixin;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.helpers.InterfaceLogic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;

/**
 * Keeps AE2's own essentia traffic out of an interface that carries the access card: the card moves
 * essentia the other way, so the plan for a marked aspect is dropped and the row refuses aspects.
 * Both hooks live behind the card, so an interface without one behaves exactly as AE2 wrote it.
 */
@Mixin(InterfaceLogic.class)
public abstract class InterfaceLogicMixin {

    @Shadow
    @Final
    private IUpgradeInventory upgrades;

    @Shadow
    @Final
    private GenericStack[] plannedWork;

    /** Unmarked slots would otherwise swallow any aspect, and a terminal hands out what a row holds. */
    @Inject(
            method = "isAllowedInStorageSlot(ILappeng/api/stacks/AEKey;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void tce$refuseEssentiaInRow(int slot, AEKey key, CallbackInfoReturnable<Boolean> callback) {
        if (key instanceof AEssentiaKey && tce$hasAccessCard()) {
            callback.setReturnValue(false);
        }
    }

    /** A marked aspect would otherwise be pulled out of the grid into the row, and the row's stock out. */
    @Inject(method = "updatePlan(I)V", at = @At("RETURN"))
    private void tce$dropEssentiaPlan(int slot, CallbackInfo callback) {
        GenericStack planned = plannedWork[slot];
        if (planned != null && planned.what() instanceof AEssentiaKey && tce$hasAccessCard()) {
            plannedWork[slot] = null;
        }
    }

    @Unique
    private boolean tce$hasAccessCard() {
        return upgrades.isInstalled(ModItems.ESSENTIA_ACCESS_CARD.get());
    }
}
