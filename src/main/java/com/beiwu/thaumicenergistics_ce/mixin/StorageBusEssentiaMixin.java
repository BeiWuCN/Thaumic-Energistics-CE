package com.beiwu.thaumicenergistics_ce.mixin;

import appeng.api.storage.MEStorage;
import appeng.parts.storagebus.StorageBusPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import com.beiwu.thaumicenergistics_ce.integration.ae2.bus.EssentiaBusStorage;

/** 与 {@link StorageBusUpgradeSlotsMixin} 的 {@code tce$gateMountedStorage} 逐字节重复，故意未登记：
 *  两个都登记会对同一次 mount 包两层 {@link EssentiaBusStorage}。 */
@Mixin(value = StorageBusPart.class, remap = false)
public abstract class StorageBusEssentiaMixin {
    @ModifyArg(method = "mountInventories", at = @At(value = "INVOKE",
            target = "Lappeng/api/storage/IStorageMounts;mount(Lappeng/api/storage/MEStorage;I)V"), index = 0)
    private MEStorage tce$gateMountedStorage(MEStorage storage) {
        return new EssentiaBusStorage(storage, (StorageBusPart) (Object) this);
    }
}
