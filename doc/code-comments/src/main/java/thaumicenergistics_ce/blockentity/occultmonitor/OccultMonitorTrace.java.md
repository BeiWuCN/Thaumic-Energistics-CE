# `src/main/java/thaumicenergistics_ce/blockentity/occultmonitor/OccultMonitorTrace.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 8 行

````java
/**
 * 监控器每秒一行追踪。没有它，各种失效模式（无网格、无电力、无书、无祭坛）看起来一模一样。
 * 除非 [THAUMICENERGISTICS_MONITOR_TRACE=true] 打开，否则这一行是关的，不花代价；
 * 标签命名的是打印出来的内容，不是持有它的字段名。
 */
````
