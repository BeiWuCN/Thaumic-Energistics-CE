# `src/main/java/thaumicenergistics_ce/golem/FacadeToSkinMapping.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 14 行

````java
/**
 * 某种方块请求的是哪种背包外观。匹配限于 Thaumaturge 命名空间，按方块 id 走。
 * 匹配刻意用子串：方块、木板、原木、楼梯在玩家眼里是同一种材质。
 * AE2 的伪装板会先被拆开；未列入表的一律返回 null，外观不变。
 */
````
