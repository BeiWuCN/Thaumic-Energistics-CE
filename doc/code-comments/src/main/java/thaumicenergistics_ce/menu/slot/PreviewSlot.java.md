# `src/main/java/thaumicenergistics_ce/menu/slot/PreviewSlot.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 8 行

````java
/**
 * 只显示物品、别的什么都不做的槽位：不能取走、不能放置、悬停也不高亮。
 * 奥术组装机的预览用它：该方块不在更新标签里带物品数据，槽位同步是服务端把物品送到客户端的唯一途径。
 * {@code isHighlightable} 也是 false，亮起来的预览槽位看着像能往里放东西。
 */
````
