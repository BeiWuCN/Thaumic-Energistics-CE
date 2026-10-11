# `src/main/java/thaumicenergistics_ce/integration/jade/ArcaneAssemblerProvider.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 19 行

````java
/**
 * 奥术组装机的 Jade 服务端数据。
 * 方块实体走 Jade 的 API：AE2 的网格状态行注册在内部包
 * {@code appeng.integration.modules.igtooltip} 里，附加 mod 挂不上。
 * 部件是例外，走 AE2 公开的 [PartTooltips]。
 * 绘制的一半是 {@code client.jade.ArcaneAssemblerTooltip}，按 {@link #UID} 配对。
 */
````

## 原第 30 行

````java
/** 与 {@code client.jade.ArcaneAssemblerTooltip} 共用；Jade 按 UID 配对两半。 */
````

## 原第 34 行

````java
/** 标签名给绘制那一半读，也是本类的公开约定。
     * {@code AssemblerVisPool}、{@code AssemblerDisplaySync}、{@code AssemblerUpgrades} 机器侧也有一份。 */
````

## 原第 38 行

````java
/** 装备带来的整数百分比 vis 折扣。 */
````

## 原第 47 行

````java
/**
     * 是句子不是翻译键；句子里带数字，与日志输出保持一致。
     */
````

## 原第 58 行

````java
/**
     * 节点从 {@code getActionableNode} 取，不要用 {@code getGridNode(null)}。
     * null 面对任何方向都不暴露，能力查询一律答 null。
     */
````

## 原第 72 行

````java
// vis 取整；百分位不值得占 tooltip 一行。
````

## 原第 76 行

````java
// 发的是可用样板数，回答 “why is nothing being crafted for me” 的就是它。
````

## 原第 82 行

````java
// 用物品堆自己的描述 id；自己拼 "block." 前缀会画出原始键名。
````

## 原第 88 行

````java
// 发存好的物品堆；只发 id 画不出来，客户端推导等于重跑一遍配方解析。
````

## 原第 95 行

````java
// 按网格顺序发，图标像配方一样从左到右读。
````

## 原第 96 行

````java
// 只发非空格子；九个空框就是九个空图标。
````

## 原第 108 行

````java
// 发组件本身；服务端挑原因但不知道玩家语言。
````

## 原第 109 行

````java
// NbtOps 带键和参数，客户端解析。
````
