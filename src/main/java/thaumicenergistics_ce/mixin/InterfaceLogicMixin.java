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
import thaumicenergistics_ce.interfaceaccess.EssentiaInterfaceRows;

/**
 * 阻止 AE2 自己的源质流量进入携带访问卡的接口：卡片是往相反方向搬运源质的，
 * 所以已标记要素的计划会被丢弃，该行也拒绝要素。另有两只钩子在卡片被拔出时撤销它的
 * 作用，并在某个要素被破坏时保存要素；其余部分都藏在卡片之后，因此没有卡片的接口
 * 行为与 AE2 写的一模一样。
 * AE2 写的一模一样。
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

    /** 未标记的槽位否则会吞下任何要素，而终端会分发标记行所持有的东西。 */
    @Inject(
            method = "isAllowedInStorageSlot(ILappeng/api/stacks/AEKey;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void tce$refuseEssentiaInRow(int slot, AEKey key, CallbackInfoReturnable<Boolean> callback) {
        if (key instanceof AEssentiaKey && tce$hasAccessCard()) {
            callback.setReturnValue(false);
        }
    }

    /** 已标记的要素否则会被从网格拉进该行，而该行的存货又被拉出去。 */
    @Inject(method = "updatePlan(I)V", at = @At("RETURN"))
    private void tce$dropEssentiaPlan(int slot, CallbackInfo callback) {
        GenericStack planned = plannedWork[slot];
        if (planned != null && planned.what() instanceof AEssentiaKey && tce$hasAccessCard()) {
            plannedWork[slot] = null;
        }
    }

    /**
     * 卡片拔出即其作用被撤销：标记随之而去，存储行回归网格。
     * 只触碰正持有我方标记的接口，而标记也只在卡片就位后才会写入。
     */
    @Inject(method = "onUpgradesChanged()V", at = @At("RETURN"))
    private void tce$releaseRowsWithoutCard(CallbackInfo callback) {
        if (!tce$hasAccessCard() && EssentiaInterfaceRows.holdsEssentia(config)) {
            EssentiaInterfaceRows.releaseRows(config, storage, networkStorage, tce$source());
        }
    }

    /** AE2 会把存储行的每个键变成掉落物，而要素没有可掉落成的物品。 */
    @Inject(method = "addDrops(Ljava/util/List;)V", at = @At("HEAD"))
    private void tce$rescueEssentiaFromDrops(List<ItemStack> drops, CallbackInfo callback) {
        EssentiaInterfaceRows.rescueEssentia(storage, networkStorage, tce$source());
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
