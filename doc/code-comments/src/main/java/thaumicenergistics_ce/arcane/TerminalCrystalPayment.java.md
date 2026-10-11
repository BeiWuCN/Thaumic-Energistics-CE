# `src/main/java/thaumicenergistics_ce/arcane/TerminalCrystalPayment.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 19 行

````java
/**
 * 终端优先使用水晶，余量由法杖支付。
 * 合并规划器给出的水晶和法杖费用后重新分配，支持两者都不足以单独支付的情况。
 * 保留费用事件已有的总价和无属性灵气费用；规划时只检查法杖储量，不扣费。
 */
````
