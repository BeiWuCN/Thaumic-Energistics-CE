# `src/main/java/thaumicenergistics_ce/client/jade/ThEJadeClientPlugin.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 13 行

````java
/**
 * 客户端半边，对应 {@link thaumicenergistics_ce.integration.jade.ThEJadePlugin}：它的四个组件。
 * 做成第二个插件，不在第一个上挂方法：[IWailaClientRegistration] 携带的是 [Screen]；
 * 每个组件上报的 UID 与服务端数据半边（在 [integration.jade] 里）相同。
 * Jade 会在两侧问每个 [@WailaPlugin]，客户端只问客户端半边。
 */
````
