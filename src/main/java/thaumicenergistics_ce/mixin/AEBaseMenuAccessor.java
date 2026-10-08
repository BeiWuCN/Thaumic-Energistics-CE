package thaumicenergistics_ce.mixin;

import appeng.api.upgrades.IUpgradeInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 转发 AE2 建升级槽那一步。开在 {@link appeng.menu.AEBaseMenu} 上而不是开在子类上：
 * [Invoker] 只在本类里找方法，子类那边会报 "No candidates were found"，而建槽逻辑正是写在基类。
 */
@Mixin(appeng.menu.AEBaseMenu.class)
public interface AEBaseMenuAccessor {

    @Invoker("setupUpgrades")
    void tce$setupUpgrades(IUpgradeInventory upgrades);
}
