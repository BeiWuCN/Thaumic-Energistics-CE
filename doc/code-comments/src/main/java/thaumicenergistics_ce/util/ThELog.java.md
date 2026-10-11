# `src/main/java/thaumicenergistics_ce/util/ThELog.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 6 行

````java
/**
 * 持有 mod 日志器，写日志的类不必导入 {@code @Mod} 组合根。
 * 是普通持有者不是包装器：{@link Logger} 实例和传给 {@link LoggerFactory#getLogger(String)} 的名字
 * 与根类用的完全一致，日志输出不变。
 */
````
