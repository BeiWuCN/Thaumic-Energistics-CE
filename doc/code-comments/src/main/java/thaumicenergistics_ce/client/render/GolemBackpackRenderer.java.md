# `src/main/java/thaumicenergistics_ce/client/render/GolemBackpackRenderer.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 28 行

````java
/**
 * 每帧从世界渲染阶段画出所有可见背包。
 * Thaumaturge 的傀儡渲染器不是 NeoForge 能加层的 living 类型，没有钩子可挂。
 * {@link #PACK_HEIGHT} 和 {@link #PACK_DEPTH} 是目测定的。
 * 绘制在 AFTER_ENTITIES 阶段，批次在这里收尾。
 */
````

## 原第 37 行

````java
/** 背包在傀儡身体上的高度，以脚上方多少方块计。 */
````

## 原第 48 行

````java
/**
     * 按皮肤区分的渲染类型，每种只建一次。
     * {@code RenderType.entityCutoutNoCull(texture)} 每次调用都分配新对象，批次中途切换类型会刷新待处理批次。
     */
````

## 原第 68 行

````java
// 这一帧在一个 tick 里占的比例：傀儡位置插值落在 tick 之间就靠它。
````

## 原第 91 行

````java
// 傀儡渲染器自身的主体旋转；模型空间 +Z 是傀儡背面，取 +depth。
````

## 原第 94 行

````java
// 四分之一圈：模型盒子按沿 X 轴两像素厚制作。
````

## 原第 95 行

````java
// 放在这里而不写进模型，皮肤纹理才能继续对上它们原本绘制的面。
````
