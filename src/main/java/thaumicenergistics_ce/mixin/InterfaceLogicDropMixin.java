package thaumicenergistics_ce.mixin;

import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
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
import thaumicenergistics_ce.interfaceaccess.EssentiaInterfaceRows;

/**
 * AE2 拆除机器时把存储行每个键都过一遍 [AEKey.addDrops]，
 * 要素没有能掉在地上的物品形态，那一处是有意留空的，源质就没了。
 * 方块形态与线缆部件拆除时都经过它（[InterfaceBlockEntity] / [InterfacePart]），
 * 所以只在这一件事上插一手：把要丢的要素交还网格。
 * 接口怎么按标记补货，一个字都不碰。
 */
@Mixin(InterfaceLogic.class)
public abstract class InterfaceLogicDropMixin {

    @Shadow
    @Final
    private ConfigInventory storage;

    @Shadow
    private MEStorage networkStorage;

    @Shadow
    public abstract IGridNode getActionableNode();

    @Inject(method = "addDrops(Ljava/util/List;)V", at = @At("HEAD"))
    private void tce$rescueEssentiaFromDrops(List<ItemStack> drops, CallbackInfo callback) {
        EssentiaInterfaceRows.rescueEssentia(storage, networkStorage, tce$source());
    }

    @Unique
    private IActionSource tce$source() {
        return IActionSource.ofMachine(this::getActionableNode);
    }
}
