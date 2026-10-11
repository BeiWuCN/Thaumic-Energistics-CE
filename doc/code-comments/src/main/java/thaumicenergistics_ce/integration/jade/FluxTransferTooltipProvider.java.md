# `src/main/java/thaumicenergistics_ce/integration/jade/FluxTransferTooltipProvider.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 11 行

````java
/**
 * 把通量接口的两半悬浮提示注册给 AE2。
 * <ul>
 *   <li>26.1.13 通过 {@code META-INF/services} 加载悬浮提示提供器，所以下面两个调用
 *       不能再从 mod 构造函数和客户端初始化里发出——改为在那个文件
 *       里写上这个类名。
 *   <li>部件不是方块实体，所以两半都走 {@link PartTooltips}，
 *       不走注册对象上那些 {@code addBlockEntity*} 方法。
 * </ul>
 */
````

## 原第 35 行

````java
// 这里没有自带方块实体的部件宿主：接口住在一条线缆上。
````
