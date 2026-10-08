# Thaumic Energistics: CE —— 26.1.2 移植调整记录

本文件记录 26.1.2 / NeoForge 线相对上游移植提交做的**全部改动**，方便回同步到 1.21.1。
每项都标了分类：

- **A 新功能** —— 1.21.1 没有这东西，值得搬过去。
- **B 移植补齐** —— 1.21.1 本来就是对的，26.1.2 移植时弄丢了，这里补回来。
- **C 26.1.2 专属** —— 换 API 的适配，不要搬。
- **D 诊断日志** —— 排障用，可随时关。

---

## 0. 现状

| 项 | 值 |
| --- | --- |
| 目标 | Minecraft 26.1.2 / NeoForge 26.1.2.112 / Java 25（`build.gradle:15 toolchain.languageVersion = JavaLanguageVersion.of(25)`） |
| 依赖 | AE2 `26.1.13-beta`、Jade `26.1.8+neoforge`、GuideME `26.1.15-beta`、Curios `15.0.0+26.1.2` |
| Thaumaturge | `gradle.properties:109 thaumaturge_commit=de63133232483fce39a8e9fc1499f8f4e7e82379`，本机自建 `libs/thaumaturge-26.1.2-NeoForge-1.0.2.jar`（maven 上没有，不能分发） |
| 版本号 | `gradle.properties:53 mod_version=2.7.3.64`（原 2.6.1.12） |
| git 基线 | HEAD = `f7f1436 Restore the essentia terminal's fill and empty gestures`（父 `d5b8f90 Swap in the 1.21.1 line's logo`、`5fa57b8 Port the mod to Minecraft 26.1.2 on NeoForge`） |
| 未提交改动 | 40 个文件（36 modified + 4 untracked），`786 insertions(+), 182 deletions(-)` |
| 构建 | `mc_gradle build` 全绿，警告只剩旧账（`ScreenArcaneAssembler.java:220` 的 `getSlotUnderMouse()` 已过时） |

本次新增的 4 个文件（未跟踪）：

```
src\main\java\thaumicenergistics_ce\client\gui\Ae2SlotHighlight.java
src\main\java\thaumicenergistics_ce\menu\slot\WandSlot.java
src\main\java\thaumicenergistics_ce\arcane\TerminalCrystalPayment.java
src\main\java\thaumicenergistics_ce\compat\thaumaturge\TcArcanePayment.java
```

---

## A. 新功能（建议同步 1.21.1）

### A1. AE2 风格槽位高亮

需求：机器的槽位悬停框要 AE2 风格，不要原版白块。

- 新增 `client\gui\Ae2SlotHighlight.java`：`FRAME_COLOUR = 0xFFDAFFFF`、`FILL_COLOUR = 0x669CD3FF`（取自 AE2 `AEBaseScreen.renderSlotHighlight` 的 `-2424833` / `1721553919`），`render(GuiGraphicsExtractor, Slot)` = `isHighlightable()` 早退 → 尺寸（`ResizableSlot` 取 `getWidth()/getHeight()`，否则 16×16）→ 四条边 `horizontalLine(x, x+w, y-1)`、`horizontalLine(x-1, x+w, y+h)`、`verticalLine(x-1, y-2, y+h)`、`verticalLine(x+w, y-2, y+h)` → `fillGradient(x, y, x+w, y+h, FILL, FILL)`。
- 7 个界面覆写 `extractContents(GuiGraphicsExtractor, int, int, float)`：`super` 之后 `graphics.pose().pushMatrix(); translate(leftPos, topPos); Ae2SlotHighlight.render(graphics, hoveredSlot); popMatrix();`
  - 原版基类：`client\gui\ScreenArcaneAssembler.java`、`ScreenKnowledgeInscriber.java`、`ScreenEssentiaVibrationChamber.java`、`ScreenDistillationEncoder.java`
  - AE2 基类：`client\gui\ScreenEssentiaCellWorkbench.java`、`ScreenEssentiaStorageBus.java`、`ScreenEssentiaTerminalBase.java`
- 已知限制：26.1.2 原版的高亮是 `AbstractContainerScreen` 的**私有**方法贴 sprite（`container/slot_highlight_back` / `_front`，24×24，半透明白），模组既覆写不了也拦不住 ⇒ 现在是「原版白块在下、AE2 框在上」，底色略偏亮。要彻底去掉白块两条路（**用户尚未选**）：① 四个菜单的槽位换成 `isHighlightable() = false` 的子类（玩家背包槽是 AE2 建的，得连 `addPlayerInventory` 一起替）；② 覆盖 `assets/minecraft/textures/gui/sprites/container/slot_highlight_*.png`（全局生效，连原版箱子和 AE2 自己的界面一起变）。
- AE2 侧旁证：`AEBaseScreen.renderSlotHighlight` 在 26.1.13-beta 已经是死代码（整个 jar 只有声明、没有调用点），AE2 自己的界面也只剩原版白块。

### A2. 奥术合成终端：水晶优先付费

原机制是「法杖 vis 优先、不够才动晶体」（Thaumaturge `content\workbench\WorkbenchPayment.calculateCrystalNeeds`：法杖 → 外部 vis 源 → 晶体，每条 primal 独立判定）。用户要反过来：**先扣终端六槽里的魔力水晶，不够/没有才掏法杖**；法杖 vis 用尽而水晶够，再回去用水晶。

- 新增 `arcane\TerminalCrystalPayment.java`（`@EventBusSubscriber(modid = ThEIds.MODID)`，游戏总线）+ `compat\thaumaturge\TcArcanePayment.java`。
- 监听 `ArcaneCraftCostEvent`，只认 `event.getWorkbench() instanceof TerminalArcaneCraftingInput`（全项目唯一构造点 `menu\slot\ArcaneCraftingResultSlot.java:198`；组装机走 `arcane\SyntheticArcaneInput.java`，不受影响）。
- 换算：**1 颗水晶 = 200 centivis** = `WandEconomy.CRYSTAL_SUBSTITUTE_VIS(2) × CENTIVIS_PER_VIS(100)`；对每个 primal `take = min(wandShare / 200, 六槽里该 aspect 的颗数)`，余数留在法杖（`left = wandShare - take × 200`），`take` 写进 `crystalsNeeded`；有改动才 `event.setCost(new ArcaneCraftCost(false, wandCentivis, crystalsNeeded, cost.auraVis(), true))`。
- `paidFromWand=false`、`affordable=true` 不会绕过检查：Thaumaturge 在 `applyCostEvent` 里会自己复查 `hasCrystals(...)`。
- **决策**：灵气价保留规划器给的 `cost.auraVis()`，不套工作台晶体路径的 `CRAFT_AURA_SURCHARGE`(+25%) —— 用户要的是「这份 vis 谁出」，不是改价。要严格按工作台计价，把 `setCost` 第 4 参换成 `TcArcanePayment.auraVisForCrystals(recipe, player)`（已写好、刻意没接上，javadoc 有说明）。
- 终端只有 `IWorkbenchAuraSource`、没有 `IWorkbenchVisSource`（`arcane\TerminalWorkbenchVis.java:27`），所以 `sourceCentivis` 恒空，不用管。
- 1.21.1 可一比一搬：那边 Thaumaturge 有同款事件与输入类（`api\recipe\ArcaneCraftCostEvent.java:19`、`content\workbench\WorkbenchPayment.java:32`（`applyCostEvent` :108）、`content\wands\WandEconomy.java:16`、`arcane\TerminalArcaneCraftingInput.java:32`，构造点 `menu\slot\ArcaneCraftingResultSlot.java:261`）。

### A3. 费用条带渲染水晶图标

- `network\ArcaneCraftCostPayload.java` 加第三字段 `List<CrystalCost> crystals`（`record CrystalCost(Identifier aspect, int count)` + `CRYSTAL_COST_CODEC`），`of(int, Map<ResourceKey<IAspect>,Integer>, AspectList)` 用 `crystalsNeeded.entries()` 过滤 `amount() > 0`。
- `menu\MenuArcaneCraftingTerminal.java:260` 传 `cost.crystalsNeeded()`。
- `client\gui\ScreenArcaneCraftingTerminal.java`：要素仍占条带右端，晶体从右往左接着排（`EssentiaCrystals.create(aspect, 1)` + `graphics.item(...)`，颗数文字同要素画法）；格宽改成随手头件数收缩 —— `wanted = max(PartArcaneCraftingTerminal.CRYSTAL_SLOTS, costs.size() + crystals.size())`、`chip = max(min(条带高, 条带宽/wanted), min(6, 条带高))`，六件以内保持原来的 11 px 不漂。
- 1.21.1 的 `drawFG` **只画要素、从不画晶体** ⇒ 新功能。

### A4. 水晶槽：按住 Shift 才显示槽位提示

- `menu\slot\CrystalSlot.java`：`setEmptyTooltip(() -> shiftHeld() ? List.of(Component.translatable("gui.thaumicenergistics_ce.arcane_terminal.crystal_required", aspectName(required))) : List.of())`；`shiftHeld()` 用 `Minecraft.getInstance().hasShiftDown()`（26.1.2 已经没有 `Screen.hasShiftDown()`）。
- 常驻提示会盖住旁边格子；AE2 的 `AppEngSlot.getCustomTooltip` 只在画提示时调 supplier，所以客户端类只在客户端解析。
- lang 键：`gui.thaumicenergistics_ce.arcane_terminal.crystal_required`（两个 lang 都有）。

### A5. 替身容器一律拒收

- 菜单 `part == null`（终端未绑定）时用菜单级替身容器 `gridFallback` / `crystalFallback` / `wandFallback`，没人写存档 ⇒ 收下等于吃掉。
- `menu\MenuArcaneCraftingTerminal.java`：三个替身容器装 `placeholdersTakeNothing` 过滤器（`IAEItemFilter.allowInsert` 恒 false），配 `refusePlaceholderInsert(Player)`（40 tick 节流 + 快捷栏提示 `gui.thaumicenergistics_ce.arcane_terminal.not_linked`）。
- 不会挡住同步：AE2 的 `AppEngSlot.mayPlace` 走 `InternalInventory.isItemValid`，而 `set`/`initialize` 走 `setItemDirect` **绕过过滤器**。
- 1.21.1 有同一套替身容器、没有这个闸门。

### A6. 源质手势的两处新守卫

（语义本身是 B2 恢复的，这两处是 26.1.2 新加的）

- `client\gui\ScreenEssentiaTerminalBase.java` 的 `slotClicked`：光标拿着容器时挡 `RepoSlot` 点击，但**放行 `ContainerInput.QUICK_MOVE`**（shift 左键取整排）。
- 同文件新增 `mouseScrolled` 覆写：光标拿着容器时挡空格 `RepoSlot` 的 shift 滚轮（返回 true）。
- 1.21.1 的守卫是 `if (slot instanceof RepoSlot && cursorIsContainer()) return;` —— 没有 QUICK_MOVE 豁免，也没有滚轮守卫。

### A7. 法杖槽的放置闸门

- 新增 `menu\slot\WandSlot.java`：`mayPlace = super.mayPlace(stack) && PartArcaneCraftingTerminal.isWand(stack)`；替身容器 `wandFallback` 同时补同款过滤器。
- 原先没有闸门（容器过滤器只管「进容器」，玩家点击走 `mayPlace`）。1.21.1 也有这个洞。

### A8. 装备槽空图标

- `menu\MenuArcaneAssembler.java`：gear 槽匿名类覆写 `getNoItemIcon()`，返回 `InventoryMenu.EMPTY_ARMOR_SLOT_HELMET / CHESTPLATE / LEGGINGS / BOOTS`（新私有方法 `emptyGearIcon(int index)`，0..3 与 `inventory\GearSlots.equipmentSlot` 同序 = HEAD/CHEST/LEGS/FEET）。
- 原版只在「槽为空且 active」时贴这张 sprite，不必自己判空。
- 1.21.1 是同一套四个装备槽、同样没图标（`menu\MenuArcaneAssembler.java:244-249`），差别只是那边 sprite id 是 `ResourceLocation`。

### A9. 傀儡被收起时先把背包摘下来

- 现象：空手潜行右键收傀儡（Thaumaturge 的 `pickUpGolem`）时，傀儡身上的背包/链接/贴皮被吞。
- 根因：Thaumaturge `content\golem\EntityThaumaturgeGolem.java:655-668 pickUpGolem`（`mobInteract` :627-630 触发）只把 `GOLEM_PROPERTIES` / `GOLEM_XP` 写进 `TCItems.GOLEM_PLACER` 再 `discard()` 掉傀儡，而背包链路存在傀儡的持久化数据里（`ThEWifiBackpackLink` / `ThEBackpackSkin` / `ThEBackpackFacade`）⇒ 一起没。TECE 的 `golem\GolemBackpackHandler.onEntityInteract` 原来在 `held.isEmpty()` 直接 return。
- 修法（`golem\GolemBackpackHandler.java`）：空手 + 潜行 + 服务端 ⇒ `salvage(golem)`；抽出 `releasePack(golem, link, returnFacade)` 给 `salvage` 与 `dismantle` 共用（落一颗带回 `AEComponents.WIRELESS_LINK_TARGET` 的背包 + 方块，再 `clear(golem)`）；`dismantle` 保留「创造模式免退方块」，收傀儡一律还方块；**不取消事件**，Thaumaturge 照常收起傀儡。
- 1.21.1 有同一个洞（`golem\GolemBackpackHandler.java:84-86` 早退；那边 `pickUpGolem` 在 `EntityThaumaturgeGolem.java:681`）。
- 未决：傀儡**死亡**（`die` → `dropCarried`）时背包也是直接没，要不要一并还。

### A10. 概率之箱给奖提示改用「原始 / 复合」

- 文案键 `block.thaumicenergistics_ce.gacha_box.payout`：中文 `获得了 %s 点研究点（原始 %s / 复合 %s）`、英文 `Gained %s research points (%s primal / %s compound)`；原来写的是「观测 / 理论」，那是 Thaumaturge 的知识类型名，看不出实际给了什么。
- 依据：`content\research\ResearchGrants.java` 的 `grantConvertedKnowledge(player, type, amount)` 是逐点灌**要素池** —— `type == KnowledgeType.THEORY ? randomDiscoveredCompound(player) : randomPrimal(player)`，即第一个数（observation）进**原始**要素池（随机元始要素），第二个数（theory）进**复合**要素池（随机一个已发现的复合要素；一个都没发现时退回元始）。
- 文件：`resources\assets\thaumicenergistics_ce\lang\zh_cn.json:267`、`...\en_us.json:267`、`blockentity\gachabox\GachaPayout.java`（类 javadoc 补了池子映射说明）。纯资源改动，`mc_gradle build` 绿。
- 1.21.1 侧若也有概率之箱，同步这两条文案即可。

### A11. 机器方块补战利品表（镐子挖回机器本体）

- 现象：26.1.2 与 1.21.1 **两棵树的 jar 里都没有任何 loot table**（实测两边产物 jar 里 `loot` 条目各 0 条；源码里没有 `GatherDataEvent` / `LootTableProvider` / `BlockLootSubProvider`，`src/generated/resources` 从未生成过；方块也没有 `getDrops` 覆写）⇒ 用镐子挖机器**什么都不掉**。机器本来只能靠 AE2 石英扳手收回：潜行 + 右键走 `appeng\hooks\WrenchHook.onPlayerUseBlock`（字节码里判 `instanceof appeng.blockentity.AEBaseBlockEntity`）→ `AEBaseBlockEntity.disassembleWithWrench`，而 TCE 的方块实体正好继承 `appeng\blockentity\grid\AENetworkedBlockEntity`。
- 修法：新增 12 张 `dropSelf` 战利品表 `src\main\resources\data\thaumicenergistics_ce\loot_table\blocks\<id>.json`（`minecraft:block` + `minecraft:survives_explosion` + `minecraft:item`，写法照 26.1.2 原版 `data\minecraft\loot_table\blocks\*.json` 与 AE2 自己的表），覆盖 `arcane_assembler`、`knowledge_inscriber`、`essentia_cell_workbench`、`essentia_vibration_chamber`、`alchemy_provider`、`infusion_provider`、`distillation_encoder`、`occult_monitor`、`alchemy_provider_connection`、`gacha_box`、`gacha_box_aggregator`、`alkusure86fumo`。
- 蕴魔观测器：破坏时**没有**任何拦截（可以直接挖），槽里的魔导手册由 `blockentity\occultmonitor\BlockEntityOccultMonitor.java:269-294 preRemoveSideEffects` → `bookSlot.drop()` 掉在原地（1.21.1 对应 `BlockInfusionMonitor.onRemove`）；配合新的战利品表即「手册随方块一起掉落」。
- 机器内容物不受影响：各机器原有的 `preRemoveSideEffects` / `dropContents` 照旧负责内容。
- 同步 1.21.1：目录名同为 `data\thaumicenergistics_ce\loot_table\blocks\`（1.21 起数据包目录改单数），12 个 JSON 可原样复制；那边的方块 id 与本侧一致。
- 校验：`mc_gradle build` 绿；产物 jar 里 12 条 `data/thaumicenergistics_ce/loot_table/blocks/*.json` 已在位，全部 JSON 通过解析。

### A12. 源质访问卡手册文案补一句

- 手册「源质访问卡」第 2 阶段的正文（键 `tc.research_text.ESSENTIAACCESSCARD.stage.2`，`assets\thaumicenergistics_ce\lang\zh_cn.json:253` / `en_us.json:253`）在第一个句号前补上结论句：中文「…在配置行上标出想要的要素，**它就会允许这类要素进入ME网络**。」，英文「…mark the aspects you want on its config row, **and it lets those aspects into the ME network**.」
- 1.21.1 成熟线**没有**这个键（那边的研究文案结构不同），所以无需同步。

### A13. 奥术合成终端手册第 2 页重写

- 键 `tc.research_text.ARCANECRAFTINGTERMINAL.stage.2`（26.1.2 在 `assets\thaumicenergistics_ce\lang\zh_cn.json:130` / `en_us.json:130`，1.21.1 在 `:149`）原来是「…也能做：法杖放进…，材料从…取，缺的…」这样的冒号罗列句，与同页邻居（无线奥术合成终端）的整句叙述风格不一致。
- 重写为整句：中文「把法杖放进终端的法杖槽，九宫格里的材料便由网络供给，缺的那点灵气也由网络补上。这次合成要付多少灵气、要不要动用水晶，终端会在你动手前先算给你看。」，英文同义。两条线已同步（26.1.2 `a341208`、1.21.1 `90bba9a`）。

### A14. 模型补齐 `particle` 槽（消掉日志里的 Missing texture references）

- 26.x 的模型系统要求每个自带 `elements` 的模型自己解析出 `particle` 槽，1.21.1 时代没这要求，照抄过来的 12 个模型因此各报一条 `Missing texture references in model …: particle`（`block/distillation_encoder` 另有 `#all` 未解析）。
- 改法：11 个部件/物品模型显式补 `"particle"`（值就是各自已有的那张贴图变量，源质等级发射器与通量传输接口用 `#indicator` / `#front` / `#emitter`；终端类用 `#lightsMedium`），`models/block/distillation_encoder.json` 补 `"all"`（该模型自带六面 elements，`all` 不参与绘制，只为消 resolver 告警，同 `infusion_provider.json` 的做法）。
- 两条线已同步（26.1.2、1.21.1 `0f02ffa`）；日志里剩的那条 `minecraft:block/block: particle` 来自 Thaumaturge 自己的 46 个模型，与本模组无关。

---

## B. 移植补齐（1.21.1 本来就对）

| # | 问题 | 文件 |
| --- | --- | --- |
| B1 | 提示跑到聊天栏：改回快捷栏 `sendOverlayMessage`（1.21.1 用的是 `displayClientMessage(msg, true)`） | `block\BlockGachaBox.java:119,164,178`、`blockentity\gachabox\GachaPayout.java:22`、`GachaBoxBreakGuard.java:33` |
| B2 | 源质手势被改成「容器空=取/满=倒」，改回 1.21.1 的**左键取 / 右键倒** | `client\gui\ScreenEssentiaTerminalBase.java` |
| B3 | JEI「+」转移只取每格第一个变体：补回 `pickSuppliable` / `supplyOf` / `asksForSomething`（注释留着、逻辑丢了）。26.1.2 的 `Ingredient` 没有 `of(ItemStack)`，用 `Ingredient.of(variant.getItem())` | `integration\jei\ArcaneCraftingRecipeTransfer.java` |
| B4 | 机器名字显示成键名：8 处补 `.useBlockDescriptionPrefix()`（26.1.2 的 `BlockItem` 不覆写 `getDescriptionId()`，名字来自 `Item.Properties.descriptionId`） | `init\ModItems.java` |
| B5 | 谐振仓三行读数看不见：`0x404040` → `TEXT_COLOUR = 0xFF404040` | `client\gui\ScreenEssentiaVibrationChamber.java` |
| B6 | 6 个配方对齐 1.21.1（`essentia_access_card`、`occult_monitor` 走注魔；4 个 `essentia_cell_*` 走 `["aba","bcb","ddd"]`） | `src\main\resources\data\thaumicenergistics_ce\recipe\*.json` |

无线连接器的报告**保持聊天栏**（1.21.1 就是 `false`），不用改。

---

## C. 26.1.2 专属（换 API 的适配，不要同步）

| # | 内容 | 文件 |
| --- | --- | --- |
| C1 | 按钮字色 `0x000000` → `0xFF000000`（`GuiGraphicsExtractor.text` 对 alpha=0 整句不画） | `client\gui\EncodeButton.java`、`InscriberButton.java` |
| C2 | 客户端没有完整配方表：新增 `recipes(Level)`（无服务端返回 null）+ 4 处 null 闸；`InscriberPreview`、`MenuKnowledgeInscriber.broadcastChanges()`（先 `updatePreview()`） | `arcane\ArcanePatternLookup.java`、`menu\InscriberPreview.java`、`menu\MenuKnowledgeInscriber.java` |
| C3 | Jade 26.1.8 的 `ProgressStyle` 不收颜色 ⇒ 颜色挂在 `ProgressView.Part.of(ratio, 0xFFAA0000)`，构造改四参；绘制图标用元素自己的 `getX()/getY()`，不能用提示框原点 | `client\jade\AlchemyProviderTooltip.java`、`client\jade\InfusionProviderTooltip.java` |
| C4 | 删掉 `ILinkStatus.ofDisconnected("...not_bound")` 覆写（它让 `MEStorageMenu.canInteractWithGrid()` 变 false，界面看得见却动不了）；`getLinkStatus()` 只观察并记录状态变化；新增 `logCharge()` | `item\WirelessArcaneCraftingTerminalMenuHost.java` |
| C5 | 纯 API 换代（语义不变）：注魔供应器 `accepts/fill/drain/amountOf`（旧 `addToContainer/takeFromContainer/containerContains`）、`SlotRangeItemHandler` → `ResourceHandler<ItemResource> extends SnapshotJournal<ItemStack[]>`、`FocusEffectAEWrench` → `AbstractEffectBehavior`、`TerminalArcaneCraftingStore` → 事务式 `consume(Consumption, TransactionContext)`、`Block.onRemove` → 方块实体 `preRemoveSideEffects(BlockPos, BlockState)` | 分散在各 `blockentity\` / `focus\` / `arcane\` / `menu\slot\` |
| C6 | 机器物品提示按要求删除：`item\ItemMachineBlock.java` 删掉，8 台机器回到 `BlockItem`（`.useBlockDescriptionPrefix()` 保留），两个 lang 各删 14 条 `tooltip.thaumicenergistics_ce.*.desc` / `.hint` | `init\ModItems.java`、`lang\zh_cn.json`、`lang\en_us.json` |

---

## D. 诊断日志（排障用，可关）

- `menu\slot\CrystalSlot.java`：限速的 `[arcane] 水晶槽 …`（水晶放不进时）。
- `essentia\EssentiaFillHelper.java`：9 条填充/倒出日志 + 电力探针（`extractAEPower(1000, Actionable.SIMULATE, PowerMultiplier.CONFIG)`）。
- `item\WirelessArcaneCraftingTerminalMenuHost.java`：`[无线奥术合成终端] AE2 链接状态：…`（只在状态翻转时写）+ 电池电量翻转日志。
- `menu\MenuEssentiaTerminalBase.java` / `client\gui\ScreenEssentiaTerminalBase.java`：`[源质手势] …`。

顺带一个排查结论（无线终端「只能取源质、不能倒源质、网络物品拿不出也放不进」）：**根因是终端物品自己的电池没电**。AE2 的 `IPortableTerminal extends ITerminalHost, IEnergySource` ⇒ `MEStorageMenu.energySource` 就是终端物品，`StorageHelper.poweredInsert/poweredExtraction` 按可用电力裁剪、电力为 0 直接返回 0；取源质走 `storage.extract` 不经电力所以能成。创造模式只免待机耗电。实测：`connected` + 1000 AE 时一切正常。

---

## E. 待实测清单

1. AE2 风格悬停框（7 个界面）；白块要不要彻底去掉（见 A1 两条路）。
2. 水晶优先付费：六槽放对应水晶 + 法杖有 vis 合魔导透镜（六晶体 + vis 20）；再试「某要素只放 1 颗、配方要 2 颗」看部分抵扣。
3. 费用条带水晶图标（魔导透镜 / 元质电池类配方，应「右端一个要素、左边六颗对应颜色水晶」）。
4. 水晶槽按住 Shift 的提示。
5. 替身容器拒收提示（快捷栏 `…not_linked`）。
6. 源质手势：左键取 / 右键倒 / shift 左键取整排 / 滚轮守卫。
7. 法杖槽只收法杖。
8. 奥术装配室装备槽四个空图标。
9. 收起傀儡时背包与方块落回地面。
10. JEI 按库存挑变体（用带矿辞的奥术配方）。
11. 机台侧：蒸馏编码台 / 知识铭刻机按钮文字、知识记录仪开界面、炼金供应器 Jade 储备条颜色、谐振仓三行读数、8 台机器名字、6 个配方。
12. 战利品表：镐子直接挖 12 台机器应掉回机器本体；蕴魔观测器还要附带掉出槽里的魔导手册（创造模式挖不掉东西，请用生存模式测）。

---

## F. 未处理 / 待决策

- 水晶槽放不进六大水晶碎片（诊断与提示已上线，等 `[arcane]` 日志）。
- 谐振仓 GUI 贴图：现役界面全是代码手画（上游 javadoc 说那张图是 60×100 的机器正面，当 176 像素窗口用会空掉三分之二），用户未选是否换图。
- 是否恢复 1.21.1 的源质手势失败提示（`essentia\EssentiaFillHelper.java` 的 `tell` + 6 条 `gui.essentia.*` 文案）。
- 傀儡死亡时是否也把背包还回来。
- `MachineItemBandSelfTest` 在集成服务端 FAIL（唯一剩余失败项，未查）。
- `src\selftest\java` 的 498 行英文注释是否译成中文。
- 以上全部改动**尚未 git 提交**。

---

## G. 删除（对齐 2.0 上游）

### G1. 源质存储总线（`essentia_storage_bus`）整族移除

- 依据：上游 2.0 线（工作区根 `Thaumic-Energistics-CE-main`）**没有** `PartEssentiaStorageBus`，`item`/`part`/`menu`/`screen` 一律不存在；它的 `data\thaumicenergistics_ce\recipe\flux_transfer_interface.json` 注释逐字写着「Replaces the arcane workbench recipe this id was carried over with from the 1.12.2 build's ESSENTIA_STORAGE_BUS」—— 通量传输接口接管了旧总线那一格。26.1.2 树里两套并存，且存储总线在 26.1.2 **没有研究条目**（1.21.1 有 `thaumaturge\research_entry\essentia_buses.json`）、配方也没有 `research` 闸门 ⇒ 它会凭空出现在创造栏与 JEI 里。
- 删除清单：
  - Java（6）：`part\PartEssentiaStorageBus.java`、`item\ItemEssentiaStorageBus.java`、`menu\MenuEssentiaStorageBus.java`、`menu\MenuEssentiaBusBase.java`（只被前者使用）、`client\gui\ScreenEssentiaStorageBus.java`、`client\jei\EssentiaBusGhostIngredientHandler.java`（只被前者使用）。
  - 注册（6 处）：`init\ModItems.java`（`ESSENTIA_STORAGE_BUS` + import）、`init\ModMenuTypes.java`（menu type + 2 个 import）、`ThaumicEnergistics.java`（`registerPartCapabilities` 里的 `TRANSPORT` 注册）、`client\ClientSetup.java`（屏幕 + 2 个 import）、`init\ModCreativeTab.java`、`client\jei\ThEJeiClientPlugin.java`（幽灵处理器 + import）。
  - 资源（9）：`assets\…\ae2\parts\essentia_storage_bus.json`、`items\essentia_storage_bus.json`、`models\item\essentia_storage_bus.json`、`models\parts\essentia_storage_bus_{base,has_channel,off,on}.json`、`textures\part\essentia_storage_bus_face.png`、`data\…\recipe\essentia_storage_bus.json`。
  - 文案（2 键 × 2 语言）：`gui.thaumicenergistics_ce.essentia_storage_bus`、`item.thaumicenergistics_ce.essentia_storage_bus`。
- 副作用：1.21.1 成熟线仍有这族总线（源质输入/输出/存储），本侧删掉即与那边分叉 —— 这是**有意的设计对齐**，不是丢逻辑。`mc_gradle build` 绿（唯一警告仍是 `ScreenArcaneAssembler.java:220` 旧账）。
- 收尾（游戏内崩溃修复）：删除之后开发态一进世界就崩 —— `run`/`runClient` 会把 selftest 源集（实际目录 `_scratch\selftest`，见 `build.gradle:60`/`:101-102`）也编进运行时，而 `gradle build` **不编译** selftest，于是 `EssentiaTransportViewSelfTest` 里的旧字节码仍在断言 `PartEssentiaStorageBus` 的 `TRANSPORT` 注册 ⇒ `java.lang.NoClassDefFoundError: thaumicenergistics_ce/part/PartEssentiaStorageBus`，崩在 `ServerStartedEvent`（`run\crash-reports\crash-2026-10-08_15.35.59-server.txt`）。已删掉该断言、`expectPortOf(...)` 与失效 import（吸力/朝向断言保留）。
- 同族死代码一并清掉：`network\EssentiaBusConfigPayload.java` 与 `network\EssentiaBusReceiver.java`（全仓已无人实现 `EssentiaBusReceiver`）连同 `init\ModNetwork.java:10,57-60` 的注册、`_scratch\selftest\…\NetworkSelfTest.java` 的 `essentia_bus_config` wire id 与两条 roundtrip 用例（**wire id 由 12 条减为 11 条**，`.class` 里已零残留引用）。`mc_gradle build` 与 `compileSelftestJava` 全绿。
