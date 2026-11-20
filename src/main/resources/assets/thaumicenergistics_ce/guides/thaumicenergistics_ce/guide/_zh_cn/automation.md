---
navigation:
  title: 源质与奥术自动化
  position: 50
  parent: thaumicenergistics_ce:index.md
  icon: thaumicenergistics_ce:alchemy_provider
item_ids:
- thaumicenergistics_ce:alchemy_provider
- thaumicenergistics_ce:arcane_assembler
- thaumicenergistics_ce:knowledge_core
- thaumicenergistics_ce:knowledge_inscriber
---

# 源质与奥术自动化

先决定要让网络接手哪一个步骤。

- [用 ME 总线输送源质](essentia-buses.md)介绍如何存入、取出或开放容器库存。
- [自动化注魔](infusion-automation.md)从给祭坛接上 ME 供料开始，再接入注魔完成信号。
- [蒸馏样板](distillation-patterns.md)介绍怎样把蒸馏产出写入处理样板。

## 炼金

<ItemLink id="thaumicenergistics_ce:alchemy_provider" /> 按相邻炼金设备的请求供给源质。它能随配方调整供料，无需像输出总线那样逐项设定。设备卡在某项请求时，先去源质终端核对该要素的库存。

## 奥术装配

用 <ItemLink id="thaumicenergistics_ce:knowledge_inscriber" /> 记录已研究的配方。放入知识核心，像在奥术工作台上一样摆放材料，确认产物正确后保存。把核心带到 <ItemLink id="thaumicenergistics_ce:arcane_assembler" />，安排好物品和 Vis 的供给，再提交合成任务。
