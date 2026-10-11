# `src/main/java/thaumicenergistics_ce/blockentity/assembler/AssemblerNodeListener.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 6 行

````java
/** 网格节点变化时唤醒机器：供电、重绘，以及被中断的合成。 */
````

## 原第 24 行

````java
// loadAdditional 在节点存在之前跑；这次唤醒得覆盖被恢复的合成。
````

## 原第 28 行

````java
/** 区块加载时创建节点。存档中途保存的合成在这里接上：
     * {@code loadAdditional} 跑的时候还没有 level 能跟它的结果比对。 */
````

## 原第 37 行

````java
/** 区块卸载时移除节点。 */
````
