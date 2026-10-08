package thaumicenergistics_ce.integration.ae2;

import appeng.api.upgrades.IUpgradeableObject;

/**
 * 菜单与界面之间递一下升级对象。界面上那个 {@code menu} 的 {@code logic} 是 protected，跨包读不到；
 * {@code getTarget()} 返回的是方块实体，部件那边拿不到，所以由菜单侧的 mixin 实现本接口把它递出来。
 * 不能放在 {@code thaumicenergistics_ce.mixin} 包里：那个包被 mixins.json 整个认作 mixin 所有，
 * 在别处引用会报 IllegalClassLoadError。
 */
public interface PatternProviderUpgrades {

    IUpgradeableObject tce$upgradeHost();
}
