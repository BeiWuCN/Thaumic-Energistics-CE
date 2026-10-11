# `src/main/java/thaumicenergistics_ce/init/ModItems.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 25 行

````java
/**
 * 物品注册，含 {@link ModBlocks} 的方块物品。
 * 方块物品放这里：NeoForge 的 {@code registerSimpleBlockItem} 属于物品注册表，
 * 持有者却来自 {@link ModBlocks}，这个类两边都要读。
 */
````

## 原第 36 行

````java
/**
     * 要继承 {@code EncodedPatternItem}：AE2 的 CPU 把任务存成 {@code AEItemKey} 标签，
     * 用 {@code PatternDetailsHelper.decodePattern} 重建，它能识别的只有 {@code EncodedPatternItem}。
     */
````

## 原第 103 行

````java
/**
     * 作为法杖核心佩戴的 AE2 扳手。
     * 不做成 {@code PartItem}：Thaumaturge 的核心是普通物品，行为放在物品堆的包组件里。
     */
````

## 原第 119 行

````java
// 容量按字节计，与 AE2 一致：以源质算是名称数字的八倍，1k 存 8192。
````

## 原第 140 行

````java
// 普通物品：容量在存储元件里，组件只是原料；档位沿用 AE2 的字节数。
````

## 原第 158 行

````java
// 没有行为的普通物品：终端和总线用的原料，与 AE2 的湮灭核心、成型核心同一种形式。
````

## 原第 186 行

````java
// 两张升级卡都会被读：访问卡开关源质手势，vis 卡把合成中未指定类型的 vis 转到灵气上。
````

## 原第 187 行

````java
// AE2 的 [isUpgradeCardItem] 要求物品是自己的类，故用工厂创建。
````
