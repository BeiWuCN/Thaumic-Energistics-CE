---
navigation:
  title: 奥术编码
  position: 45
  parent: thaumicenergistics_ce:index.md
  icon: thaumicenergistics_ce:arcane_encoding_terminal
item_ids:
- thaumicenergistics_ce:arcane_encoding_terminal
- thaumicenergistics_ce:wireless_arcane_encoding_terminal
- thaumicenergistics_ce:knowledge_core
---

# 奥术编码

<ItemLink id="thaumicenergistics_ce:arcane_encoding_terminal" /> 把奥术工作台的配方记进知识核心。它顶掉的是早期的知识编码器：产出一样，但材料按工作台的摆法摊在九格里，不用一件一件塞进机器槽位。

无线版 <ItemLink id="thaumicenergistics_ce:wireless_arcane_encoding_terminal" /> 是同一个终端装在口袋里。九格、核心与选中项都记在物品上，关掉界面走开再打开，东西还在原处。

## 左右两半

左半边是奥术工作台那样的九格，旁边是产物预览。右半边是核心面板：3 列 × 7 行，逐条列出这个核心里已有的样板，核心槽就在面板正下方。

## 放入知识核心

把 <ItemLink id="thaumicenergistics_ce:knowledge_core" /> 放进右侧面板下方的核心槽。没有核心时编码按钮会拒绝并提示，九格仍然可以用来试摆配方。

## 编码

1. 按奥术工作台的摆法把材料放进九格。
2. 箭头右边的框里显示产物，框上方的读数就是这条样板要记下的 Vis。
3. 配方收的是标签或者装满的容器时，打开旁边那两个开关，样板就会认下整组材料，而不只是显示出来的那一个。
4. 按下编码按钮。成功时九格清空，核心里多一条记录。

编码受研究门槛限制：还没解锁的配方会被拒绝，和当年知识编码器一样。

## 选中与删除

点右侧的样板槽即可选中，外面会亮起一圈彩色边框。编码格空着时再按一次编码按钮，就是删除选中的那条记录。左上角的小叉只清空编码格，不会动核心。

编码与删除共用同一个按钮是有意为之：一次点击不会同时做两件事。

## JEI

在 JEI 里对着一条奥术配方按 `+`，九格会被直接填好。材料不会被搬走——九格记的是「要编什么」，不消耗它——编好的样板照常留在核心里。

## 接入网络

装在电缆上时，终端取用所在网络的存量。作为无线物品，它需要一条链路：在无线访问点绑定，或者装上量子桥接卡与配对奇点，从任何地方连到已供电的量子桥。

装了 AE2WTLib 的通用终端时，这台终端是其中一个可切换的模式。
