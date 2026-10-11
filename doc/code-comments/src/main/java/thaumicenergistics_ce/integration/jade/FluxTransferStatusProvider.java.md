# `src/main/java/thaumicenergistics_ce/integration/jade/FluxTransferStatusProvider.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 13 行

````java
/**
 * 咒波接口的 tooltip 服务端半边。它走 AE2 自己的部件注册表，不走 Jade 的，
 * 因为 Jade 只认识方块实体；与本类配对的是
 * {@code client.jade.FluxTransferTooltip}，注册这两个的是 {@link FluxTransferTooltipProvider}。
 */
````
