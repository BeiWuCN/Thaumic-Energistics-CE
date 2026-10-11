# `src/main/java/thaumicenergistics_ce/mixin/InterfaceLogicDropMixin.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 19 行

````java
/**
 * AE2 拆除机器时把存储行每个键都过一遍 [AEKey.addDrops]，
 * 要素没有能掉在地上的物品形态，那一处是有意留空的，源质就没了。
 * 方块形态与线缆部件拆除时都经过它（[InterfaceBlockEntity] / [InterfacePart]），
 * 所以只在这一件事上插一手：把要丢的要素交还网格。
 * 接口怎么按标记补货，一个字都不碰。
 */
````
