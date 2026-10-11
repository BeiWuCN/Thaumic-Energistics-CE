# `src/main/java/thaumicenergistics_ce/client/jade/InfusionProviderTooltip.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 30 行

````java
/**
 * 注魔供应器的 Jade tooltip：旁边的祭坛实际能抽到什么。
 * 它是 {@link InfusionProviderProvider} 的绘制半边，两边报同一个 {@link #getUid() UID}，
 * Jade 靠它配对服务端数据。
 * 条目一次一个要素：一个图标，数量标在角上，格式用 {@link AmountFormat#SLOT}。
 */
````

## 原第 63 行

````java
// 注册表里没有就整条不列，不给它标成别的东西。
````

## 原第 67 行

````java
// 用 AE2 自己的缩写「16K」「28M」，图标下面的数字读起来像在终端里。
````

## 原第 86 行

````java
// 放最后：这台机器本身不存东西，刚放下它时玩家最需要看的就是这行。
````

## 原第 106 行

````java
/**
     * 一个要素小图标，数量写在旁边。
     * <ul>
     *   <li>Jade 26.1.8 把 {@code IElement} 接口换成了 {@code Element} 类，它的契约是两个方法：
     *       报一段朗读文本，以及在交给你的位置上提取渲染状态。尺寸现在是一个字段，不再是一打重写。
     *   <li><b>坐标要用自己的 {@code getX()}/{@code getY()}，不能用传进来的那两个 int。</b>
     *       {@code BoxElementImpl.extractRenderState} 遍历子元素时，给每个子元素传的是同一个
     *       {@code Vector2i}，也就是整个提示框的原点；真正的位置是布局在排完行之后用
     *       {@code setX}/{@code setY} 写进元素自己的。Jade 自带的元素（{@code ItemStackElement}、
     *       {@code ProgressElement}、{@code TextElementImpl}、{@code SpriteElement}）一律只用自身坐标。
     *       1.21.1 那一版用的是老接口 {@code IElement.render(graphics, x, y, delta, alpha)}，
     *       那里的 x/y 恰好就是元素自己的位置，照抄过来就会把整排图标叠画在提示框左上角。
     *   <li>角标算进宽度：Jade 按各元素上报的宽度排一行，
     *       最早的版本只报了图标的 16 像素，数字就压到下一个图标上。
     *   <li>左对齐，短的最后一行和第一行对齐。
     * </ul>
     */
````

## 原第 142 行

````java
// 传进来的 x/y 是提示框原点，不是这个图标的位置；布局把自己的坐标写进了这两个字段。
````

## 原第 146 行

````java
// 图标最下一行，一行读起来就是一整条数字。上面的数量写 0，
````

## 原第 147 行

````java
// 因为这个角标才是那个数字，不是图标自己的标签。
````
