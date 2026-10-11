# `src/main/java/thaumicenergistics_ce/util/ThEItemTags.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 13 行

````java
/**
 * 26.1.2 上 {@link ItemStack} 的 NBT 往返读写。
 *
 * 原版砍掉了 {@code ItemStack#save(HolderLookup.Provider)} 和它的两个 optional 兄弟，改用
 * {@code ItemStack.CODEC}，但 codec 仍会走每个组件自己的 codec，装着要素的组件会经过
 * {@code RegistryFixedCodec}：交给它的 ops 不带注册表就抛 {@code Can't access
 * registry thaumaturge:aspect}。所以当年满足那三个签名用的 provider 这里还得留着——
 * 它买的是 ops，不是签名。
 */
````

## 原第 26 行

````java
/**
     * {@code ItemStack.CODEC} 用的带注册表后端；provider 为 null 就退回普通 NBT，
     * 只在物品堆上没有组件需要注册表时才管用。
     */
````

## 原第 34 行

````java
/** 写一个已知非空的物品堆；也就是旧的 {@code ItemStack#save(Provider)}。 */
````

## 原第 39 行

````java
/** 读回物品堆，读不出来的一律给 {@link ItemStack#EMPTY}。 */
````

## 原第 50 行

````java
/** 读回物品堆，值完全读不出来时返回空。 */
````
