package thaumicenergistics_ce.mixin;

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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import java.util.List;
import thaumicenergistics_ce.integration.ae2.bus.EssentiaBusStorage;

@Mixin(value = StorageBusPart.class, remap = false)
public abstract class StorageBusUpgradeSlotsMixin extends UpgradeablePart {
    @Unique private ItemStack tce$legacyUpgrade = ItemStack.EMPTY;

    protected StorageBusUpgradeSlotsMixin(IPartItem<?> item) { super(item); }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void tce$readLegacyUpgrade(ValueInput input, CallbackInfo callback) {
        tce$legacyUpgrade = input.read("tceLegacyUpgrade", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        if (tce$legacyUpgrade.isEmpty()) {
            for (var entry : input.childrenListOrEmpty("upgrades")) {
                if (entry.getIntOr("Slot", 0) == 5) {
                    tce$legacyUpgrade = entry.read(ItemStack.MAP_CODEC).orElse(ItemStack.EMPTY);
                    break;
                }
            }
        }
        tce$restoreLegacyUpgrade();
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void tce$writeLegacyUpgrade(ValueOutput output, CallbackInfo callback) {
        if (!tce$legacyUpgrade.isEmpty()) {
            output.store("tceLegacyUpgrade", ItemStack.CODEC, tce$legacyUpgrade);
        }
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
