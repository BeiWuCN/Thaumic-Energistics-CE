package thaumicenergistics_ce.mixin;

import appeng.api.upgrades.IUpgradeableObject;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.implementations.PatternProviderMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import thaumicenergistics_ce.integration.ae2.PatternProviderUpgrades;

/**
 * 把升级槽加进样板供应器的菜单，槽位类型与语义都交给 {@link AEBaseMenuAccessor} 转发的 AE2 那一步。
 * {@code logic} 是它本类自己声明的字段，{@code @Shadow} 拿得到；基类的成员不能这样取，所以走访问器。
 */
@Mixin(PatternProviderMenu.class)
public abstract class PatternProviderMenuMixin implements PatternProviderUpgrades {

    @Shadow
    @Final
    protected PatternProviderLogic logic;

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
        ((AEBaseMenuAccessor) (Object) this)
                .tce$setupUpgrades(((IUpgradeableObject) (Object) logic).getUpgrades());
    }

    @Override
    public IUpgradeableObject tce$upgradeHost() {
        return (IUpgradeableObject) (Object) logic;
    }
}
