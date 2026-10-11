# `src/main/java/thaumicenergistics_ce/integration/jei/CrucibleJeiRecipeType.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 7 行

````java
/**
 * Thaumaturge 的坩埚配方类型，借用不镜像：另开一页会和玩家看的那一页不一致。
 * 和 [ArcaneJeiRecipeType] 分成两个类：类别不同，类型就不同，合成一个字段会让两个类别互相顶掉。
 * 类型在方法内解析，不存静态字段：JEI 加载前这里不会被读到，
 * 类加载时也就不会碰到 JEI 的类。
 */
````

## 原第 17 行

````java
/** Thaumaturge 自己的坩埚配方类型。 */
````
