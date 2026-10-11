# `src/main/java/thaumicenergistics_ce/arcane/ArcanePatternDetails.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 18 行

````java
/**
 * 把 {@link ThEArcanePattern} 适配到 AE2 的合成 API。
 * 每个非空网格格位成为一个输入，再加上 vis 支付不了的那些晶体；
 * vis 本身不映射成 AE2 合成材料，它在合成时来自灵气。
 * 元初晶体由 vis 支付，不出现在输入里；复合晶体没有 vis 价值，会作为输入出现。
 */
````

## 原第 47 行

````java
/**
     * 同 {@link #of}，但定义由外部传入，不重建：{@code IPatternDetails} 的相等性定义在它上面，
     * 重建出的 key 未必满足 {@code save(load(tag)) == tag}，就匹配不上机器。
     * @param decodedDefinition 样板解码时的原始 key，传 {@code null} 则自行构建一个
     */
````

## 原第 67 行

````java
// 格位接受的全部物品，不只是它显示的那一个：
````

## 原第 68 行

````java
// 按标签写入的格位接受该标签的任意成员。
````

## 原第 81 行

````java
// 组装机没法用 vis 支付的晶体；数量就是配方的需求。
````

## 原第 138 行

````java
/**
     * 由一组物品 key 里任意一个即可满足的输入，第一个是 AE2 视图显示的那个。
     * 多个 key 正对应矿典条目的含义：该标签的任意成员都可以。
     */
````
