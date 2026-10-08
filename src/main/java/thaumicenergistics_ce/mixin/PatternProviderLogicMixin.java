package thaumicenergistics_ce.mixin;

import appeng.api.networking.IManagedGridNode;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 给样板供应器装上升级物品栏：AE2 只给接口留了槽，供应器一个都没有，源质访问卡因此没处插。
 * 挂在 logic 上而不是两个宿主上，方块与部件共用一份实现，存取也只需一对钩子。
 * 两个宿主的 [writeToNBT]／[readFromNBT] 本来就会调到 logic 的同名方法，所以份量就是这么点。
 */
@Mixin(PatternProviderLogic.class)
public abstract class PatternProviderLogicMixin implements IUpgradeableObject {

    @Unique
    private IUpgradeInventory tce$upgrades = UpgradeInventories.empty();

    /**
     * 认机器物品只看宿主是方块还是部件，不问 {@code getTerminalIcon()}：
     * 部件那个走的是实例方法，构造期字段还没落地。
     */
    @Inject(
            method = "<init>(Lappeng/api/networking/IManagedGridNode;"
                    + "Lappeng/helpers/patternprovider/PatternProviderLogicHost;I)V",
            at = @At("TAIL"))
    private void tce$installUpgrades(
            IManagedGridNode node,
            PatternProviderLogicHost host,
            int patternSlots,
            CallbackInfo callback) {
        ItemLike machine = host instanceof BlockEntity ? AEBlocks.PATTERN_PROVIDER : AEParts.PATTERN_PROVIDER;
        tce$upgrades = UpgradeInventories.forMachine(
                machine, 1, ((PatternProviderLogic) (Object) this)::saveChanges);
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return tce$upgrades;
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void tce$writeUpgrades(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo callback) {
        tce$upgrades.writeToNBT(tag, "upgrades", registries);
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void tce$readUpgrades(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo callback) {
        tce$upgrades.readFromNBT(tag, "upgrades", registries);
    }
}
