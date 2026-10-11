# `src/main/java/thaumicenergistics_ce/network/GolemBackpackPayload.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 8 行

````java
/**
 * 傀儡背包长什么样，告知正在看它的玩家。
 * 背包存在傀儡的持久数据里，原版从不同步，按定时器发送而非随变化发送。
 * @param entityId 该傀儡
 * @param status {@link #STATUS_NO_BACKPACK}、{@link #STATUS_IN_RANGE} 或 {@link #STATUS_OUT_OF_RANGE}
 * @param skinOrdinal 该皮肤在 {@code BackpackSkins} 中的序号
 */
````
