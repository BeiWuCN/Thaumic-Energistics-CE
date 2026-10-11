# `src/main/java/thaumicenergistics_ce/network/EncoderSourcePayload.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 12 行

````java
/**
 * 蒸馏编码器的源模板，由客户端设置。
 * 它对应一个槽位却仍要上路：物品留在玩家物品栏里，原版槽位同步没有东西可送。
 * @param containerId 它作用的菜单，发给已关闭屏幕的数据包被忽略
 * @param stack 要蒸馏的物品，数量为一，空则清空该槽位
 */
````
