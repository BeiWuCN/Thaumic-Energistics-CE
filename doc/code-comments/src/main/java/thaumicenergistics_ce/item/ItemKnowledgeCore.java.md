# `src/main/java/thaumicenergistics_ce/item/ItemKnowledgeCore.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 16 行

````java
/**
 * 知识核心：奥术组装机能执行的奥术配方的可携带清单。
 * 物品本身是普通的 {@link Item}，内容存在由 {@link HandlerKnowledgeCore} 写入的自定义数据里，
 * 这个类只在 tooltip 中报告它们。
 */
````

## 原第 35 行

````java
// 26.1 里 TooltipContext 没了 level()：核心需要的本来就是注册表。
````
