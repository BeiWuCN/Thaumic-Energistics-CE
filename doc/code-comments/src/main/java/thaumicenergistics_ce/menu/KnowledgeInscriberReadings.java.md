# `src/main/java/thaumicenergistics_ce/menu/KnowledgeInscriberReadings.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 9 行

````java
/**
 * 知识铭刻机的两项读数，由菜单交给屏幕：服务端上取自机器和它的核心槽，
 * 客户端上是服务端最后发来的值；{@code set} 保留收到的值，客户端上槽位同步会调它。
 */
````

## 原第 19 行

````java
/** 状态为谁而读：配方能不能存要对照这名玩家检查。 */
````

## 原第 22 行

````java
/** 核心槽里是否有核心。只有菜单能看到自己的槽位。 */
````

## 原第 46 行

````java
/**
     * 机器的状态，只是对不能存该配方的玩家，[ACTIONABLE] 变成 [RESEARCH_LOCKED]：
     * 按钮的 tooltip 读它；[ACTIONABLE] 是可存储，不是被允许。
     */
````
