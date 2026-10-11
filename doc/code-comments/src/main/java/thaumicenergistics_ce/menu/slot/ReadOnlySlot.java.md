# `src/main/java/thaumicenergistics_ce/menu/slot/ReadOnlySlot.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 9 行

````java
/**
 * 仅供显示的槽位：渲染真实的物品堆，拒绝一切交互。
 * 它展示玩家不得取走、插入或被模组排序的真实机器状态。
 * 一个空的零槽位容器会对自动化隐藏 {@code slot.index} 与 {@code slot.container}。
 * 每个会改动状态的入口点都是空操作，{@link #getItem()} 读取真实来源。
 */
````
