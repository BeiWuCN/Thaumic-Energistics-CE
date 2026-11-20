---
navigation:
  title: 用 ME 总线输送源质
  position: 20
  parent: thaumicenergistics_ce:automation.md
  icon: thaumicenergistics_ce:essentia_access_card
item_ids:
- thaumicenergistics_ce:essentia_access_card
---

# 用 ME 总线输送源质

先决定源质应留在哪一边，再选总线。给总线装上 <ItemLink id="thaumicenergistics_ce:essentia_access_card" />，并让它贴住源质容器可交互的一面。

- <ItemLink id="ae2:import_bus" /> 将源质从容器抽入 ME 存储。
- <ItemLink id="ae2:export_bus" /> 把配置好的源质从网络送入容器。
- <ItemLink id="ae2:storage_bus" /> 让网络直接访问容器库存，源质仍留在容器里。

## 示例：把罐里的源质抽进元件

让装有访问卡的输入总线贴住罐子。确认总线在线，而且网络里有能接收这种源质的元件。打开源质终端，对照罐子和网络的读数：罐子少多少，库存便应增加多少。若设置了过滤，装有其他源质的罐子应保持不变。

存储总线的行为不同。终端能显示罐内库存，但只有真正存取时，罐里的数量才会变化。
