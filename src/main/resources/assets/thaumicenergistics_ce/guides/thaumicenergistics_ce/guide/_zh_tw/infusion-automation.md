---
navigation:
  title: 自動化注魔
  position: 10
  parent: thaumicenergistics_ce:automation.md
  icon: thaumicenergistics_ce:infusion_provider
item_ids:
- thaumicenergistics_ce:infusion_provider
- thaumicenergistics_ce:occult_monitor
---

# 自動化注魔

<ItemLink id="thaumicenergistics_ce:infusion_provider" /> 讓祭壇直接從 ME 網路取用源質。<ItemLink id="thaumicenergistics_ce:occult_monitor" /> 能核對材料，並在注魔成功後發出紅石脈衝。

<GameScene zoom="3" interactive={true} background="transparent">
  <ImportStructure src="assets/infusion-setup.snbt" />
  <IsometricCamera yaw="205" pitch="30" />
  <LineAnnotation color="#9b78ff" from="1.5 2 5.5" to="3.5 3.5 2.5" alwaysOnTop={true} thickness="0.06" />
  <LineAnnotation color="#f0bd62" from="5.5 2 5.5" to="3.5 3.5 2.5" alwaysOnTop={true} thickness="0.06" />
</GameScene>

紫線表示供應器向矩陣供應源質，金線表示觀測器監視祭壇。拖曳場景可查看兩台裝置背後的線纜。

## 接入祭壇

1. 按魔導手冊搭建祭壇。將符文矩陣放在中央基座正上方兩格，用世界鹽讓四個斜角的柱子成形。材料基座對稱放置。
2. 將供應器放在矩陣 12 格以內，接入有供能與空餘頻道的 ME 網路。它按祭壇需求輸出源質，本身沒有緩衝，也不接收管道送來的源質。
3. 在同一矩陣 12 格以內接上觀測器，插入魔導手冊。附近有多座祭壇時，確認它目前監視的對象。

## 完整範例：元始杖芯

中央基座放銀樹杖柄。八個外圍基座放兩個元始魔力，以及黑曜石、寒霜、石英、甘蔗、烈焰和白骨杖柄各一個。讓兩個元始魔力相對擺放，六個杖柄填滿另外三對基座。

1. 準備風、火、水、地、秩序、混沌各 32 點，以及魔力 64 點，共 256 點。祭壇增益和整合包可能改變花費，以觀測器為準。
2. 確認觀測器識別到正確的配方且沒有報缺料。基礎不穩定度為 8，開始前請核對穩定度。
3. 用施法工具啟動矩陣。注魔停滯時，先查剩餘需求中缺的是哪一種源質。
4. 收到完成脈衝後，確認中央基座上有成品，取走後才放入下一批的催化物。

## 完成訊號與排錯

完成脈衝可以觸發外部收料或允許下一批上料。上料、啟動矩陣和取出成品是獨立步驟；先逐步驗證，再考慮連續執行。

- 找不到祭壇：檢查魔導手冊、頻道和與矩陣的距離。
- 缺少源質：檢查供應器及源質終端。裝在物品儲存裡的滿安瓿不算可用源質。
- 識別不到配方：在 JEI 核對中央物品、外圍材料與過量的物品。
- 沒有脈衝：觀測器只在它監視的祭壇成功完成注魔時發出訊號。
