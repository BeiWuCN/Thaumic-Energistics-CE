# `src/main/java/thaumicenergistics_ce/menu/EncoderClicks.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 7 行

````java
/** 蒸馏编码器的点击处理与 shift 点击路由。
 * 从 {@link MenuDistillationEncoder} 拆出，以守住文件行数预算。 */
````

## 原第 15 行

````java
// 先重新推导这一行，后面的判断依据它当前的样子。
````

## 原第 17 行

````java
// slotId 是菜单索引不是容器索引，玩家槽位排在前面。
````

## 原第 18 行

````java
// 源凹槽也在这里处理：TemplateSlot 两个方向都拒绝，否则清不掉。
````

## 原第 30 行

````java
// 未发现的凹槽不绘制内容，在那个位置点击不选中。
````

## 原第 42 行

````java
// 点击已选中的要素会清除它。
````
