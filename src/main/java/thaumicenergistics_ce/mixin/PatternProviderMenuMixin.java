package thaumicenergistics_ce.mixin;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.implementations.PatternProviderMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把升级槽加进样板供应器的菜单。建槽直接用 AE2 自己的 [setupUpgrades]：
 * 它是 {@code protected final}，走 {@link Invoker} 转发，不必照抄一遍槽位类型与 UPGRADE 语义。
 */
@Mixin(PatternProviderMenu.class)
public abstract class PatternProviderMenuMixin implements PatternProviderUpgrades {

    @Shadow
    @Final
    protected PatternProviderLogic logic;

    @Invoker("setupUpgrades")
    protected abstract void tce$setupUpgrades(IUpgradeInventory upgrades);

    @Inject(
            method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;"
                    + "Lappeng/helpers/patternprovider/PatternProviderLogicHost;)V",
            at = @At("TAIL"))
    private void tce$addUpgradeSlots(
            MenuType<?> type,
            int id,
            Inventory playerInventory,
            PatternProviderLogicHost host,
            CallbackInfo callback) {
        tce$setupUpgrades(((IUpgradeableObject) (Object) logic).getUpgrades());
    }

    @Override
    public IUpgradeableObject tce$upgradeHost() {
        return (IUpgradeableObject) (Object) logic;
    }
}
