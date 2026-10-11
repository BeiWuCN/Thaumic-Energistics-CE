# `src/main/java/thaumicenergistics_ce/integration/jei/ThEJeiPlugin.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 15 行

````java
/**
 * Thaumic Energistics 的 JEI 插件，配方转移那一半，两侧 JEI 都会向它索取。
 * 这里不写任何界面类，专用服务端也要跑它。
 * 幽灵原料处理器在另一个插件 [client.jei.ThEJeiClientPlugin] 里，有自己的 UID。
 * 两个插件都用 Thaumaturge 的配方分类，铭刻机编码的内容与合成一致。
 */
````

## 原第 27 行

````java
/**
     * 构造时打一行日志：[ForgePluginFinder] 只报告找到的插件，不报告漏掉的。
     * 没这行就分不清“没加载”和“加载了但沉默”。
     */
````

## 原第 50 行

````java
// 用自带配方类型的处理器，别只给 [IRecipeTransferInfo]。
````

## 原第 51 行

````java
// info 会被 JEI 的 [BasicRecipeTransferHandler] 包起来：它假定槽位一一对应，12 对 9 时就拒绝。
````

## 原第 55 行

````java
// 用自带配方类型的处理器，别只给 [IRecipeTransferInfo]。
````

## 原第 56 行

````java
// info 会被 JEI 的 [BasicRecipeTransferHandler] 包起来：它假定槽位一一对应，12 对 9 时就拒绝。
````

## 原第 61 行

````java
// 用自带配方类型的处理器，别只给 [IRecipeTransferInfo]。
````

## 原第 62 行

````java
// info 会被 JEI 的 [BasicRecipeTransferHandler] 包起来：它假定槽位一一对应，12 对 9 时就拒绝。
````

## 原第 67 行

````java
// 坩埚配方进的是处理样板，格数与合成网格对不上，不能交给上面的基础处理器。
````

## 原第 71 行

````java
// 用自带配方类型的处理器，别只给 [IRecipeTransferInfo]。
````

## 原第 72 行

````java
// info 会被 JEI 的 [BasicRecipeTransferHandler] 包起来：它假定槽位一一对应，12 对 9 时就拒绝。
````
