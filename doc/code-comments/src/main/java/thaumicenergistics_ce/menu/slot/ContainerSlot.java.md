# `src/main/java/thaumicenergistics_ce/menu/slot/ContainerSlot.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 3 行

````java
/**
 * 玩家正使用的源质容器在哪，供得告诉服务端是哪一个的终端使用。
 * 光标上的物品堆与主手是两个不同的位置，两者都不是菜单槽位。
 * 槽位 id 是 {@code Slot.index}，不是 {@code getSlotIndex()}：
 * 后者在槽位自己的容器内计数，AE2 的槽位一加入列表指的就是另一个槽位。
 */
````
