# `src/main/java/thaumicenergistics_ce/integration/jade/AlchemyReceiverProvider.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 11 行

````java
/**
 * 炼金接收器的 Jade 服务端数据：它绑到的供应器，没有则为空。它不持网格节点，
 * 故没有频道行，链路就是全部状态。画的那一半是 {@code client.jade.AlchemyReceiverTooltip}，
 * 两边都报 {@link #UID}。
 */
````

## 原第 20 行

````java
/** 与 {@code client.jade.AlchemyReceiverTooltip} 共用：Jade 按 UID 配对这两半。 */
````

## 原第 24 行

````java
/** {@link #appendServerData} 的传输格式，tooltip 那一半读回它。 */
````

## 原第 27 行

````java
/** 只在 {@link #TAG_BOUND} 为 true 时写，缺失的坐标永远不会被读成原点。 */
````

## 原第 35 行

````java
// 读字段本身：[resolveProvider()] 会在玩家背后把链路修好或切断。
````
