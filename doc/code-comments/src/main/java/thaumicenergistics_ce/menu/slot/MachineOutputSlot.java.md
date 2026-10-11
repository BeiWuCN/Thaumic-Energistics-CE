# `src/main/java/thaumicenergistics_ce/menu/slot/MachineOutputSlot.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 8 行

````java
/**
 * 机器写入、玩家取空的槽位：蒸馏编码器写出的样板。拒绝放置，允许取走，
 * 只有 {@code encode()} 会写这个样板。
 * 它不是 {@code ReadOnlySlot}：空操作的 {@code set} 会吞掉客户端的
 * {@code AbstractContainerMenu.setItem} 写入，样板永远到不了屏幕。
 */
````
