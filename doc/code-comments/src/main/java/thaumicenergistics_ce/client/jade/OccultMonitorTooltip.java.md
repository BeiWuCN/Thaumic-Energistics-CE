# `src/main/java/thaumicenergistics_ce/client/jade/OccultMonitorTooltip.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 20 行

````java
/**
 * 神秘监控器的 Jade tooltip：{@link OccultMonitorProvider} 的绘制半边。
 * 数字来自服务端，文字在这里拼，跟随玩家语言。
 * 风险拆成“4（基础 1 + 祭坛 3）”，玩家才知道该修哪一半。
 */
````

## 原第 38 行

````java
// 状态行背后有两种故障：缺书是机器自身的毛病，玩家能修。
````

## 原第 39 行

````java
// “没有祭坛”是对房间的判断，只有搜索才能下。
````

## 原第 59 行

````java
// 先显示实时稳定性，那个数字才在变，仪式背后的两个排在后面。
````

## 原第 60 行

````java
// 负值不是错误：祭坛把值夹在 -100 到 25 之间，低于零就吐出去。
````

## 原第 84 行

````java
// 在客户端构建，名称用玩家的语言，不用服务端的。
````

## 原第 100 行

````java
/** Thaumaturge 自己给稳定度的档位说法，抄它的阈值和翻译键，护目镜与监控器才对得上。
     * 阈值出自 {@code BlockEntityInfusionMatrix.stabilityTierKey}。 */
````
