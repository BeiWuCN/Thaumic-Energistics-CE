# `src/main/java/thaumicenergistics_ce/network/PartitionWellPayload.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 11 行

````java
/**
 * 「把这个要素放进那个分区槽位」，玩家从 JEI 拖出一个时由屏幕发出。
 * 槽位写入行不通：{@code FakeSlot.set} 止步于它所在的那个屏幕。
 * AE2 的 {@code InventoryActionPacket} 会丢弃任何不是 {@code AEBaseMenu} 的菜单。
 * 要素以 id 上路，接收者才解析得出是哪一个，以及它为何被丢弃。
 */
````

## 原第 33 行

````java
/** 表示「把标记从槽位取出」的 id，与总线上一致：键没法搭在光标上。 */
````
