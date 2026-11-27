---
navigation:
  title: 源質與奧術自動化
  position: 50
  parent: thaumicenergistics_ce:index.md
  icon: thaumicenergistics_ce:alchemy_provider
item_ids:
- thaumicenergistics_ce:alchemy_provider
- thaumicenergistics_ce:arcane_assembler
- thaumicenergistics_ce:knowledge_core
- thaumicenergistics_ce:arcane_encoding_terminal
---

# 源質與奧術自動化

先決定要由 ME 網路接手哪一步。

- [用 ME 匯流排輸送源質](essentia-buses.md)
- [自動化注魔](infusion-automation.md)
- [蒸餾樣板](distillation-patterns.md)

## 煉金

<ItemLink id="thaumicenergistics_ce:alchemy_provider" /> 會按相鄰相容設備的請求補充源質。配方改變時不必重新配置輸出過濾。若供料停滯，先去源質終端核對對應要素的庫存。

## 奧術裝配

在 <ItemLink id="thaumicenergistics_ce:arcane_encoding_terminal" /> 上編碼配方：按奧術工作臺的擺法放好材料，確認產物預覽無誤後按下編碼按鈕，樣板就寫進知識核心。將核心放入 <ItemLink id="thaumicenergistics_ce:arcane_assembler" />，接好物品與 Vis 供應，再送出合成任務。
