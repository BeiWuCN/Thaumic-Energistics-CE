# `src/main/java/thaumicenergistics_ce/blockentity/assembler/AssemblerGridTick.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 8 行

````java
/**
 * 在 AE2 网格 tick 中更新样板缓存、补充 vis，并推进正在执行的合成。
 */
````

## 原第 21 行

````java
// 读取成功才结算；还没有 level 的重建会重试，见 refresh()。
````
