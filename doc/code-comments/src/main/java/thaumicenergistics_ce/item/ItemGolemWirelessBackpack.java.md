# `src/main/java/thaumicenergistics_ce/item/ItemGolemWirelessBackpack.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 16 行

````java
/**
 * 一条通往 ME 网络的无线连接，形态是傀儡能携带的东西。
 * 连接是 AE2 自己的：一个 {@link GlobalPos} 存在 {@link AEComponents#WIRELESS_LINK_TARGET}。
 * {@link #LINKABLE_HANDLER} 是内存卡请求的对象；对傀儡点一下就算装备。
 * Thaumaturge 的饰品注册表用不了，{@code ItemGolemAccessory} 是 final；连接存在傀儡的数据里。
 */
````

## 原第 24 行

````java
/** 交给 AE2，内存卡靠它链接与解除链接这个物品。 */
````

## 原第 47 行

````java
// 这里用 AE2 自己的措辞，已链接的背包读起来像已链接的无线终端。
````
