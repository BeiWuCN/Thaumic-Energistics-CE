# `src/main/java/thaumicenergistics_ce/blockentity/occultmonitor/OccultMonitorReadings.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 13 行

````java
/**
 * 气泡的读取面：监控器最后一次发给客户端的内容，以及发送它的快照。
 * 这里只被读，从不计算，玩家看到的气泡就是服务端的快照。
 * 书不在其中，它作为方块状态传输。
 */
````

## 原第 32 行

````java
/** 把当前读数交给同步单元；它只在有变化时才发。 */
````
