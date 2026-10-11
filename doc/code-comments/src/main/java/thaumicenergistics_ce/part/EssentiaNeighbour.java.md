# `src/main/java/thaumicenergistics_ce/part/EssentiaNeighbour.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 17 行

````java
/**
 * 找到总线所面对方块上的源质容器，并问对问题。
 * <ul>
 * <li>{@code isConnectable} 收的是容器自己的面：位于北侧的总线落在它的 SOUTH 面。
 * <li>面不对就沉默：罐子只在 {@code UP} 应答 true，别的面什么都插不进去。
 * <li>顺序：总线那一面上的 transport，然后任一被接受的面上的 storage，最后把 transport 当 storage。
 * </ul>
 */
````

## 原第 31 行

````java
/**
     * 有意不缓存，是本 mod 邻居查找里唯一这么做的一个：目标不是调用方自己的方块，
     * 部件的宿主也从不说邻居被重新打包过。直接问更省。
     */
````

## 原第 64 行

````java
/** 按面不可免：管道的内容按面不同，一个适配器只服务一个面。 */
````

## 原第 83 行

````java
/** 立即生效：Thaumaturge 的管道 API 没有事务可以托管这次改动。 */
````
