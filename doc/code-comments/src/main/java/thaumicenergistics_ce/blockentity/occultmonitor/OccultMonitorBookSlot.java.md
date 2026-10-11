# `src/main/java/thaumicenergistics_ce/blockentity/occultmonitor/OccultMonitorBookSlot.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 17 行

````java
/**
 * 监控器唯一的槽、镜像它的两个方块状态，以及右键对典籍做什么。只放得进魔导手册，
 * 只有潜行的玩家能把它取出来；两个方块状态都在这里写，方块类因此只剩一层薄覆写。
 */
````

## 原第 55 行

````java
/** 放入典籍，只在玩家潜行时取出。普通右键会把机器解除武装。见 {@code BlockOccultMonitor}。 */
````

## 原第 105 行

````java
// 空槽也必须写这个字段：ItemStack.CODEC 拒收数量为零的物品堆，会让整次存档失败，
````

## 原第 106 行

````java
// 而 OPTIONAL_CODEC 写出的空 compound 正好是 load() 读得回的那个。
````

## 原第 120 行

````java
// 拆除期间不能触发 setChanged，否则 BOOK 状态更新会把正在移除的方块写回世界。
````
