# `src/main/java/thaumicenergistics_ce/inventory/GearSlots.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 10 行

````java
/**
 * 奥术组装机四个装备槽能放什么。机器容器和菜单槽共用这条规则：
 * 两份规则正是菜单什么都收而容器拒绝的成因，只有 [mayPlace] 看得见点击。
 * 顺序对应槽 0..3，头、胸、腿、脚。
 */
````

## 原第 42 行

````java
// 26.1.2 删掉了 Equippable 接口：槽位现在放在 EQUIPPABLE 数据组件上。
````
