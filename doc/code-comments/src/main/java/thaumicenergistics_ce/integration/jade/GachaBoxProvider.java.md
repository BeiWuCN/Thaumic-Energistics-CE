# `src/main/java/thaumicenergistics_ce/integration/jade/GachaBoxProvider.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 19 行

````java
/** Gacha Box 的服务端半边：它绑定到谁，以及它为何不转。 */
````

## 原第 24 行

````java
/** 与 {@code client.jade.GachaBoxTooltip} 共用：Jade 靠 UID 配对两半。 */
````

## 原第 45 行

````java
// 放在 tooltip 最前：玩家最先要确认盒子有没有接入网络。
````

## 原第 46 行

````java
// 这一条同时覆盖 "no power" 与 "no channel"。
````

## 原第 52 行

````java
// 按已保存的物品堆发送，不发数量：tooltip 要画出这些卡片，光有 id 画不出来。
````

## 原第 53 行

````java
// 空槽位不发，这一行恰好是盒子里的卡片。
````

## 原第 65 行

````java
// 原因作为组件传输，客户端用玩家自己的语言渲染。
````

## 原第 66 行

````java
// 两种原因不传：缺失的上半部分会自己显现，节点离线上面已经说过。
````
