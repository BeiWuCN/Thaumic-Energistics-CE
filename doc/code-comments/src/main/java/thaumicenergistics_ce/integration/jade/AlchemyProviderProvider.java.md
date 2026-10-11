# `src/main/java/thaumicenergistics_ce/integration/jade/AlchemyProviderProvider.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 11 行

````java
/**
 * 炼金供应器的 Jade 服务端数据：网格状态，以及绑定到它的接收器。
 * AE2 从附加 mod 挂不上的包里画自己的网格状态行，这一行因此写在这里。
 * 绘制的那一半是 {@code client.jade.AlchemyProviderTooltip}，两半都上报 {@link #UID}。
 */
````

## 原第 20 行

````java
/** 与 {@code client.jade.AlchemyProviderTooltip} 共用：Jade 按 UID 配对两半。 */
````

## 原第 24 行

````java
/** {@link #appendServerData} 的传输格式，tooltip 那一半读回它。 */
````

## 原第 27 行

````java
/** 供应器持有的 AE：没通电的网格会让这份储备为空，这一点也会显示出来。 */
````
