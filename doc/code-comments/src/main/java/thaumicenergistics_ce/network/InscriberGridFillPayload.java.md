# `src/main/java/thaumicenergistics_ce/network/InscriberGridFillPayload.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 13 行

````java
/**
 * 知识铭刻机整个 3x3 网格，由客户端一次写入：
 * 逐单元更新看得见，还会让机器把网格重复求解九遍。
 * 客户端先把同样的九个物品堆应用到自己的容器副本上，使两侧一致。
 * {@code cells} 按阅读顺序恰好九个物品堆，更短的列表用空物品堆补齐。
 */
````
