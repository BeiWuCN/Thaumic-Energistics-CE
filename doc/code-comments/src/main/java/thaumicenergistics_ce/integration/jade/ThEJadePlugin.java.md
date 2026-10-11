# `src/main/java/thaumicenergistics_ce/integration/jade/ThEJadePlugin.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 13 行

````java
/**
 * 注册本 mod 的 Jade 提供器：服务端数据那一半，两侧 Jade 都会向它索取。
 * 类上打注解，不写在 service 文件里：NeoForge 上 Jade 按注解找插件；
 * Jade 是可选的，只有 Jade 存在时这个类才会加载。
 * 绘制在客户端的方块组件由 [client.jade.ThEJadeClientPlugin] 注册。
 */
````
