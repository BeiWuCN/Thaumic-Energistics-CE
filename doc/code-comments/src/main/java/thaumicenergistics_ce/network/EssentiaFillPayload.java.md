# `src/main/java/thaumicenergistics_ce/network/EssentiaFillPayload.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 12 行

````java
/**
 * 「把这个要素从网络抽进我的容器」，由源质终端的左击发出。
 * {@code aspectId} 以 id 上路：客户端拼错的键匹配不上服务端存储。
 * {@code where} 指名容器槽位，{@code stack} 只是客户端的提示。
 * {@code wholeStack} 是 shift 点击，填满手持的整个物品堆。
 */
````

## 原第 45 行

````java
// 这个 record 的 stack 字段是客户端提示，这里不读。
````
