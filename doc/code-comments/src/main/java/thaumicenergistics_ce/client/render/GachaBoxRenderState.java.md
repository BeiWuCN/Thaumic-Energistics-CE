# `src/main/java/thaumicenergistics_ce/client/render/GachaBoxRenderState.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 5 行

````java
/**
 * 抽奖盒渲染器从 extract 阶段传给 submit 阶段的东西。
 * <p>
 * 两阶段拆分意味着再不能用局部变量：大脑在不在、转了多少度都在 {@code extractRenderState} 里定下，
 * 在 {@code submit} 里才画。
 */
````

## 原第 13 行

````java
/** 箱里有没有大脑，也就是有没有东西可画。 */
````

## 原第 16 行

````java
/** 这一帧大脑转动的角度，单位是度。 */
````
