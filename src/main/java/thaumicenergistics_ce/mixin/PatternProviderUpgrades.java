package thaumicenergistics_ce.mixin;

import appeng.api.upgrades.IUpgradeableObject;

/**
 * 菜单与界面之间递一下升级对象。
 * 界面上那个 {@code menu} 的 {@code logic} 是 protected，跨包读不到；{@code getTarget()} 返回的是方块实体，部件那边拿不到。
 */
public interface PatternProviderUpgrades {

    IUpgradeableObject tce$upgradeHost();
}
