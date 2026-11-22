---
navigation:
  title: 用 ME 匯流排輸送源質
  position: 20
  parent: thaumicenergistics_ce:automation.md
  icon: thaumicenergistics_ce:essentia_access_card
item_ids:
- thaumicenergistics_ce:essentia_access_card
---

# 用 ME 匯流排輸送源質

先決定源質要留在哪一邊。將 <ItemLink id="thaumicenergistics_ce:essentia_access_card" /> 裝到匯流排上，再接到源質容器可以互動的一面。

- <ItemLink id="ae2:import_bus" /> 將源質抽入 ME 儲存。
- <ItemLink id="ae2:export_bus" /> 將已設定的源質從網路送入容器。
- <ItemLink id="ae2:storage_bus" /> 讓網路直接存取容器庫存，源質仍留在容器內。

## 範例：把罐內的源質抽進元件

讓裝有存取卡的輸入匯流排貼住罐子。確認匯流排在線，且網路有能接收該源質的元件。比對罐子與終端的數量，罐子減少多少，網路便應增加多少。若設了過濾條件，其他要素的罐子不應被抽取。儲存匯流排則只在實際存取時改變罐內數量。
