package com.beiwu.thaumicenergistics_ce.recipe.arcane;

/**
 * 奥术合成终端付费源模式的持久化落点。菜单的 [crystalFirst] 只是 GUI 同步字段，
 * 关掉菜单就没了；真正的值存在宿主身上：放置式存部件 NBT，无线存终端物品的数据组件。
 * 三种宿主（部件、独立无线、AE2WTLib 无线）都实现它，菜单只认这个接口。
 */
public interface ArcanePaymentPreference {

    /** true = 先用水晶槽的整颗晶体；false = 让法杖 6 要素按 2:1 顶。 */
    boolean crystalFirst();

    /** 翻转并持久化；菜单的 GUI 同步字段随后跟上。 */
    void setCrystalFirst(boolean value);
}
