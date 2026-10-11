# `src/main/java/thaumicenergistics_ce/essentia/ThEImmediateStorage.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 6 行

````java
/**
 * 必须直接问它会做什么改动的存储，因为它不认识事务：管道的一面，
 * Thaumaturge 的管道 API 在被问的那一刻就应用改动；还有通往提供器网格的
 * 无线连接，它的电耗在任何日志之外。
 *
 * <p>所以 {@link thaumicenergistics_ce.util.ThETransaction#preview} 落在这种存储上，会正好留下
 * 它本该避免的那个改动；只想问一声的调用方，必须改调
 * 这些方法。
 */
````

## 原第 17 行

````java
/** 插入 {@code amount} 会收下多少，问的时候什么都不改。 */
````

## 原第 20 行

````java
/** 抽取 {@code amount} 会给出多少，问的时候什么都不改。 */
````
