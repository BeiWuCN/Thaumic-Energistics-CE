# `src/main/java/thaumicenergistics_ce/arcane/SyntheticArcaneInput.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 15 行

````java
/**
 * 给机器用、也给脱离工作台校验配方的最小 {@link IArcaneCraftingInput}。
 * 槽序同 {@code InventoryArcaneWorkbench}：先九格网格，再六格晶体，最后法杖槽。
 * 这里永远为空，机器用 vis 付账，不用法杖。
 */
````

## 原第 57 行

````java
/**
     * 由样板的网格加要在晶体槽里露出的晶体堆构建输入。
     * @param crystals 最多六个物品堆，多的条目忽略
     */
````
