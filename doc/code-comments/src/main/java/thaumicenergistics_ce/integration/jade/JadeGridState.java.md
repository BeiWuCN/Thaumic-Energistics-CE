# `src/main/java/thaumicenergistics_ce/integration/jade/JadeGridState.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 9 行

````java
/**
 * AE2 显示的四种网格节点状态，以及 tooltip 如何绘制它们。
 * 取值、文字与颜色都取自 AE2，翻译键也用它自己的，每台机器共用同一套。
 * 状态在服务端解析（节点在那边），再以 ordinal 发给客户端。
 * 枚举公开是为了 [client.jade] 的 tooltip 那一半，写入侧仍是包级私有。
 */
````

## 原第 52 行

````java
// 做了边界检查：越界的 ordinal 从对面传来会让客户端崩溃。
````
