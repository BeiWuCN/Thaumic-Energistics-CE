# `src/main/java/thaumicenergistics_ce/integration/jade/OccultMonitorProvider.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 15 行

````java
/**
 * 神秘监控器的 Jade 服务端数据：它看不看得见，看见了什么。
 * 服务端读祭坛并把结果写进数据标签；稳定性归服务端。
 * 绘制那一半是 [client.jade.OccultMonitorTooltip]，按 [UID] 配对，数字原样传过去。
 */
````

## 原第 24 行

````java
/** 与 [client.jade.OccultMonitorTooltip] 共用：Jade 按 [UID] 配对这两半。 */
````

## 原第 28 行

````java
/** 监控器是否备有书和祭坛：缺一个它就不出风险结论。
     * 机器同步给自己渲染器的气泡也写这个词（[OccultMonitorSync.TAG_REPORTING]）；两处得一致。 */
````

## 原第 32 行

````java
/** 节点的上次活跃之后有没有跑过祭坛搜索。
     * 「没有祭坛」在房间被搜过之后才是关于这个房间的事实。 */
````

## 原第 35 行

````java
/** 机器上装没装神秘学书（thaumonomicon）。缺了它监控器是瞎的，不是空闲。 */
````

## 原第 41 行

````java
/** 祭坛当前稳定性乘十后的值。见 [appendServerData]。 */
````

## 原第 64 行

````java
// 祭坛自己的稳定性乘十：它是 float，这标签只能装 int。
````
