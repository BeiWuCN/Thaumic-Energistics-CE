# `src/main/java/thaumicenergistics_ce/block/BlockAlchemyProviderConnection.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 22 行

````java
/**
 * 炼金供应器连接件：无线源质链路的接收端。{@code facing} 把插头指向它装的那个面，
 * 本 mod 资源里的方块状态针对它声明了全部六个方向，属性和它的名字都是关键。
 * {@code connected} 是链路在不在；插头能装在任意面，向上向下都行。
 */
````

## 原第 73 行

````java
/**
     * 方块 tick 器搬源质：它没有自己的网格节点，它是线不是机器，故没法像供应器那样让 AE2 来 tick 它；
     * 工作量是每半秒一次传输。
     */
````
