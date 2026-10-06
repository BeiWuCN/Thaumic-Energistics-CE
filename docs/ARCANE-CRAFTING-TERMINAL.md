# 奥术合成终端（Arcane Crafting Terminal）

本文件记录**当前工作树**里奥术合成终端的事实，事实均带 `路径:行号`。行号会随改动漂移；改过相关代码后请重新核对。

## 一、形态：没有方块

`src\main\java\thaumicenergistics_ce\block\` 下没有 `BlockArcaneCraftingTerminal`。终端只有两种形态，**共用同一个菜单类与屏幕类**：

| 形态 | 入口 | 注册 |
| --- | --- | --- |
| 电缆部件 | `part\PartArcaneCraftingTerminal.java:49` `extends AbstractTerminalPart implements ArcaneTerminalHost` | `init\ModItems.java:93`（`ItemArcaneCraftingTerminal`） |
| 无线物品 | `item\ItemWirelessArcaneCraftingTerminal.java:39` `extends WirelessTerminalItem implements ArcaneTerminalLink`，`:41 POWER_CAPACITY = 200_000` | `init\ModItems.java:163` |

- 菜单：`menu\MenuArcaneCraftingTerminal.java:40` `extends MenuEssentiaTerminalBase implements ICraftingGridMenu, InternalInventoryHost`。
- 屏幕：`client\gui\ScreenArcaneCraftingTerminal.java:30`；`client\ClientSetup.java:133` 与 `:150` 两处都用它。
- 菜单类型：`init\ModMenuTypes.java:66`（有线，host = `ITerminalHost`）与 `:103`（无线，同一菜单类，host 换成 `IPortableTerminal`）。
- 研究条目：`src\main\resources\data\thaumicenergistics_ce\thaumaturge\research_entry\arcane_terminal.json`。

## 二、槽位

常量在 `part\PartArcaneCraftingTerminal.java:83-89`：`GRID_SIZE = 9`、`WAND_SLOT = 0`、`CRYSTAL_SLOTS = 6`、`CRYSTAL_COLUMN = 3`。
菜单添加顺序（`menu\MenuArcaneCraftingTerminal.java:107-144`）：九格合成网格 → 1 个法杖槽 → 3+3 晶体槽 → 结果槽 → 玩家背包。

| 槽 | 语义 | 说明 |
| --- | --- | --- |
| 九格 | `SlotSemantics.CRAFTING_GRID` | `:107` |
| 法杖 | `SlotSemantics.STORAGE` | `:116`；AE2 没有"工具"语义 |
| 晶体（左 3 / 右 3） | 自定义 `THAUMICENERGISTICS_CRYSTALS_LEFT` / `..._RIGHT`（`:46` 注册） | `menu\slot\CrystalSlot.java:16` 每个槽钉死一个原质要素，`mayPlace` 调 `TcWorkbench.isValidCrystal` |
| 结果 | `SlotSemantics.CRAFTING_RESULT` | `:144` |

两条容易踩的语义：

- **网格里的晶体不能当支付**（`part\PartArcaneCraftingTerminal.java:57`）：会被同时算成材料与支付，`ArcaneShapedRecipePattern.matches` 会拒掉每一个需要晶体的配方。晶体必须放进六个晶体槽。
- 法杖槽与晶体槽的过滤器（`:114-128`）拒绝杂物；放错东西会让晶体需求被读成"不可支付"。

## 三、Vis 与灵气支付

- 价格：`arcane\ArcaneVisCost.java:14`。`crystalVis = primalCrystals * 2`（`ThEArcanePattern.java:42 CRYSTAL_SUBSTITUTE_VIS = 2`）；
  `totalVis = max(0, baseVis) + crystalVis`；有晶体时再乘 `CRAFT_AURA_SURCHARGE = 1.25`（`:45`）并向上取整。`baseVis` 来自工作台灵气，电缆自己没有。
- 支付有两条路（`part\PartArcaneCraftingTerminal.java:209-239 supplyAura`）：
  - 装了 vis 连接卡 → `TerminalAuraPayment.payAura`，直接抽周围灵气；
  - 没装 → 取网格的 `IEnergyService`，按 `AE_PER_VIS = 1_000.0`、`CENTIVIS_PER_VIS = 100`（`TerminalAuraPayment.java:24,26`）用 AE 买。
  - 两个 pass 的契约：先模拟一次、再真实扣一次；失败不收费。
- `TerminalWorkbenchVis.java:17`：没有它什么都做不了，因为 `baseVis` 只从一个工作台的灵气来。

## 四、合成事务：store 属于终端

- `arcane\TerminalArcaneCraftingStore.java:21` 是 `IArcaneCraftingStore` 在全项目里**唯一**的实现。它直接拿终端的三个容器（网格 / 晶体 / 法杖）当自己的库存，所以**付钱用的是终端格子里的东西，不是网络**。
- 项目里 `consume` 没有调用点：`menu\slot\ArcaneCraftingResultSlot.java:110` 把 store 交给库的 `ArcaneCraftingTransaction.craft(workbenchContext(), server, input, store, false)`。
  store 会被调用两遍（一遍 `simulate = true`、一遍真实），**只有第二遍真取东西** —— 这就是"被拒的合成零代价"的来源。
- 结果槽 `mayPickup` 恒为 false（`menu\slot\ArcaneCraftingResultSlot.java:59`）：每一次取出都必须走会扣费的 `doClick`。

## 五、研究门

- `arcane\ArcaneResearchGate.java`：`isGated(research)` 就是 `research != null`；`gate(...)` 返回 `Optional<ResearchGate>`。
- pattern 侧：`ThEArcanePattern.java:125` `isResearchGated()`、`:129` `gate()`；`arcane` 目录里没有调用点。
- **唯一的消费点**在 `blockentity\inscriber\InscriberResolution.java:203`（`ResearchGate.passes(player, gate)`）。
  终端路径上项目只看到库里枚举 `Failure.RESEARCH_LOCKED`；具体判定规则在 Thaumaturge 库内。

## 六、JEI 转移

- `integration\jei\ThEJeiPlugin.java:41-52` 注册三个 handler：知识铭刻器 + `ArcaneJeiRecipeType.arcane()`、两个终端 + 同一 recipe type、以及原版 `RecipeTypes.CRAFTING`。
  一个 handler 同时服务有线与无线（`:53`），分开注册只会互相顶掉。
- 两个终端的处理器（`integration\jei\ArcaneCraftingRecipeTransfer.java`、`CraftingRecipeTransfer.java`）都返回 `getMenuType() = Optional.empty()`：
  wired 与 wireless 共用菜单类，点名一个会让另一个没有转移按钮。
- 槽位用 **AE2 的 `SlotSemantics`**（`CRAFTING_GRID` 9 个 + `PLAYER_INVENTORY`），不是 JEI 的 `InventorySlotType`（全项目 grep 该名字 0 命中）。
- **已知缺口**（`integration\jei\ArcaneCraftingRecipeTransfer.java` 的类 javadoc）：六个晶体槽与法杖槽不参与 JEI 转移，要玩家自己放。
  相关：模板取"有库存的那个变体"而不是配方里排第一的（同一段 javadoc，包不认 tag）；槽位清单只有 `CRAFTING_GRID`（`:75`）与 `PLAYER_INVENTORY`（`:81`）。

## 七、已知过时说法（勿再引用）

`PLAN.md:428` 记载：旧文档 `docs\ARCANE-CRAFTING-TERMINAL.md:93-112` 及其 `_refs\remote-clone` 源码声称"JEI transfer works"、且引用的 `ArcaneRecipeTypes.arcane()` 签名与工作树不同 —— 那部分不可信，以本文件为准。
