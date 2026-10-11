# `src/main/java/thaumicenergistics_ce/menu/MenuEssentiaVibrationChamber.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 16 行

````java
/**
 * 源质振动室的菜单：玩家物品栏，加机器的三项读数。机器没有槽，
 * 燃料从管道或 ME 网络进，电力从线缆出。读数用 {@link ContainerData} 传，因每 tick 都变；
 * 屏幕读自己的方块实体副本，只会显示最后一次方块更新恰好带的值。
 */
````

## 原第 30 行

````java
// 读数，按屏幕从 [ContainerData] 读出的顺序。数字和含义在 [VibrationChamberSync]；
````

## 原第 31 行

````java
// 这些名字留着是因屏幕要用。
````

## 原第 104 行

````java
/**
     * shift 点击只在玩家物品栏和快捷栏之间搬物品堆，没别的：机器没有槽，
     * 这不是疏漏，是能做到的极限。
     */
````
