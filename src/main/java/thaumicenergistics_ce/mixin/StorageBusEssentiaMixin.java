package thaumicenergistics_ce.mixin;

import appeng.api.storage.MEStorage;
import appeng.parts.storagebus.StorageBusPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import thaumicenergistics_ce.integration.ae2.EssentiaBusStorage;

@Mixin(value = StorageBusPart.class, remap = false)
public abstract class StorageBusEssentiaMixin {
    @ModifyArg(method = "mountInventories", at = @At(value = "INVOKE",
            target = "Lappeng/api/storage/IStorageMounts;mount(Lappeng/api/storage/MEStorage;I)V"), index = 0)
    private MEStorage tce$gateMountedStorage(MEStorage storage) {
        return new EssentiaBusStorage(storage, (StorageBusPart) (Object) this);
    }
}
