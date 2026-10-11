# `src/main/java/thaumicenergistics_ce/client/gui/InscriberButton.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 10 行

````java
/**
 * 知识铭刻机的按钮，贴图来自参考构建的两态图集。
 * 一张 32x32 的图集放两个 32x13 的帧，空闲在 v=0、悬停在 v=15，没有禁用帧：
 * 标签说明哪里不对（"No Core"、"Invalid"、"Full"），只要没悬停就画空闲态。
 * 标签向下居中两像素，与参考构建一致：原版居中位置太低。
 */
````

## 原第 54 行

````java
// 颜色必须带 alpha：26.1.2 的 [GuiGraphicsExtractor#text] 第一句就是
````

## 原第 55 行

````java
// `if (ARGB.alpha(color) != 0)`，alpha 为 0 的文字整句丢掉不画。
````

## 原第 56 行

````java
// 1.21.1 的 [GuiGraphics#drawString] 没有这道闸门，同样的 0x000000 在那边是黑字。
````
