# `src/main/java/thaumicenergistics_ce/client/jei/ThEJeiClientPlugin.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 14 行

````java
/**
 * {@link thaumicenergistics_ce.integration.jei.ThEJeiPlugin} 的客户端部分：幽灵配料处理器和它投放的屏幕。
 * 做成第二个插件：registerGuiHandlers 注册本身就要一个 Screen 名。
 * JEI 只从它的客户端启动器到这里，UID 与转移那一半分开。
 */
````

## 原第 34 行

````java
// 每个具体屏幕类一个处理器：JEI 把一个 [Class] 和同类型的处理器配对，
````

## 原第 35 行

````java
// 而这个拖拽目标收的是 Thaumaturge 的要素配料，不是物品。
````

## 原第 38 行

````java
// 每个具体屏幕类一个处理器：JEI 把一个 [Class] 和同类型的处理器配对，
````

## 原第 39 行

````java
// 而这个拖拽目标收的是 Thaumaturge 的要素配料，不是物品。
````

## 原第 42 行

````java
// 每个具体屏幕类一个处理器：JEI 把一个 [Class] 和同类型的处理器配对，
````

## 原第 43 行

````java
// 而这个拖拽目标收的是 Thaumaturge 的要素配料，不是物品。
````
