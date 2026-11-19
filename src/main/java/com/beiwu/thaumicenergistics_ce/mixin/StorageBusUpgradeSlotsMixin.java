package com.beiwu.thaumicenergistics_ce.mixin;

import appeng.parts.storagebus.StorageBusPart;
import appeng.api.storage.MEStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Unique;
import appeng.parts.automation.UpgradeablePart;
import appeng.api.parts.IPartItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.HolderLookup;

import java.util.List;
import com.beiwu.thaumicenergistics_ce.integration.ae2.bus.EssentiaBusStorage;

@Mixin(value = StorageBusPart.class, remap = false)
public abstract class StorageBusUpgradeSlotsMixin extends UpgradeablePart {
    @Unique private ItemStack tce$legacyUpgrade = ItemStack.EMPTY;

    protected StorageBusUpgradeSlotsMixin(IPartItem<?> item) { super(item); }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void tce$readLegacyUpgrade(CompoundTag input, HolderLookup.Provider registries, CallbackInfo callback) {
        tce$legacyUpgrade = ItemStack.parseOptional(registries, input.getCompound("tceLegacyUpgrade"));
        if (tce$legacyUpgrade.isEmpty()) {
            for (var value : input.getList("upgrades", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) value;
                if (entry.getInt("Slot") == 5) {
                    tce$legacyUpgrade = ItemStack.parseOptional(registries, entry);
                    break;
                }
            }
        }
        tce$restoreLegacyUpgrade();
    }
    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void tce$writeLegacyUpgrade(CompoundTag output, HolderLookup.Provider registries, CallbackInfo callback) {
        if (!tce$legacyUpgrade.isEmpty()) output.put("tceLegacyUpgrade", tce$legacyUpgrade.save(registries));
    }

    @Inject(method = "upgradesChanged", at = @At("TAIL"))
    private void tce$restoreAfterRemoval(CallbackInfo callback) { tce$restoreLegacyUpgrade(); }

    @Unique
    private void tce$restoreLegacyUpgrade() {
        if (tce$legacyUpgrade == null || tce$legacyUpgrade.isEmpty()) { return; }
        ItemStack pending = tce$legacyUpgrade;
        int originalCount = pending.getCount();
        tce$legacyUpgrade = ItemStack.EMPTY;
        for (int slot = 0; slot < getUpgrades().size() && !pending.isEmpty(); slot++) {
            pending = getUpgrades().insertItem(slot, pending, false);
        }
        tce$legacyUpgrade = pending;
        if (pending.getCount() < originalCount) { getUpgrades().sendChangeNotification(0); }
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        super.addAdditionalDrops(drops, wrenched);
        if (!tce$legacyUpgrade.isEmpty()) { drops.add(tce$legacyUpgrade.copy()); }
    }

    @Override
    public void clearContent() {
        tce$legacyUpgrade = ItemStack.EMPTY;
        super.clearContent();
    }

    @ModifyArg(method = "mountInventories", at = @At(value = "INVOKE",
            target = "Lappeng/api/storage/IStorageMounts;mount(Lappeng/api/storage/MEStorage;I)V"), index = 0)
    private MEStorage tce$gateMountedStorage(MEStorage inventory) {
        return new EssentiaBusStorage(inventory, (StorageBusPart) (Object) this);
    }
}
