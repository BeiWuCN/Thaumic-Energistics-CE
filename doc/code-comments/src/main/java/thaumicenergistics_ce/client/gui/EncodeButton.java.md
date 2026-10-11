# `src/main/java/thaumicenergistics_ce/client/gui/EncodeButton.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 10 行

````java
/**
 * 蒸馏编码器的 Encode 按钮，来自参考构建自己的两态贴图。
 * 禁用态覆一层纱：图集里没有禁用帧，而“Encode”本身说明不了什么。
 * 绘制成 34x14，不用原生的 32x13：参考构建把它拉伸到面板条带。
 * 标签距顶部 3 像素居中，与铭刻机的标签一致。
 */
````

## 原第 61 行

````java
// 颜色必须带 alpha：26.1.2 的 [GuiGraphicsExtractor#text] 第一句就是
````

## 原第 62 行

````java
// `if (ARGB.alpha(color) != 0)`，alpha 为 0 的文字整句丢掉不画。
````

## 原第 63 行

````java
// 1.21.1 的 [GuiGraphics#drawString] 没有这道闸门，同样的 0x000000 在那边是黑字。
````
