# `src/main/java/thaumicenergistics_ce/client/gui/Ae2SlotHighlight.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 7 行

````java
/**
 * AE2 的悬停框：一像素浅青细框加半透明蓝底，画在物品之上。
 * <p>26.1.2 的原版把槽位高亮搬进 {@code AbstractContainerScreen} 的两个私有方法里贴白方块 sprite，
 * 模组既覆写不了也拦不住；AE2 自己的 {@code AEBaseScreen.renderSlotHighlight} 在这一代也没接上钩子
 * （它的 jar 里只有声明，没有任何调用点），所以本模组照它的画法与配色自己画一遍。
 * 颜色就是 {@code AEBaseScreen} 里那两个常量。
 */
````

## 原第 15 行

````java
/** 框色：AE2 的 {@code -2424833}。 */
````

## 原第 17 行

````java
/** 底色：AE2 的 {@code 1721553919}。 */
````

## 原第 23 行

````java
/** 坐标按调用方已经平移过的坐标系算，就是槽位自己的 {@code x}/{@code y}。 */
````

## 原第 30 行

````java
// AE2 自己的槽位可用样式改尺寸，框跟着槽走。
````
