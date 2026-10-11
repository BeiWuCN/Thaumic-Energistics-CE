# `src/main/java/thaumicenergistics_ce/blockentity/vibrationchamber/BlockEntityEssentiaVibrationChamber.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 34 行

````java
/**
 * 将源质转为 AE 能量的振动室。
 * 燃料存储、燃烧和能量输出分别由 ChamberEssentiaTank、ChamberBurn 和 ChamberEnergyOutput 处理。
 */
````

## 原第 43 行

````java
/** 能量槽容量，单位 AE；1 AE 兑 2 FE，AE2 把 16 kAE 报成 32,000 FE。 */
````

## 原第 46 行

````java
/** 每 tick 的最大 AE 输出，取燃料可达到的峰值功率。 */
````

## 原第 54 行

````java
/** 退回：能量槽满到一个 tick 的量以内，什么都放不下。 */
````

## 原第 56 行

````java
/** 网格上只有这台机器：电力无处可去。 */
````

## 原第 68 行

````java
// 燃料、燃烧和电力分别在 [ChamberBurn]、[ChamberEssentiaTank]、[ChamberEnergyOutput]：
````

## 原第 69 行

````java
// 本类 tick 它们，并对网格、管道和界面当门面。
````

## 原第 84 行

````java
// 空闲 0.0 且不占频道，跟 AE2 自己的发电机一致（[VibrationChamberBlockEntity:57]、
````

## 原第 85 行

````java
// [ChargerBlockEntity:43]）：扁平网络最缺频道时，带频道的机器会灭。
````

## 原第 107 行

````java
/** 什么都不取出来：振动室烧掉给它的东西。 */
````

## 原第 120 行

````java
/** 返回 true，让管道看到目的地不是死路，尽管 {@link #takeEssentia} 什么都不取。 */
````

## 原第 132 行

````java
/** 缓冲或能量槽满时返回 0：管道照这个数导向，满的机器再报吸力会吸来烧不掉的源质。 */
````

## 原第 174 行

````java
/**
     * 客户端的副本：状态、燃烧速率、缓冲燃料。只由 {@link #markForClientUpdate()} 发出，
     * 不每 tick 发，否则被盯着看时倒计时会冻住。
     */
````

## 原第 186 行

````java
// 两者都会跑：基类回答有没有变化，同步单元把字节流写进机器。
````

## 原第 190 行

````java
/** 包级可见，给读字节流的 {@link VibrationChamberSync} 和下面的重载用。 */
````
