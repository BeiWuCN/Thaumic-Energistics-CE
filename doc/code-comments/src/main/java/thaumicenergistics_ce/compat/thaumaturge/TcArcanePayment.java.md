# `src/main/java/thaumicenergistics_ce/compat/thaumaturge/TcArcanePayment.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 9 行

````java
/**
 * Thaumaturge 奥术付费的常数和灵气折扣。
 * 都在 {@code content} 包里，不在 {@code api} 包，会变动；
 * 终端的「晶体先付」靠这几个数换算，它们挪到哪这里就跟到哪。
 */
````

## 原第 17 行

````java
/** 一颗魔力水晶在法杖口径下值多少 centivis —— 两边换算共用它（一水晶 = 2 vis = 200）。 */
````

## 原第 22 行

````java
/**
     * 一次合成改由晶体付之后，无属性灵气那部分要多少钱：工作台走晶体路径的价
     * （{@code CRAFT_AURA_SURCHARGE} 加价乘装备折扣）。
     * <b>目前没接上</b>：{@link thaumicenergistics_ce.arcane.TerminalCrystalPayment}
     * 原样保留规划器算的灵气价，怕同一场合成凭空贵四分之一；
     * 要让终端严格按工作台晶体路径计价，把 {@code event.setCost(...)} 的第 4 参换成它。
     */
````
