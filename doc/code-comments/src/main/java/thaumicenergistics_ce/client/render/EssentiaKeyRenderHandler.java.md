# `src/main/java/thaumicenergistics_ce/client/render/EssentiaKeyRenderHandler.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 23 行

````java
/**
 * AE2 怎么画源质 key。注册是硬要求：画没有处理器的 key 类型时 AE2 会抛。
 * 图标来自 Thaumaturge 的 {@link AspectRendering}，它知道要素纹理和玩家的发现状态；
 * 手写 blit 会丢掉遮罩。
 */
````

## 原第 36 行

````java
// AE2 吞掉渲染处理器抛的一切，未解析的 key 就此无声消失：这条警告是那段代码跑过的唯一证据。
````

## 原第 46 行

````java
// 数量写 0，因 AE2 紧接着在这之上自己画数量。再要芯片自己的标签会把数字印两遍。
````

## 原第 62 行

````java
// 没有方块表面形态：见类注释。
````

## 原第 68 行

````java
// 没有方块表面形态：见类注释。
````
