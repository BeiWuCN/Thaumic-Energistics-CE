# `src/main/java/thaumicenergistics_ce/integration/jade/InfusionProviderProvider.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 17 行

````java
/**
 * 注魔供应器的 Jade 服务端数据：它旁边的注魔祭坛实际能抽什么。
 * [getAspects] 刻意为空：方块是窗口不是容器，管道会跳过它。绘制那一半是
 * [client.jade.InfusionProviderTooltip]，不能放这里：解析要素 id 要客户端的 level；
 * 两边都上报 [UID]，Jade 靠它把两者配对。没有这一对，tooltip 挂不到方块上。
 */
````

## 原第 27 行

````java
/** 与 [client.jade.InfusionProviderTooltip] 共享：Jade 按 [UID] 配对这两半。 */
````

## 原第 31 行

````java
/** [appendServerData] 的线上格式。tooltip 那一半把它读回来。 */
````
