---
navigation:
  title: 蒸馏样板
  position: 30
  parent: thaumicenergistics_ce:automation.md
  icon: thaumicenergistics_ce:distillation_encoder
item_ids:
- thaumicenergistics_ce:distillation_encoder
---

# 蒸馏样板

处理样板需要把源质写成产物时，使用 <ItemLink id="thaumicenergistics_ce:distillation_encoder" />。

1. 把空白样板放进编码台。
2. 设置原料。可以从 JEI 把物品拖到原料槽，这一步不会拿走实际物品。
3. 核对要素种类和数量，按蒸馏线路实际能送回的产量编码。
4. 把样板装入支持的样板供应器，并让蒸馏线路把产出的源质送回 ME 存储。

样板告诉 AE2 应等待哪些产物。实际加工和源质回收由你布置的线路完成。机器已经做完、合成任务却迟迟不结束时，先比较样板记录的要素和实际入网的种类及数量。
