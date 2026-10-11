# `src/main/java/thaumicenergistics_ce/menu/VibrationChamberReadings.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 8 行

````java
/**
 * 振动室的读数，由菜单交给屏幕。
 * 服务端上经 {@link VibrationChamberSync#reading} 取自机器，客户端上就是服务端最后发来的值。
 * {@code set} 原样保留传入的值：客户端的槽位同步会调用它。
 */
````
