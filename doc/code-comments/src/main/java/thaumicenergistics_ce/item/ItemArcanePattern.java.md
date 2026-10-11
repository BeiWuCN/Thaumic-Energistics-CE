# `src/main/java/thaumicenergistics_ce/item/ItemArcanePattern.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 13 行

````java
/**
 * 奥术样板的物品形态，待处理的 AE2 合成计划才能挺过存档。AE2 经
 * {@link PatternDetailsHelper#decodePattern} 重载已存任务，它要 {@code EncodedPatternItem}。
 * 定义里带配方不带结果，结果在配方之间不唯一。相等和解码一样要紧，
 * 供应器索引是以两者为键的 {@code HashMap}。
 */
````

## 原第 25 行

````java
/**
     * 经 AE2 的 builder 构建该物品，那是 AE2 唯一接受的 {@code EncodedPatternItem} 来源。
     * <ul>
     *   <li>properties 作为参数传入而不在这里构造：26.x 起注册 id 挂在 {@code Item.Properties} 上，
     *       物品构造器经 {@code Item$Properties.effectiveDescriptionId} 读回它。调用方是
     *       {@code DeferredRegister.Items.registerItem}，只有那个重载会盖上 id——在这个方法里构造
     *       properties 会交出一个没有 id 的实例，加载时直接 {@code NullPointerException: Item id not set} 死掉。
     * </ul>
     */
````

## 原第 50 行

````java
// 作为定义原样传入，解码出的任务才等于这台机器的提供。
````
