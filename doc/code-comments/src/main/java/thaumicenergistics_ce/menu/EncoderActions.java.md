# `src/main/java/thaumicenergistics_ce/menu/EncoderActions.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 8 行

````java
/** 蒸馏编码器的动作主体，从 {@link MenuDistillationEncoder} 拆出来，让后者不超文件行数预算。
 * 每个方法都是宿主的转发目标，且只在传给它的那个宿主实例上跑。 */
````

## 原第 18 行

````java
// 在这里也拒，不只界面里拒：动作载荷也走这条路，手搓的点击不能选中未发现的要素。
````
