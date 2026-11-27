---
navigation:
  title: 奧術編碼
  position: 45
  parent: thaumicenergistics_ce:index.md
  icon: thaumicenergistics_ce:arcane_encoding_terminal
item_ids:
- thaumicenergistics_ce:arcane_encoding_terminal
- thaumicenergistics_ce:wireless_arcane_encoding_terminal
- thaumicenergistics_ce:knowledge_core
---

# 奧術編碼

<ItemLink id="thaumicenergistics_ce:arcane_encoding_terminal" /> 把奧術工作臺的配方記進知識核心。它取代的是早期的知識編碼器：產出一樣，但材料按工作臺的擺法攤在九宮格裡，不必一件一件塞進機器槽位。

無線版 <ItemLink id="thaumicenergistics_ce:wireless_arcane_encoding_terminal" /> 是同一個終端裝在口袋裡。九宮格、核心與選中項都記在物品上，關掉介面走開再打開，東西還在原處。

## 左右兩半

左半邊是奧術工作臺那樣的九宮格，旁邊是產物預覽。右半邊是核心面板：3 欄 × 7 列，逐條列出這個核心裡已有的樣板，核心槽就在面板正下方。

## 放入知識核心

把 <ItemLink id="thaumicenergistics_ce:knowledge_core" /> 放進右側面板下方的核心槽。沒有核心時編碼按鈕會拒絕並提示，九宮格仍然可以用來試擺配方。

## 編碼

1. 按奧術工作臺的擺法把材料放進九宮格。
2. 箭頭右邊的框裡顯示產物，框上方的讀數就是這條樣板要記下的 Vis。
3. 配方收的是標籤或者裝滿的容器時，打開旁邊那兩個開關，樣板就會認下整組材料，而不只是顯示出來的那一個。
4. 按下編碼按鈕。成功時九宮格清空，核心裡多一條記錄。

編碼受研究門檻限制：還沒解鎖的配方會被拒絕，和當年知識編碼器一樣。

## 選中與刪除

點右側的樣板槽即可選中，外面會亮起一圈彩色邊框。編碼格空著時再按一次編碼按鈕，就是刪除選中的那條記錄。左上角的小叉只清空編碼格，不會動核心。

編碼與刪除共用同一個按鈕是有意為之：一次點擊不會同時做兩件事。

## JEI

在 JEI 裡對著一條奧術配方按 `+`，九宮格會被直接填好。材料不會被搬走——九宮格記的是「要編什麼」，不消耗它——編好的樣板照常留在核心裡。

## 接入網路

裝在電纜上時，終端取用所在網路的存量。作為無線物品，它需要一條鏈路：在無線存取點綁定，或者裝上量子橋接卡與配對奇點，從任何地方連到已供電的量子橋。

裝了 AE2WTLib 的通用終端時，這臺終端是其中一個可切換的模式。
