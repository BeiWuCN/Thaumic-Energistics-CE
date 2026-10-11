# `src/main/java/thaumicenergistics_ce/infusion/InfusionRisk.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 3 行

````java
/**
 * 一次注魔有多危险：0 到 {@link #CAP} 之间的一个数，以及它落入的五个档位之一。
 * @param base 配方自身的不稳定度
 * @param altar 祭坛周边额外增加的量，以摆错位置的方块计
 * @param shortages 仪式是否正在等它够不到的源质
 * @param stability 祭坛此刻自己报的稳定度，也就是护目镜显示的值
 */
````

## 原第 24 行

````java
/**
     * 风险落在五个档位中的哪一档，1（几乎没什么）到 {@link #MAX_TIER}。
     * 按 Thaumaturge 自己的阈值 12.5、0、-25 读祭坛稳定度，再以 20 为间隔切成五档。
     */
````
