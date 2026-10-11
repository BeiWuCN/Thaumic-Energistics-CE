# `src/main/java/thaumicenergistics_ce/item/WirelessEssentiaTerminalMenuHost.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 11 行

````java
/**
 * 无线源质终端背后的菜单宿主。终端行为全来自 AE2 的 {@link WirelessTerminalMenuHost}，
 * 唯一的覆写是按键。按键经基于部件的 {@code PartEssentiaTerminal} 读，
 * 后者用同样方式施加同样的限制：两个都说「只要源质」的终端该走同一套机制。
 */
````

## 原第 18 行

````java
/** 只要源质。监听器为空是因没有东西要存：宿主每次都答同一句，
     * 「玩家改了选择」这类回调没有可记的。 */
````
