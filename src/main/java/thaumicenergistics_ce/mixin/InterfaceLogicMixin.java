package thaumicenergistics_ce.mixin;

import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.helpers.InterfaceLogic;
import appeng.util.ConfigInventory;
import java.util.List;
import net.minecraft.world.item.ItemStack;
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
import thaumicenergistics_ce.interfaceaccess.EssentiaInterfaceAccess;

/**
 * Keeps AE2's own essentia traffic out of an interface that carries the access card: the card moves
 * essentia the other way, so the plan for a marked aspect is dropped and the row refuses aspects.
 * <ul>
 *   <li>Two more hooks undo the card's work when it is pulled, and save the aspects when one is broken.
 *   <li>The rest live behind the card, so an interface without one behaves exactly as AE2 wrote it.
 * </ul>
 */
@Mixin(InterfaceLogic.class)
public abstract class InterfaceLogicMixin {

    @Shadow
    @Final
    private IUpgradeInventory upgrades;

    @Shadow
    @Final
    private GenericStack[] plannedWork;

    @Shadow
    @Final
    private ConfigInventory config;

    @Shadow
    @Final
    private ConfigInventory storage;

    @Shadow
    private MEStorage networkStorage;

    @Shadow
    public abstract IGridNode getActionableNode();

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

    /**
     * The card out is its work undone: the marks go with it and the storage row goes back to the grid.
     * Only an interface that is holding a mark of ours is touched, and marks only go in behind the card.
     */
    @Inject(method = "onUpgradesChanged()V", at = @At("RETURN"))
    private void tce$releaseRowsWithoutCard(CallbackInfo callback) {
        if (!tce$hasAccessCard() && EssentiaInterfaceAccess.holdsEssentia(config)) {
            EssentiaInterfaceAccess.releaseRows(config, storage, networkStorage, tce$source());
        }
    }

    /** AE2 turns each key of the storage row into a drop, and an aspect has no item to be dropped as. */
    @Inject(method = "addDrops(Ljava/util/List;)V", at = @At("HEAD"))
    private void tce$rescueEssentiaFromDrops(List<ItemStack> drops, CallbackInfo callback) {
        EssentiaInterfaceAccess.rescueEssentia(storage, networkStorage, tce$source());
    }

    @Unique
    private IActionSource tce$source() {
        return IActionSource.ofMachine(this::getActionableNode);
    }

    @Unique
    private boolean tce$hasAccessCard() {
        return upgrades.isInstalled(ModItems.ESSENTIA_ACCESS_CARD.get());
    }
}
