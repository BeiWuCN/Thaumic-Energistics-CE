# `src/main/java/thaumicenergistics_ce/part/PartEssentiaTerminal.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 21 行

````java
/**
 * 作为线缆部件的源质终端：一台只列源质的 AE2 终端。
 * 过滤用 {@link KeyTypeSelection}，即 AE2 自己给终端键类型的通道；父类的选择允许所有类型，
 * 我们这份替换它，字段就由手工读写。父类的字段是私有的，它的读/写会把宽松选择存下来。
 */
````
