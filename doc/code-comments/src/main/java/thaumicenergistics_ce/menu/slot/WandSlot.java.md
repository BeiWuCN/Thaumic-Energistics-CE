# `src/main/java/thaumicenergistics_ce/menu/slot/WandSlot.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 8 行

````java
/**
 * 奥术合成终端的法杖槽，只收法杖。规则就是{@code PartArcaneCraftingTerminal} 给法杖容器
 * 装的那条过滤器（{@code TcWand.isWand}）：按物品类认，不认标签。
 *
 * <p>两处规则必须一致。容器上的过滤器管的是「进容器」，玩家点击走的是这里的
 * {@code mayPlace}；只有过滤器没有 {@code mayPlace} 时，槽位会先把东西收下、
 * 再由容器把它退掉，看起来就是「什么都能放上去」。
 */
````
