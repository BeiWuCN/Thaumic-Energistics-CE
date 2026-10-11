# `src/main/java/thaumicenergistics_ce/blockentity/alchemyprovider/AlchemyProviderStorage.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 15 行

````java
/**
 * 把供应器当 ME 物品栏看：网格交出来的东西，供外界取走。源质放在缓冲里，
 * 故这里的一切询问都转问缓冲；抽取被拒，缓冲里的源质正在往外走，不会再回来。
 */
````

## 原第 41 行

````java
// AE2 用 Actionable 发问，Thaumaturge 用事务作答；空跑就是开了事务却从不提交，
````

## 原第 42 行

````java
// 所以改成直接问缓冲。
````

## 原第 74 行

````java
/** 缓冲以 int 计量；单次 AE 插入本来也超不过一个要素槽允许的量。 */
````
