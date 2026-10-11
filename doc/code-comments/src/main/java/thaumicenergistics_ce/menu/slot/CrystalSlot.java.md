# `src/main/java/thaumicenergistics_ce/menu/slot/CrystalSlot.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 17 行

````java
/**
 * 奥术合成终端六个水晶槽之一，钉在一个元质上。拒绝交给 {@code TcWorkbench.isValidCrystal}，
 * 就是工作台自己的规则（{@code MenuArcaneWorkbench.addSlots}），终端收的东西和工作台一致。
 * {@code mayPlace} 是全部闸门：进槽的每条路都过它。
 */
````

## 原第 23 行

````java
/** 同一条结论一秒最多写一行，免得拖动时刷屏。 */
````

## 原第 32 行

````java
// 每个槽钉死一个元质，玩家得知道是哪一个：按住 Shift 悬停空槽时写清它要什么。
````

## 原第 33 行

````java
// 常驻会盖住旁边的槽，所以照 AE2 自己的习惯让 Shift 决定出不出。
````

## 原第 34 行

````java
// 供货方（AE2 的 getCustomTooltip）是在画提示时才调这个 supplier 的，
````

## 原第 35 行

````java
// 于是 Screen 这个客户端类只在客户端才被解析——服务端建菜单时不会碰它。
````

## 原第 42 行

````java
/** 左 Shift（或右 Shift）按住时才出提示。 */
````

## 原第 44 行

````java
// 26.1.2 把 Screen.hasShiftDown() 拿掉了，改问客户端自己。
````

## 原第 48 行

````java
/** 要素的名字就是语言文件里的 {@code aspect.<命名空间>.<路径>}，跟 Thaumaturge 对齐。 */
````

## 原第 68 行

````java
/**
     * 诊断：写清「哪个槽、来的什么东西、自带什么要素、哪一层放行或拦下」。
     * 拦下来只有两处——容器闸门（{@code isCrystal}，即物品不对）或者要素不匹配。
     * 客户端也会走 {@code mayPlace}，同一件事可能在日志里出现两份，看线程名分辨。
     */
````
