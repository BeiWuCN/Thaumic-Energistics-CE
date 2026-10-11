# TECE 2.7.4.4

## 26.1.2 / NeoForge

This update includes the unreleased 2.7.4.3 changes and the subsequent work for 2.7.4.4.

### Additions

- Add a server config for terminal access, essentia bus power costs, golem backpack transfers, arcane assembler speed and power use, essentia vibration chamber fuel duration, and gacha box settings. Example configs and a configuration guide are included in the repository.
- Let ME Import, Export and Storage Buses handle essentia when fitted with an Essentia Access Card. Import and Export Buses have six upgrade slots; Storage Buses keep their five native slots. Cards can use any supported upgrade slot, subject to AE2's normal card limits.
- Let both wireless terminals be equipped in Curios slots. Each terminal also has its own configurable key binding in AE2's controls category; neither key is assigned by default.
- Let the Vis Connection Card bind to a Vis Relay Interface output with Shift-right-click. Arcane terminals use local chunk VIS first, then draw the missing amount from the bound output. An unloaded, removed or inactive output cannot supply VIS, and checking the binding does not load its chunk.
- Add `terminals.allowAeForAuraVis`, disabled by default. Turning it on allows an arcane terminal without a Vis Connection Card to spend AE to draw environmental VIS. This still requires enough environmental VIS; electricity does not create it.
- Add localized essentia-name searches in essentia and arcane terminals. Unsupported languages fall back to English, which uses the Latin aspect names. Localized names no longer carry a Latin suffix in tooltips.
- Let Shift-clicking wands and crystals in the player inventory fill the arcane terminal's dedicated slots, including in the wireless terminal.
- Add both arcane terminals to the catalyst list beside JEI's arcane crafting recipes.
- Show available chunk VIS in both arcane terminal screens, using the workbench's readout style.
- Add shapeless recipes for all four ME Essentia Cells using an empty casing and the matching storage component.

### Fixes

- Refresh essentia immediately when an Essentia Access Card is inserted into or removed from an arcane terminal. Reopening the screen is no longer necessary, even when the stored amounts have not changed.
- Hide essentia from ordinary ME terminals and from arcane terminals without an Essentia Access Card by default. Essentia terminals continue to display it; this setting does not remove anything from storage.
- Preserve essentia and account for power correctly when a bus transfer is only partly accepted or fails. Pending transfers survive saves and dismantling, and export buses count their transfer operations correctly.
- Keep bus filters, resource-type choices and upgrade limits working with essentia. Removing the access card stops essentia access without affecting item or fluid handling.
- Fix breaking an Occult Monitor with a Thaumonomicon on it so the block can be removed and its contents are returned correctly.
- Stop golem backpack skin changes from consuming the material used to select the skin. The appearance now updates immediately.
- Replace iron ingots with thaumium ingots in the four ME Essentia Cell recipes.
- Fix research text formatting and rewrite the entries to read more naturally and describe the current mechanics, including the access card's behavior.
- Use “Terminal” for the top title of both arcane terminals, while retaining each terminal's full name above the crafting grid.
- Trim the Occult Monitor's Jade tooltip, moving detailed information behind Shift. Match the Alchemy Provider's energy bar to Jade's standard style, and color the Arcane Assembler's discount value and percent sign gold.
- Refresh the crafting preview when the Vis Connection Card, payment setting, local VIS or bound output's supply changes. Previewing or rolling back a craft refunds the relay's energy charge.

### Changes

- The Arcane Assembler draws environmental VIS from its own chunk rather than a 3×3 chunk area. Its VIS buffer is capped at 300, including when loading older saved values.
- Remove the Essentia Access Card slot from the ME Interface. Essentia import, export and external storage access now belong to the corresponding ME buses. Cards left in the previously added sixth Storage Bus slot are preserved and moved into a free native slot when one becomes available.
- Update the mod logo and wireless arcane crafting terminal texture.
- Change the Chinese Arcane Assembler labels from “灵气减免 / 灵气缓存” to “魔力减免 / 魔力缓存”.

### Other (internal refactors, build, documentation)

- Adapt the research system to Thaumaturge's current structure and generate its data through datagen.
- Update the pinned Thaumaturge revision to `80903dd9` and rebuild against it.
- Clean up the code structure and move Java comments and implementation notes into `doc`. These documents are excluded from both the release JAR and the sources JAR.
- Add regression checks for recipes, terminal visibility, wireless shortcuts, bus transfers, upgrade migration, golem backpack changes and VIS payment rollback.

---

# TECE 2.7.4.4

## 26.1.2 / NeoForge

本次更新包含尚未发布的 2.7.4.3 改动，以及之后为 2.7.4.4 完成的调整。

### 新增

- 增加服务端配置，可以调整终端访问规则、源质总线耗电、傀儡背包传输、奥术装配室速度与耗电、源质谐振仓燃料时长，以及概率之箱的运行参数。仓库中提供了配置示例和说明。
- ME 输入总线、输出总线和存储总线装入源质访问卡后可以处理源质。输入和输出总线使用六个升级槽，存储总线保留原生五槽。升级卡可以放入任意支持的槽位，卡片数量上限仍按 AE2 的规则执行。
- 无线源质终端和无线奥术合成终端支持放入 Curios 饰品栏。两种终端各有独立的打开按键，可以在 AE2 的按键分类中设置，默认不绑定。
- 魔力访问卡可以通过 Shift 右键绑定魔力中继接口的输出端。奥术终端先使用本区块 VIS，缺额再由绑定的输出端补足。输出端未加载、被拆除或离线时不能供给，检查绑定也不会加载其所在区块。
- 增加 `terminals.allowAeForAuraVis` 开关，默认关闭。开启后，未装魔力访问卡的奥术终端可以消耗 AE 取用环境 VIS，但仍需有足够的环境 VIS，不能用电力凭空生成魔力。
- 源质终端和奥术终端支持按当前语言搜索源质名称。尚未支持的语言回退到英文，英文使用要素的拉丁名称。其他语言的源质提示不再附带拉丁文后缀。
- 在玩家物品栏中 Shift 点击法杖或水晶，可以快捷放入奥术终端的对应槽位，无线终端同样支持。
- 在 JEI 奥术合成配方旁的可用设备列表中加入奥术合成终端和无线奥术合成终端。
- 两种奥术终端都显示当前区块可用的 VIS，提示样式与奥术工作台一致。
- 四种 ME 源质存储单元增加“空外壳＋对应存储元件”的无序合成配方。

### 修复

- 奥术终端插入或取出源质访问卡后立即刷新源质列表。即使网络库存没有变化，也不需要重新打开界面。
- 默认在普通 ME 终端，以及未装源质访问卡的奥术终端中隐藏源质。源质终端仍然显示源质，隐藏不会删除网络中的库存。
- 修复总线传输被部分接收或失败时的源质保留与耗电结算。未完成的传输可以随存档和拆卸保留，输出总线也会正确计算传输操作次数。
- 修复源质总线的过滤、资源类型选择和升级卡限制。取出访问卡后停止源质访问，物品和流体功能不受影响。
- 修复蕴魔观测器放有魔导手册时无法正常挖掉的问题，破坏后正确返还方块及其中的物品。
- 更换傀儡背包外观时不再消耗用于选择材质的材料，外观也会立即刷新。
- 将四种 ME 源质存储单元配方中的铁锭改为神秘锭。
- 修复研究文本的格式问题，重写部分条目，使措辞更自然，并与当前机制一致，包括源质访问卡的实际用途。
- 两种奥术终端顶部的标题统一为“终端”，合成区域上方仍保留各自的完整名称。
- 精简蕴魔观测器的 Jade 提示，详细信息改为按住 Shift 查看。炼金供应器的能量条改为 Jade 的标准样式，奥术装配室的减免数字和百分号统一显示为橙黄色。
- 魔力访问卡、支付开关、本区块 VIS 或绑定输出端的供给变化时刷新合成预览。预览和合成回滚会退还中继接口扣除的电量。

### 调整

- 奥术装配室改为只取用所在区块的环境 VIS，不再统计周围 3×3 区块。魔力缓存上限改为 300，读取旧存档时也遵守这一上限。
- 移除 ME 接口的源质访问卡槽。源质输入、输出和外部存储访问分别交给对应的 ME 总线。此前放在存储总线第六槽中的卡片会保留，原生槽位有空位时自动移入。
- 更新模组 Logo 和无线奥术合成终端材质。
- 将奥术装配室中文提示中的“灵气减免 / 灵气缓存”改为“魔力减免 / 魔力缓存”。

### 其他（内部重构、构建、文档）

- 研究系统适配 Thaumaturge 当前的结构，并通过 datagen 生成研究数据。
- 将 Thaumaturge 固定版本更新到 `80903dd9`，并据此重新构建。
- 整理代码结构，将 Java 注释及实现说明迁入 `doc`。发布 JAR 和源码 JAR 均不包含这些文档。
- 补充配方、终端显示、无线终端按键、总线传输、升级卡迁移、傀儡背包及魔力支付回滚的回归检查。
