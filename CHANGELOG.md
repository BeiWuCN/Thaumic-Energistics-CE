# Thaumic Energistics CE 2.7.6 / Minecraft 1.21.1 / NeoForge

## English

- Wireless Arcane Crafting Terminals and Wireless Essentia Terminals can join AE2WTLib Universal Terminals.
- Fixed a startup crash in the terminal scan tooltip integration with Thaumaturge 1.1.1. (#12)

- In the Essentia Terminal, left-click an essentia entry to fill a held container or take a filled phial with an empty cursor. Right-click to empty filled containers or store empty ones. The terminal shows a hint when an Essentia Access Card is required.
- Detect the clicked terminal slot from the pointer position to prevent accidental storage of filled phials.
- Recognize supported containers through Thaumaturge's essentia capability. Void jars, essentia crystals, and mana beans are excluded from container operations.
- Align the research titles for the ME Arcane Crafting Terminal and Thaumic Processor with their item names in English, Simplified Chinese, Traditional Chinese, and Russian.

- Skip zero-amount essentia entries when inspecting a pattern provider's return inventory.
- Keep scan results in an item tooltip beside the cursor with Thaumaturge 1.1.1.
- Match the AE2 Cell Workbench style for disabled partition slots.
- Remove extra spaces between Chinese text and Latin letters or digits in research entries.

## 中文

- 无线奥术合成终端和无线源质终端可加入 AE2WTLib 通用终端。
- 修复与 Thaumaturge 1.1.1 一起加载时的终端扫描提示崩溃。（#12）
- 源质终端左键装填容器，光标为空时可左键从源质条目取出装好的安瓿。右键倒空已装填的容器或存放空容器。需要源质访问卡时会显示提示。
- 按点击时的鼠标位置判断终端槽位，避免装有源质的安瓿被误当普通物品存入网络。
- 通过 Thaumaturge 的源质能力识别容器。虚空罐、要素水晶和魔力豆不参与容器操作。
- 统一 ME 奥术合成终端和神秘水晶处理器的研究标题与物品名称，覆盖英文、简中、繁中和俄文。
- 样板供应器读取回流库存时跳过零数量源质条目。
- 适配Thaumaturge 1.1.1的扫描提示，已发现的要素显示在鼠标旁的物品提示框内。
- 源质元件工作台的禁用分区槽沿用AE2元件工作台的样式。
- 去掉中文研究文案中汉字与英文字母或数字之间的多余空格。

# Thaumic Energistics CE 2.7.5.5 / Minecraft 1.21.1 / NeoForge

## English

- Pattern provider screens reuse an existing upgrade panel, preventing duplicate registration when Applied Flux is installed. (#8)
- Arcane Crafting Terminals match smaller shaped recipes and consume ingredients from the correct grid positions. They retain cost information when payment is unavailable, show why crafting was refused, and refresh the result when linked relay Vis changes.
- JEI highlights missing ingredients for arcane recipes. Required primal Vis Crystals in Thaumic Energistics CE recipes are listed from the top without gaps. The Arcane Crafting Terminal also accepts ordinary crafting recipes from JEI and crafts them without spending Vis.
- A phial containing a single aspect can mark an essentia cell partition slot without consuming the phial.
- Disabled partition slots no longer cover toolbar tooltips in the Essentia Cell Workbench.
- Research notes use aspects that reflect what each device does, with easier introductory puzzles and more involved notes for advanced storage and automation.
- Standard and MEGA essentia cells have distinct textures when installed in an ME Drive.
- The Arcane Crafting Terminal remembers whether each player prefers crystals or wand Vis for payment.
- The Essentia Cell Workbench uses AE2's workbench layout and can retain and copy a partition to the next cell. Essentia cells are configured in this dedicated workbench.
- The mod name and creative tab display without a colon. The Arcane Crafting Terminal, Essentia Terminal, Essentia Import and Export Buses, and Essentia Level Emitter use the ME prefix in their item names and screen titles.
- Essentia storage components accept any Vis Crystal aspect, and their workbench only offers to mark the essentia inside a phial.
- A Thaumometer held on the cursor scans individual stored items in ME terminals using Thaumaturge's inventory scanning rules. Discovered aspects appear inside the item tooltip.
- Two buttons in the Arcane Crafting Terminal return ingredients and crystals to the ME network or the player's inventory. Items that do not fit stay in their slots.
- Selected machine, wireless terminal, and crystal growth recipes use primal Vis Crystals that reflect their functions.
- JEI fills recipe crystal slots from ME storage or the player inventory. In wand-first mode it fills crystals that the payment preview still requires.
- An in-game guide covers getting started, essentia storage, terminals, arcane crafting, automation, and crystal growth in English, Simplified Chinese, Traditional Chinese, and Russian. It includes a worked example of ME-fed infusion and explains the completion signal.
- Shift-transfer in JEI fills the Arcane Crafting Terminal with multiple sets of ingredients and their required crystals when stock and slot capacity allow.
- JEI transfers crucible and fixed-output infusion recipes into processing patterns with the catalyst first, followed by any other items and the required essentia amounts.
- The Crystal Catalyzer and Overload Processing Factory support the Thaumic Crystal production chain. ExtendedAE and ExtendedAE Plus machines can cut Thaumic Circuit Boards and assemble Thaumic Processors.
- The Arcane Workbench reforms two Thaumic Crystal Dust into one Thaumic Crystal for 5 Vis.
- The Arcane Crafting Terminal displays the available Vis more prominently and shows a red warning when there is too little aura to craft. It also shows the required Vis and any discount, and each cost icon has a tooltip with its aspect and amount.

## 中文

- 样板供应器界面复用现有升级面板，修复安装 Applied Flux 时重复注册面板的问题。（#8）
- 奥术合成终端能匹配小于九宫格的有序配方，并从正确的槽位消耗材料。无法支付时仍显示配方开销，并提示合成失败的原因。已绑定中继器的 Vis 变化时，终端会刷新合成结果。
- JEI 会标出奥术配方缺少的材料。神秘能源 CE 配方所需的原始要素水晶碎片从顶部连续排列。普通工作台配方也可一键转入奥术合成终端，合成时无需消耗 Vis。
- 可用只含一种要素的安瓿标记源质存储元件的分区槽，标记时不会消耗安瓿。
- 源质元件工作台的禁用分区槽不会再遮挡工具栏提示。
- 研究笔记的要素与设备功能对应。入门研究的谜题较简单，高级存储与自动化研究的笔记更复杂。
- 标准源质存储元件与 MEGA 源质存储元件装入 ME 驱动器时使用不同的贴图。
- 奥术合成终端会记住每位玩家选择的是水晶优先还是法杖优先。
- 源质存储元件在专用的源质元件工作台配置。工作台采用 AE2 的布局，并可保留分区设置供下一个元件使用。
- 模组名称和创造模式物品栏标签去掉了冒号。奥术合成终端、源质终端、源质输入与输出总线以及源质发信器的名称和界面标题统一使用 ME 前缀。
- 源质存储组件的配方接受任意要素的魔力水晶碎片。在源质元件工作台用安瓿标记时，标记对象是瓶内的源质。
- 在 ME 终端用鼠标拿着魔导透镜时，可按 Thaumaturge 的物品栏扫描规则逐个扫描网络里的物品。已发现的要素显示在物品提示中。
- 奥术合成终端新增两个按钮，可将合成材料和水晶退回 ME 网络或玩家背包。放不下的物品留在原槽位。
- 部分机器、无线终端和水晶生长相关配方使用了与其功能对应的原始要素水晶碎片。
- JEI 可从 ME 网络或玩家背包补入配方所需的水晶碎片。法杖优先时，只补支付预览仍需要的水晶碎片。
- 游戏内指南提供简体中文、繁体中文、英文和俄文，涵盖入门、源质存储、终端、奥术合成、自动化和水晶生长，并演示如何用 ME 网络为注魔供料及接收完成信号。
- 库存和槽位容量允许时，按住 Shift 从 JEI 转移配方，会向奥术合成终端填入多份材料及所需的水晶碎片。
- JEI 可将坩埚配方与产物固定的注魔配方转为处理样板。输入依次为被转化的物品、其他材料，以及各类源质及其用量。
- 闪电科技的水晶催化器和过载处理工厂支持神秘水晶生产链。ExtendedAE 与 ExtendedAE Plus 的对应机器可切出神秘电路板并装配神秘处理器。
- 奥术工作台可消耗 5 Vis，将 2 份神秘水晶粉重新凝成 1 颗神秘水晶。
- 奥术合成终端放大了可用 Vis 读数。灵气不足以完成当前配方时，下方以红字显示「可用vis不足」。终端还会显示当前配方所需的 Vis 和减免比例，悬停费用图标可查看对应要素及消耗量。

# Thaumic Energistics CE 2.7.5.4-hotfix1 / Minecraft 1.21.1 / NeoForge

- AE2 Crystal Growth Accelerators recognize all four budding Thaumic Crystal blocks. (#7)

---

# Thaumic Energistics: CE 2.7.5.4 / MC 1.21.1 / NeoForge

## English

- Fixed an issue where dismantling a pattern provider discarded essentia waiting to return to the ME network. Buffered essentia now drops in phials, preserving the aspect and exact amount, including partial phials.
- Applied the fix to the provider integration paths for AE2, ExtendedAE, ExtendedAE Plus, AdvancedAE, and AE2 Lightning Tech.

Validation: Verified the drop and clear sequence with 12 essentia on a standard AE2 Pattern Provider. All 14 regression tests passed with optional integrations installed.

---

## 中文

- 修复拆卸样板供应器时丢失待回流源质的问题。缓冲中的源质会装进安瓿随供应器掉落，保留原有要素和数量，包括不足一整瓶的余量。
- 修复覆盖 AE2、ExtendedAE、ExtendedAE Plus、AdvancedAE 和 AE2 Lightning Tech 的样板供应器联动路径。

验证：在 AE2 原版供应器上验证了 12 点退料的掉落与清空流程。安装可选联动模组的环境通过 14 项回归测试。

# TECE 2.7.5.2 — Minecraft 1.21.1 / NeoForge

- Remove `ConfigCommentI18nMixin`; NeoForge loads `ModConfigSpec$ValueSpec` before mixin preparation on 1.21.1, so the mixin aborted startup with `MixinTargetAlreadyLoadedException`.
- Read localized config comments through `ValueSpec.getTranslationKey()` on the client tick instead, without mixing into the config class.
- Lower the required NeoForge version from 21.1.250 to 21.1.248.

---

# Thaumic Energistics: CE 2.7.5.1

Minecraft **1.21.1** / NeoForge **21.1.250** / Java **21**.
Requires AE2 **19.2.17** and the official Minecraft 1.21.1 Thaumaturge **1.1.0** release ([CurseForge file 9452543](https://www.curseforge.com/minecraft/mc-mods/thaumaturge/files/9452543)). Thaumaturge is not included in this release; older 1.0.3 builds are incompatible.

## English

### Added and changed
- Rename the research category to Applied Energistics (应用能源学) and use the AE wrench focus as its tab icon.
- Obtain the Thaumic Upgrade Smithing Template through archaeology at a 1.5% chance; duplicate it with one template, one thaumium ingot and seven Thaumic Crystal Blocks. Remove the initial crafting recipe.
- Convert any AE2 Inscriber press in a crucible with 8 Ordo, 8 Cognitio and 8 Praecantatio. Require four previous-tier components in symmetric corner positions for every 4k–256k storage-component upgrade.
- Add seven animated Thaumic Crystal tools with 960 durability and Vis repair exclusively on recharge pedestals. Upgrade the five ordinary Fluix tools using a Thaumic Upgrade Smithing Template and Thaumic Crystal Block. Preserve enchantments, custom names and damage; retain AE2 intrinsic tool enchantments. Craft the thaumic wrench and cutting knife with AE2 quartz-tool patterns, replacing Certus Quartz with Thaumic Crystal; place the research left of crystal cultivation.
- Add a dedicated Thaumic Circuit Press, its crafting/copying recipes, and use it instead of AE2's Calculation Press for thaumic circuit boards. Integrate the supplied assembler frame model and retain working RGB crafting lights.
- Show smithing and AE2 Inscriber inputs in the research book, with machine names and ingredient hover targets using the existing book artwork.
- Separate assembler plain-Vis and typed-primal payments. Prefer chunk Vis for ordinary costs and interfaces for supplied primals; fill missing types by splitting chunk Vis. Six primals, one of each, can pay one plain Vis. Combine sources without double-spending recipe elements.
- Show a single `Vis source: XXXX` line for a Vis Relay Interface mounted against the assembler, using its four-digit memory-card address and matching per-digit colors. Prefer attached interfaces when supplying Vis.
- Let AE2's ME Storage Monitor select essentia from jars, phials and labels, display live network stock, clear the selection with an empty hand, and lock/unlock it with a wrench. Add in-world essentia icon rendering and update the research instructions.
- Add Russian localization, using legacy Thaumic Energistics and current Thaumaturge terminology.
- Show the Knowledge Inscriber's recipe Vis requirement above the output and counted crystals below it, arranged from right to left.
- Add Thaumic Crystals, dust, circuit boards and processors, four budding-block qualities, four growth stages, infusion restoration, and 256k essentia storage and components.
- Reorganize the research tree and recipe progression. Circuit boards use a dedicated Inscriber press; revise shaped layouts and research gates.
- Add the infusion-crafted Fluix Brain as forbidden research after the Box of Probability. Normal-brain payout chances range from 45% to 67%; Fluix Brain chances range from 60% to 85%. Existing speed-card timing is preserved.
- Update machine, GUI, cell, component, core and card artwork. Rebuild the Box of Probability as a complete two-block-tall machine, with a separate upgraded brain model and knowledge-essentia culture liquid.
- Update to Thaumaturge 1.1.0 APIs and move Java packages to `com.beiwu.thaumicenergistics_ce`.

### Fixes
- Align all four crystal-growth inventory icons with AE2 by using flat generated item models instead of the crossed world model.
- Give the Box of Probability upper occupied block a geometry-free model with the machine particle texture, fixing missing-texture break particles. Rewrite the tools and circuit research as field notes; remove the addon-only Fluix wrench and knife.
- Give every registered TECE item a bounded, purpose-specific aspect composition so machine processing chains no longer leave items unscannable. Keep normal aspect-discovery restrictions.
- Limit tool repairs to recharge pedestals; disable passive, material, combination and Mending repairs while preserving naming and enchantments.
- Shorten the insufficient-Vis hint to current/required amounts. Retain the previous assembler crafting-light effect.
- Make local verification explicitly opt-in; keep verification subscribers out of ordinary dev/release runs and audit both mod and source JARs for test entry points and scripts.
- Remove the assembler's stalled-craft timeout bypass: a recipe without sufficient Vis/primals now keeps waiting rather than producing an unpaid result. Preserve staged chunk Vis and fractional relay amounts across saves.
- Move the custom Fluix Brain mesh outside Minecraft's vanilla model-discovery directory to avoid invalid-rotation model-loading errors.
- Split the assembler's 300 Vis buffer into six 50 Vis primal tanks; cap relay requests by each tank's remaining room and scale each bar to 50. Clamp old oversized buffers to these limits without converting overflow into other aspects.
- Offset essentia monitor icons from the screen surface using AE2's native icon depth to prevent z-fighting.
- Replace deprecated NeoForge screen-coordinate and JEI crafting-station registration calls.
- Align the Knowledge Inscriber's clear-grid hover and click area with its existing X artwork.
- Repair Knowledge Inscriber save/delete actions, recipe preview synchronization, slot feedback and recipe-display registration.
- Correct probability-box model UVs, inventory rendering, collision/interaction across both block spaces and status-screen artwork. Upgrading returns the ordinary zombie brain; removing the upgraded brain jar returns an empty jar.
- Prevent partial energy loss when a probability-box round cannot be funded; use simulated network extraction to check available power.
- Align crystal names with AE2: flawless, flawed, chipped and damaged. Rename their block/item registry IDs to `*_budding_thaumic_crystal`, with aliases for old saved IDs. Only flawless budding blocks resist growth decay; other tiers follow AE2's 1/12 decay chance and end as a Thaumic Crystal Block. Compress/decompress crystals at 4:1.
- Match all crystal-bud and cluster collision/selection shapes to AE2 in six directions, including waterlogged states.
- Restore the Arcane Assembly Chamber's own frame and correct its glass rendering; show internal light strips while crafting.
- Fix the assembler's six aspect bars: exact small values, decreasing/zero readings, per-aspect capacity scale and crafting-progress reset. Keep the artist's gradient textures and concise Aer/Aqua/Ignis/Ordo/Perditio/Terra tooltips.
- Correct machine active-state artwork, vibration-chamber side UVs and creative-cell animation metadata.

### Licensing and credits
- Textures created by **麦淇淋**: **CC BY-NC-SA 3.0**. See `LICENSE-ASSETS.md` for scope and third-party attribution.
- This version is authored by **BeiWuCN**. Code is licensed under **MIT**; historical copyright notices for original Thaumic Energistics code are retained. Textures keep their separate CC licenses.
- Fluix brain anatomy references **Pixel Brain by sparkskye**, **CC BY 4.0**; geometry redrawn and recolored with added neural implants.

## 中文

### 新增与调整
- 研究分类更名为“应用能源学”（Applied Energistics），分类图标改为 AE 扳手核心。
- 神秘锻造模板改为考古刷取，概率 1.5%；使用一张模板、一个神秘锭和七个神秘水晶块复制出两张，移除首次制作配方。
- 神秘电路压印模板改为任意 AE2 压印模板加 Ordo、Cognitio、Praecantatio 各 8 点的坩埚配方；4k～256k 组件均需四个前级组件，对称放置于四角。
- 新增七件动画材质神秘水晶工具：耐久 960，仅在充能基座以 Vis 修复；使用神秘锻造模板、对应福鲁伊克斯工具与神秘水晶块升级，保留附魔、名称及损耗，保留 AE2 固有附魔。神秘水晶扳手和切割刀沿用 AE2 石英工具布局，以神秘水晶替代赛特斯石英；工具研究置于水晶培育左侧。
- 新增神秘电路压印模板及制作、复制配方，电路板改用专用模板；接入提供的装配室框架模型并保留合成 RGB 灯带。
- 研究书补全锻造台和 AE2 压印器的输入材料展示、机器名称与材料悬停，沿用原有书页美术。
- 装配室分开结算普通 Vis 与指定元素：普通费用优先区块 Vis，元素优先接口供给，缺少的类型由区块 Vis 拆分补足；六种元素各 1 点可合成 1 Vis。支持混合供给，并避免元素被重复计入两种费用。
- 装配室表面贴有魔力中继接口时，增加一行 `Vis源：XXXX`，地址及每位颜色与内存卡一致，并优先使用贴面的接口供给 Vis。
- 直接使用 AE2 的 ME 存储监控器显示源质存量：罐子、安瓿和标签选择要素，空手清除，扳手锁定或解锁；补全源质图标的世界渲染并更新研究说明。
- 新增俄语翻译，参照旧版神秘能源及当前 Thaumaturge 的俄语术语。
- 知识记录仪在产物上方显示配方所需 Vis，下方从右往左显示水晶与数量。
- 新增神秘水晶、粉、电路板、处理器，四品质母岩、四阶段晶芽、注魔修复与 256k 源质存储组件和元件。
- 重排研究树和配方进度，电路板使用专用压印模板，调整有序合成布局及研究门槛。
- 概率之箱后新增福鲁伊克斯之脑禁忌研究，通过注魔制作。普通脑成功率为 45%～67%，升级后为 60%～85%，保留加速卡原有耗时规则。
- 更新机器、界面、元件、组件、核心和访问卡美术。概率之箱改为完整的两格高机器，增加升级脑模型和知识源质培养液，英文名改为 Box of Probability。
- 适配 Thaumaturge 1.1.0，Java 包名迁移至 `com.beiwu.thaumicenergistics_ce`。

### 修复
- 四个水晶生长阶段的物品栏图标改用与 AE2 一致的平面物品模型，修正交叉方块模型造成的大小与位置偏差。
- 概率之箱上半格使用无几何体、带正确粒子材质的模型，修复黑紫破坏粒子。工具与水晶电路研究改写为实验记录，移除本模组新增的福鲁伊克斯扳手和切割刀。
- 为所有注册物品补充有用途区分、数值受控的要素组成，修复加工链造成的不可扫描问题，保留正常要素发现限制。
- 工具仅可通过充能基座恢复耐久，关闭手持、材料、合并与经验修补，保留改名及附魔继承。
- 本地验证改为显式启用，普通开发运行及发布环境不加载测试订阅器；检查主 JAR 和源码 JAR，禁止混入测试入口及验证脚本。
- 移除装配室等待超时后绕过魔力检查的逻辑，Vis 或元素不足时继续等待；暂存区块 Vis 与中继小数余量均随存档保存。
- 福鲁伊克斯脑自定义模型移出原版模型扫描目录，避免旋转数组被原版解析器误读而报错。
- 奥术装配室总缓存上限为 300 Vis，六种元素各占 50 Vis，中继按各元素剩余空间补充，每根条以 50 为满格；旧存档超额数据按此限制收敛，不将溢出转换成其他元素。
- 源质监控图标使用与 AE2 原生图标一致的深度偏移，避免与屏幕面重叠闪烁。
- 替换已弃用的 NeoForge 界面坐标及 JEI 工作站注册接口，清理对应编译警告。
- 对齐知识记录仪清空按钮的高亮、点击区域和原有 X 图案。
- 修复知识记录仪保存、删除、预览栏同步、槽位反馈及查看配方时的网络显示类型注册问题。
- 修复概率之箱模型 UV、物品栏显示、上下两格碰撞与交互和状态屏幕。升级返还普通僵尸之脑；取下升级后的缸中之脑得到空罐。
- 修复概率之箱电量不足时可能丢失部分能量的问题，启动前通过模拟提取确认可用电量。
- 母岩名称统一为无瑕、有瑕、开裂、损坏，注册 ID 改为 `*_budding_thaumic_crystal`，添加旧存档 ID 兼容映射。无瑕母岩不退化，其余按 AE2 的十二分之一概率退化，最终成为神秘水晶块；压缩与拆解改为四合一和一拆四。
- 四阶段晶芽、晶簇的六方向碰撞箱与选取框对齐 AE2，并检查含水状态。
- 恢复奥术装配室自己的框架，修正玻璃并增加合成时的内部灯带。
- 修复六元素条的微量数值、消耗后下降、清零、独立容量刻度和合成进度复位，保留画师渐变贴图及简洁的拉丁元素名提示。
- 修复机器工作状态美术、源质谐振仓侧面 UV 和创造元件动画元数据。

### 协议与署名
- **麦淇淋**创作的材质采用 **CC BY-NC-SA 3.0**，范围和第三方署名见 `LICENSE-ASSETS.md`。
- 本版本由 **BeiWuCN** 主导开发，代码采用 **MIT**；保留旧版 Thaumic Energistics 的历史版权声明。材质保留独立的 CC 授权。
- 福鲁伊克斯脑部造型参考 **sparkskye** 的 **Pixel Brain**（**CC BY 4.0**），重新建模、配色并增加植入结构。

## Validation / 验证

Java 21 build passed for Minecraft 1.21.1. All 14 dedicated-server regression checks passed both with and without optional compatibility mods. A client entered a world with MEGA Cells and JEI enabled; all 76 registered item models loaded.

### 1.21.1 additional integration / 1.21.1 补充联动

- Optional MEGA Cells essentia storage: 1M, 4M, 16M, 64M and 256M cells/components, their supplied textures and research progression. Items, recipes and research exist only when MEGA Cells is installed.
- Advanced cell housing uses void metal and warded glass; normal low-tier housing recipes are unchanged.
- 新增 Mega Cells 可选联动：1M、4M、16M、64M、256M 源质元件、组件、对应材质与研究。仅安装 Mega Cells 时出现。
- 高级外壳改用虚空锭和守护者玻璃，低阶普通外壳配方保持不变。

- Place the five MEGA Cells research entries above the base storage chain, progressing from right to left; shorten their text into field notes.
- 五档高容量研究移至基础存储链上方，从右向左推进；研究文本精简为实验笔记。

---

# TECE 2.7.4.6 — Minecraft 1.21.1 / NeoForge

- Port the 2.7.4.6 feature set to Thaumaturge 1.0.2 on Minecraft 1.21.1.
- Add native essentia import/export/storage buses and provider integration for ExtendedAE, ExtendedAE Plus, AdvancedAE and Lightning Tech.
- Give wireless arcane terminals independent inventories, offline local access, optional quantum connectivity and Curios shortcuts.
- Use environmental VIS directly and bound relay cards for wand charging; correct 1.0.2 aura payment units.
- Add 16 server settings; fix terminal access permissions, upgrade slot visibility and overlapping titles.
- Update backpack models, skins, transfer indicators, research text, recipes, Jade, JEI and decorative resources.
- Set the creative tab icon to the AE Wrench Focus.

Validation: build, 10,000-cycle VIS conservation regression, and game tests with and without optional compatibility mods.

---

# TECE 2.7.4.2-hotfix1

## Fixes

- Fix the crash on 26.1.2, where the mod failed to load with a `NoClassDefFoundError` because the GUI self-test still referenced the resonance chamber screen's old class name after the screen was renamed. Both references point at the new name now
- Add a Traditional Chinese translation for players in Taiwan

---

# TECE 2.7.4.2

## Fixes

- Keep the essentia an interface is broken with: it stays with the dropped item, where before breaking the interface destroyed whatever it held
- Unlink the ME network when the terminal's binding is cleared, where before an unbound terminal stayed on the network
- Keep the wireless arcane crafting terminal on the single network it is bound to, where before it joined two at once
- Hand the interface's essentia rows back to AE2 to stock: this line's own stocking logic duplicated AE2's inventory management
- Cut the essentia vibration chamber's output to a tenth: 200 -> 20 AE/t, which puts potentia's peak at 32, ignis at 20 and every other aspect at 10, and takes one piece of essentia from 40k-160k AE down to 4k-16k AE
- Read the chamber's output limit from the constant instead of hardcoding 2000 AE/t in the GUI text: the screen and Jade show 32 now
- Drop the unsupported 350% claim from the chamber's research page rather than promise a figure

## Removals

- Drop the pattern provider's upgrade slot along with the two accessors and two mixins that came with it. AE2's pattern provider has no upgrade inventory, so the card 2.7.3.66 made room for had nowhere to live

## Other (internal refactors, build, documentation)

- Describe what the access card actually does at the interface - it outputs aspects from the network to the outside - and say it also fits a wireless arcane crafting terminal
- Name the wireless arcane crafting terminal in full in its text
- Point the README and README.en.md icon at the new branch name, `1.21.1`

---

# TECE 2.7.3.66

## Additions

- Crucible recipes can be written into a processing pattern straight from JEI in the pattern encoding terminal: the catalyst takes the first input slot, each of the recipe's aspects takes one essentia slot, and the result is the single output. The aspects go in on purpose - a network that is not told which aspects the crucible wants has nothing to stock
- The pattern provider gained a slot for the essentia access card. AE2 gives the ME Interface an upgrade inventory and left the pattern provider with none, so neither the block nor the cable part had anywhere to put the card
- A second decorative doll: the BeiWu_CN figure
- The research tab icon is now the knowledge core's crystal, one frame doubled to 32x32 rather than four animation frames drawn on top of each other

## Fixes

- Breaking a machine with a pickaxe dropped nothing. Eleven of the thirteen blocks require the correct tool, and `Tool.isCorrectForDrops` reads `minecraft:mineable/pickaxe`, which this line never shipped, so no tool counted as correct and every loot table was dead weight. The machines drop themselves now
- The vibration chamber screen is drawn from its texture now: the panel, the slot wells and all three gauges (flames for burn, a purple bar for energy, a pale bar for essentia). The three readout lines are gone - the art leaves 61 pixels on the left and 68 on the right while the longest line needs about 100 - so the numbers live in the gauge tooltips
- The arcane terminal's top line showed the terminal's own name while the label above the crafting grid said the same thing on both the wired and the wireless terminal, which made the two indistinguishable. The top line reads "Terminal" now and the grid label carries the terminal's own name
- The first version of the tab icon pointed at the whole 16x64 knowledge core sheet, so its four animation frames stacked; that path has no frame handling, so the icon takes the first frame only

## Other (internal refactors, build, documentation)

- The crucible feature is three new classes: reading the recipe names no JEI type (the dedicated server runs that path too), the recipe type is borrowed from Thaumaturge's crucible category the way the workbench's is (a mirror would open a second page that does not match the one the player sees), and the transfer is handed to AE2's own `EncodingHelper`
- Two startup crashes inside the pattern provider's upgrade slot: `@Invoker` only looks in the mixin's own target class, so putting `setupUpgrades` on `PatternProviderMenu` throws `InvalidAccessorException` and the accessor has to sit on `AEBaseMenu`, which declares it; and a plain class cannot live in a package the mixin config declares, or it fails with `IllegalClassLoadError`, so the mixed-in interface moved to `integration.ae2`
- The pattern provider's injection descriptor checker covers 12 descriptors and grew two rules (every accessor must be declared in the same target class; no referencable plain class may be left inside the mixin package). `runData` now loads the whole mod's mixins as a startup self-check - it would have caught both crashes before anyone launched a client

---

# TECE 2.7.3.65

## Additions

- Machine slot highlights now use AE2's style: a cyan outline over a translucent fill instead of the vanilla white block
- Arcane crafting terminals spend the six crystal slots before the wand, where they used to prefer the wand
- The arcane terminal's cost strip draws the crystals a craft will use, next to the aspect icons
- The crystal slots explain which crystal they take only while shift is held, instead of covering the neighbouring slots
- The arcane assembler's four gear slots show their empty icons
- Twelve machines gained loot tables: a pickaxe returns the machine itself, where it used to drop nothing

## Fixes

- Collapsing a golem with an empty hand now hands its backpack, wireless link and skin back first; they used to disappear with it
- Clicking a placeholder slot on a terminal that is not bound now refuses the item and says so, instead of silently swallowing it
- Two gaps in the essentia gestures: shift left-click took the whole row and was blocked, and shift scrolling was not blocked at all
- The wand slot did not check that what went into it was a wand
- The gacha payout line named Thaumaturge's knowledge types (observation and theory), which does not say what was actually granted, so it now says primal and compound
- The unbound terminal warning had no text, so players saw the key itself

## Changes

- Followed the new head of Thaumaturge's 1.21.1 branch: the wand focus moved onto upstream's new spell API, essentia containers are read through an item capability view, and several parts renamed their members
- Rewrote the arcane crafting terminal's and the essentia access card's second research pages
- The dependency floor on Thaumaturge is now 1.0.0. Older builds have none of these class names and would die at class load rather than being refused at startup

## Other (internal refactors, build, documentation)

- Re-derived the vendored Thaumaturge patch onto the new upstream. The build moving off the server thread, encoding the index once, and the client skipping an unchanged index are kept; the allocation-free fingerprint is dropped, because upstream rewrote the fingerprint as a sectioned comparison and touching it risks a cache that does not invalidate, which trades a stale index for a little speed
- The focus is a datapack-driven spell behaviour now, so the spell part and the focus data map come with it; without them the focus does nothing
- The focus no longer charges vis itself. The wand path charges once by spell complexity, so charging again would take it twice
- JEI is pinned back to the 19.36 that Thaumaturge compiles against. JEI later added a parameter to `ListElementInfo.createFromElement` and Thaumaturge's search index still calls the older three-argument form, so a newer JEI throws `NoSuchMethodError` the moment JEI's search box is opened

---

# TECE 2.7.3.64

## Other (internal refactors, build, documentation)

- Caught up with the Thaumaturge version

---

# TECE 2.7.3.63

## Other (internal refactors, build, documentation)

- Brought the comments in line with how a person would write them

---

# TECE 2.x Release Notes (2.7.3.62)

## Other (internal refactors, build, documentation)

- Reworked the API isolation layer so every aspect key goes through the TcAspects facade in compat/thaumaturge. Five files outside the layer had been naming the upstream TCAspects directly, across 19 references
- Split the gacha box's block entity, moving the AE buffer and its grid charging into GachaPower and the bound player and the brain rules into GachaOwner; the block entity drops from 435 lines to 342
- The README icon now comes from logo.png at the repository root

---

# TECE 2.x Release Notes (2.7.3.61)

## Fixes

- Reworked the code structure for a more modern environment
- Fixed the essentia cell workbench having no function at all
- Fixed quick operations not working in the essentia cell workbench
- Fixed a crash when shift-clicking in the essentia cell workbench
- Fixed the alchemy provider and the wireless alchemy receiver not working
- Fixed the six primal vis progress bars inside the Arcane Assembler not drawing correctly
- Fixed the distillation encoder being able to duplicate items
- Fixed the distillation encoder being able to swallow a blank pattern
- Fixed the knowledge inscriber showing surplus slots
- Fixed the registered ids of the alchemy provider and the wireless alchemy receiver
- Fixed the registered id of the Occult Monitor
- Fixed the wireless alchemy receiver working without consuming energy
- Fixed the wand focus AE Wrench being unable to rotate AE block parts
- Fixed a wrong essentia cell recipe
- Fixed the arcane crafting terminal sometimes failing to craft
- Fixed the large number of errors in this mod's research pages
- Fixed the wireless alchemy receiver's unbound hint reciting a whole binding procedure; it now just says "not bound yet"
- Fixed the alchemy provider and the wireless alchemy receiver being unable to serve "pull-type" machines that only implement IEssentiaTransport (the infusion machine, for instance): whatever is asked for is now drawn from the network and delivered, where previously standing next to such a machine was refused silently
- Fixed the alchemy provider's Jade information stating the same thing three times; it now shows only the grid state and the bound receivers
- Fixed the wireless receiver's bound coordinates being drawn in dark grey and smearing into Jade's background; they are white now
- Fixed sneak interactions intercepting other parts: they now only apply to cables that actually carry an arcane crafting terminal, and sneak-left-click clears the pairing
- Fixed the distillation encoder's essentia source slot losing the name JEI writes into it, and fixed breaking the block taking the slot's contents with it (only the pattern drops back)
- Fixed the knowledge inscriber's loose slot filtering: the core slot accepts knowledge cores only, and the twenty-one mirror-only slots beside it accept no input at all
- Fixed the wireless arcane crafting terminal not being registered in AE2's GridLinkables, which made the terminal binding slot reject the item outright
- Fixed line-leading style being lost on wrapped research pages
- Fixed the wired and wireless arcane crafting terminals sharing one JEI transfer registration, which lost the transfer button on the placed terminal
- Fixed JEI transfer treating a tag's first member as the template and reporting a shortage even when the network had stock; it now takes the variant with the largest amount
- Fixed the arcane crafting terminal counting the network as one more ingredient and still reporting INGREDIENTS_CHANGED when the grid already held the material
- Fixed both arcane crafting terminals showing the item name twice in the title; the title now comes from AE2's terminal style
- Fixed the placement of the arcane crafting terminal's vis cost row, now laid out by the terminal style document so it tracks the artwork as the window is resized
- Fixed the vis cost row drawing over the inventory and overflowing the window's right edge; it is now drawn inside the wooden bar below the crafting output, right-aligned with the chip
- Fixed essentia gestures swallowing clicks: only a click that actually fires a gesture is consumed, the rest goes back to AE2, and jars and ampoules enter the network normally again
- Fixed the ME interface's two rows not being cleared after the essentia access card was pulled, so the row's contents were still handed out
- Fixed AE2 still moving essentia backwards into the interface's storage row while the essentia access card was fitted
- Fixed essentia drawn by the access card being written into the interface's storage row, inflating the network total while the source container never drained
- Fixed the essentia access card treating the storage row as a filter, which made the terminal pay out essentia out of thin air
- Fixed the flux transfer interface querying its destination twice per cycle while paired; now once
- Fixed the development-environment run: the main source set no longer declares the self-test directory again, so the same sources are not compiled twice and service files are not copied twice, which used to error out before server start
- Fixed the Occult Monitor falsely reporting "no infusion altar detected" while its own node was offline, and the missing-thaumonomicon hint is no longer masked by the network being offline; the panel's log label misalignment by two spaces is fixed too
- Fixed the essentia resonance chamber declaring itself full too early (the old logic locked itself out by reading the state it was already in); it now judges by remaining space and half-tick usage, and the load path behaves the same way
- Fixed four "韵魔观测器" typos in the Chinese language file (block, Jade config, research name, Jade title)
- Fixed the essentia cell workbench's cell slot swallowing a whole stack of cells and its upgrade slot swallowing a whole stack of cards
- Fixed an out-of-bounds error when shift-clicking a cell or upgrade card in the player's inventory (the movable range was nine slots short, excluding the hotbar)
- Fixed shift-clicking a terminal handling only the one stack under the cursor; it now fills a whole held stack of containers
- Fixed a NoClassDefFoundError on dedicated server startup caused by payload registration referencing a client screen class
- Added the missing Chinese and English names for the ME Essentia Storage Component (the language keys were absent)

## Additions

- The Occult Monitor now emits a redstone signal of strength 15 once when an infusion craft finishes
- Added the wireless arcane crafting terminal
- Added the vis link card
- Added the essentia access card
- Added the gacha box and the gacha box aggregator: brain slot binding a player, the cognitio essentia port, speed cards, research points settled through Thaumaturge's knowledge pool, Jade lines, infusion recipes and research entries
- Reworked the essentia storage bus into the flux transfer interface, working on grid ticks with a 20-tick cycle, its extraction and release ends decided by AE2 memory card pairing
- The flux transfer interface's extraction end draws flux from its chunk at 100 AE plus 1 auram and 1 ordo per point, buffering up to 256 points; its release end vents 4 flux per cycle into the 3×3×3 in front of it
- Added a Jade status line for the flux transfer interface: the server writes the waiting reason, the client draws the refusal in red or the working line
- Added two probabilistic behaviours: 10% of the flux drawn condenses into corrupted essentia entering the network (needs a drive and accepting storage), and about 1.5% of it vents the whole batch into the controller's chunk
- Added the flux transfer interface's research page (reusing Thaumaturge's own thaumaturge:flux) and its research entries
- Added the vis link card, fitted into the wireless arcane crafting terminal as an AE2 upgrade card; the vis it needs comes from the chunk the player stands in, and the network is no longer charged
- The placed arcane crafting terminal part supports the vis link card too, with one more upgrade slot
- The wireless arcane crafting terminal supports the essentia access card, with jar and ampoule gestures matching the essentia terminal
- Essentia access card: once fitted to an ME interface (one per block and per cable part), the aspects marked in the configuration rows are drawn from adjacent containers into the network
- The essentia cell workbench joined AE2's upgrade slot system: upgrade cards go on the cell itself (three slots) and the panel refreshes with the cell's contents

## Changes

- The Occult Monitor reads the thaumonomicon in its slot again, and its recipe now consumes a thaumometer
- Rewrote the flux transfer interface's seven research pages in plain declarative prose and gave the page +2 warp
- The essentia storage bus was reworked into the flux transfer interface shell: menu, screen, JEI drag-and-drop, configuration payload, transport view, capability registration and ME storage all removed, now extending P2PTunnelPart for memory card pairing
- The flux transfer interface's recipe moved from the arcane workbench to infusion (ae2:interface catalyst plus vis relay, 2 salt, ordo and vitium crystals, annihilation core and two pistons, 88 vis, instability 7)
- Research tree reshuffled: the vis relay interface moved above the wireless essentia terminal, the flux transfer interface above that, and the basic energetics icon was given up to the ME Essentia Component Casing
- Unified naming on "vis": vis relay interface, vis link card, and three hand-written research texts
- Both upgrade cards (essentia access card, vis link card) became infusion recipes, replacing their workbench recipes, with three research entries filled in
- Research gates became aspect costs: 24 entries gained note_aspects (3–10 points), observation rows billed per entry while theory rows keep the 1–2 rows of the 1.7.10 tree
- The AE Wrench focus now goes through AE2's own part operations
- The Occult Monitor's idle draw is now 64 AE/t (it was 256 inherited from upstream, briefly 32)
- Renamed infusion_monitor to occult_monitor throughout (block, package, classes, registered ids, resources, recipes, research), with the Chinese name 蕴魔观测器
- The alchemy provider and the wireless alchemy receiver now push essentia from the network to "pull-type" machines
- The alchemy provider's Jade display became a text overlay bar chart (previously Jade's arrow-style progress bar), with values shown directly on the bar
- The wireless receiver's second Jade line now shows the coordinates of the bound provider (previously a 10 AE/unit price note, dropped along with the unified charge)
- Wireless essentia transfer is now billed in both directions: it used to charge 10 AE per unit only when drawing from the network, while feeding the provider back was free; the charge is now uniform and settled before the essentia moves, and drawing pays per unit and stops when it cannot pay
- The author line was updated to BeiWuCN, Nividica, Alkusure86, Community
- The Occult Monitor's bubble render distance is capped at two chunks, and infusion bubbles are drawn only within eight blocks
- Moved the machine subsystems (essentia resonance chamber, Occult Monitor, knowledge inscriber, essentia provider) together with their hosts and in-package collaborators into sub-packages, turning blockentity from one long run of flat classes into a per-machine directory tree
- Renamed the puppet's second model after the block it belongs to
- Removed a large amount of hand-written item and block descriptions and usage hints, keeping only state and live data (essentia cell capacity, knowledge core contents, golem backpack cost, terminal bound coordinates, wand focus name and text)
- The essentia cell workbench screen switched to AE2's upgrade screen style, with two toolbar buttons ("partition by contents" and "clear partitions"); the partition cells draw greyed out and unusable while no cell is fitted
- Upgrade card tooltips are now generated by AE2's upgrade card registry, keeping the Arcane Assembler and the essentia cell workbench in step
- The Arcane Assembler, Occult Monitor and essentia resonance chamber now take their panel readings synchronised from the server

## Removals

- Removed the essentia import and export buses (parts, items, menus, screens, models, textures, two recipes and two research rewards), and pointed the two research entries at the essentia storage bus instead
- Deleted 16 textures and their animation metadata that no model or screen referenced but that rode along in the jar
- Deleted the logo image that nothing referenced
- Removed the in-repository self-test: the selftest source set, SelfTestHook/SelfTestProvider and every THAUMICENERGISTICS_*_SELFTEST switch, with testing moved out of the repository

## Other (internal refactors, build, documentation)

- Each machine's slots are registered as item capabilities so pipes can only withdraw what breaking the block would drop; the three essentia buses gained an IEssentiaTransport capability, and provider links, buffers and the resonance chamber tank moved to BlockCapabilityCache
- Deleted four screen drafts nothing ever loaded (the bus screens actually load AE2's own styles)
- Updated the mod logo
- Bumped the version number
- Introduced the compat/thaumaturge isolation layer: infusion altars, wands and the aspect index and other upstream APIs were funnelled into a facade, the Arcane Assembler's giant class was split up, and dead code removed
- Source tree reorganisation: self-test moved into selftest, Arcane Assembler pieces into blockentity.assembler, client screens and buttons into client.gui, the client halves of Jade and JEI out of the common tree, registration code and payloads into the network package, and the empty api and research packages deleted
- Menu payloads now all go through MenuNetwork, menus no longer name payloads directly, and GhostGridSlot takes a writer
- The Arcane Assembler's craft lifecycle, vis search, tooltip text and upgrade accounting were split into their own classes
- The slots, grids and buffer logic of the Occult Monitor, knowledge inscriber, essentia cell workbench, provider and resonance chamber were split out, leaving the block entities responsible only for ticking and their outward interfaces
- Comment and javadoc tidying: long comments rewritten as prose with the rules recorded in docs/CONVENTIONS.md, over-long comment lines and over-tall javadoc brought down until the project's audit counts read zero, and member javadoc cut back to the parts carrying a number, a break, or a library quirk
- Dependencies moved to maven.modrinth coordinates, Thaumaturge switched to being built from source by a fetch script (which runs data generation and checks the aspect json count), and a GitHub Actions workflow added with every action bumped to its current major version
- CI: Thaumaturge's checkout directory and build output moved out of the Actions cache, and the upstream licence (MIT) and README notes corrected
- The vis aspect index's hashing, loading, building and writing moved off the server thread, with the client skipping the rebuild when the index is unchanged (shipped as a patch under tools/patches)
- Specified UTF-8 encoding for Java compilation and Javadoc
- Self-test now waits for Thaumaturge's vis index to be ready before reading it, and prefers requesting registry access from the server
- Trimmed the comments in build.gradle, gradle.properties, settings.gradle and the related classes
- The issue tracker URL now points at this repository
- Working notes (the comment convention checklist and the arcane crafting terminal design notes) are untracked and ignored

---

# TECE 2.7.4.2-hotfix1

## 修复

- 修好 26.1.2 环境下启动即崩的那次事故：给谐振仓界面改过类名之后，自测里的引用没跟着改，模组一加载就抛 `NoClassDefFoundError`。两处引用都改到了新类名
- 补上繁体中文（台湾用语）的翻译

---

# TECE 2.7.4.2

## 修复

- 把拆掉 ME 接口时被一起清掉的源质留给掉落物，此前接口一破，里面存着的源质就没了
- 清掉无线奥术合成终端的绑定时一并断开 ME 链接，此前终端解除绑定后仍挂在网络上
- 让无线奥术合成终端只认它绑定的那一个网络，此前它会同时接上两个
- 把接口的源质行交回给 AE2 自己补货，这一线的补货逻辑与 AE2 的库存管理是重复的
- 把振动室的基础输出降到十分之一：200 → 20 AE/t，potentia 峰值 320 → 32、ignis 20、其余要素 10，一份源质从 4 万～16 万 AE 降到 4 千～1.6 万 AE
- 让振动室的输出上限跟着常量走，不再把 2000 AE/t 写死在界面文案里：界面与 Jade 现在显示 32
- 删掉研究页里那句没有依据的「约 350%」，不再承诺具体倍数

## 移除

- 删掉样板供应器的升级槽，以及随行的两个访问器和两个 mixin。AE2 的样板供应器没有升级 inventory，2.7.3.66 给它加的那个槽插进去的访问卡没有归宿

## 其他（内部重构、构建、文档）

- 把访问卡的研究文本改成描述它在 ME 接口上的实际行为（从网络向外输出源质），并写明它也能装进无线奥术合成终端
- 在文案里把无线奥术合成终端写全名
- 把 README 与 README.en.md 里的图标地址从旧分支名改到 `1.21.1`

---

# TECE 2.7.3.66

## 新增

- 坩埚配方可以在样板编码终端里按 JEI 转移直接写成处理样板：催化剂物品占第一个输入格，配方的每个要素各占一格源质，产物是输出。要素写进样板是刻意的 —— 不告诉网络坩埚要哪几种要素，它就没东西可备
- 样板供应器多了一个升级槽，装得下源质访问卡。AE2 只给 ME 接口配了升级 inventory，方块与线缆部件两种形态的供应器此前都没地方插卡
- 第二个装饰娃娃：BeiWu_CN 头像
- 研究页签的图标改为知识核心的晶体，取单帧放大到 32×32，不再是叠在一起的四帧动画

## 修复

- 用镐子挖机器此前什么都不掉：十三个方块里有十一个要求正确工具，而 `Tool.isCorrectForDrops` 依据的 `minecraft:mineable/pickaxe` 标签这一线从来没提供，于是任何工具都判为不正确，已经写好的战利品表形同虚设。现在挖掉会掉回机器本体
- 振动室界面改为按贴图绘制：面板、槽位凹槽与三条仪表（燃烧火焰、能量紫条、源质浅条）都进贴图。三条读数行删掉 —— 贴图左侧只剩 61 像素、右侧 68，最长一行要约 100，数字改在仪表 tooltip 里给
- 奥术合成终端顶行显示的原本是终端自己的名字，而网格上方的标签有线与无线都写着同一句，两个终端分不出来。顶行现在读「终端」，网格标签写各自的形态名
- 页签图标的第一版直接指向知识核心的 16×64 动画表，画出来是四帧叠在一起；该路径没有帧处理，改为只取第一帧放大到 32×32

## 其他（内部重构、构建、文档）

- 坩埚那条走三个新类：配方读取不点任何 JEI 类型（专用服务端也要跑这条路），配方类型借用 Thaumaturge 的坩埚分类、与奥术工作台同一套借法（镜像会另开一页，和玩家实际看到的那一页对不上），搬运交给 AE2 自己的 `EncodingHelper`
- 样板供应器升级槽的两处启动崩溃：`@Invoker` 只在本 mixin 的目标类里找成员，把 `setupUpgrades` 挂到 `PatternProviderMenu` 上会 `InvalidAccessorException`，访问器必须落在声明它的 `AEBaseMenu`；另外 mixin 配置声明的包里不能放可以被外部引用的普通类，否则 `IllegalClassLoadError`，混合进去的接口已移到 `integration.ae2`
- 样板供应器的注入描述符检查器扩到 12 条并加了两条规则（访问器必须声明在同一目标类、mixin 包内不得留可被引用的普通类）；`runData` 现在作为启动自检跑一遍完整的 mixin 加载，上面两次崩溃它都能提前抓到

---

# TECE 2.7.3.65

## 新增

- 机器槽位的悬停框改成 AE2 风格：青色描边加半透明填充，不再用原版的白色方块
- 奥术合成终端改为先扣六槽里的魔力水晶、不够才掏法杖，此前是法杖优先
- 奥术合成终端的费用条带会画出这次要动用的水晶，颗数与要素并排显示
- 水晶槽改为按住 Shift 才说明这一格收哪种水晶，提示不再常驻挡住旁边的格子
- 奥术装配室的四个装备槽补上空图标
- 十二台机器补上战利品表：用镐子直接挖会掉回机器本体，此前挖掉什么都不剩

## 修复

- 收起傀儡（空手潜行右键）时先把背包、无线链接与贴皮摘下来还给玩家，此前会被一起吞掉
- 终端未绑定时点替身槽改为明确拒收并提示，此前会静默收下，等于把物品吃掉
- 源质手势的两处遗漏：shift 左键取整排被拦下、shift 滚轮没有挡
- 法杖槽此前不检查放进去的是不是法杖
- 概率之箱的给奖提示原本写的是 Thaumaturge 的知识类型名（观测／理论），看不出实际给了什么，改为「原始／复合」
- 终端未绑定的提示此前没有文案，玩家看到的是键名本身

## 改动

- 跟进 Thaumaturge 上游 1.21.1 分线的新版本：法杖焦点整体换到上游新的法术 API，源质容器改用物品能力视图，若干部件的成员随之易名
- 奥术合成终端第二页与源质访问卡第二页的研究文本改写
- 对 Thaumaturge 的依赖下限明确为 1.0.0。更旧的版本没有这批类名，装上去会在类加载时崩，而不是在加载时被拦下

## 其他（内部重构、构建、文档）

- 自建 Thaumaturge 补丁重新推导到新上游。保留索引构建移出服务端线程、索引载荷只编码一次、客户端跳过内容相同的索引三项；放弃分配无关指纹那一项 —— 上游自己重写了指纹并改成分节比对，动它的风险是缓存该失效时不失效，那是拿陈旧索引换一点速度
- 焦点现在是数据包驱动的法术行为，随代码补上法术部件与焦点数据映射两份文件，缺了它们焦点不生效
- 焦点不再自己收费。法杖路径已按法术复杂度统一扣费，再收一次就是双扣
- JEI 钉回 Thaumaturge 编译所用的 19.36。JEI 后来给 `ListElementInfo.createFromElement` 加了一个参数，Thaumaturge 的搜索索引仍按旧的三参签名调用，用更新的 JEI 一打开 JEI 搜索框就 `NoSuchMethodError`

---

# TECE 2.7.3.64

## 其他（内部重构、构建、文档）

- 跟进了 Thaumaturge 的版本

---

# TECE 2.7.3.63

## 其他（内部重构、构建、文档）

- 规范了代码注释，让它读起来不像是做梦梦到的天书

---

# TECE 2.x 版本更新（2.7.3.62）

## 其他（内部重构、构建、文档）

- 重构了 API 隔离层，把要素键收进 compat/thaumaturge 的 TcAspects 门面。此前有 5 个文件在隔离层之外直接引用上游的 TCAspects，共 19 处
- 拆分概率之箱的方块实体，把 AE 缓冲与取电拆为 GachaPower、把归属玩家与取脑规则拆为 GachaOwner，方块实体从 435 行降到 342 行
- README 的图标改由仓库根目录的 logo.png 提供

---

# TECE 2.x 版本更新（2.7.3.61）

## 修复

- 重构了代码结构，使其更适合现代化的环境
- 修复了源质元件工作台没有功能的问题
- 修复了源质元件工作台不能快捷操作的问题
- 修复了在源质元件工作台进行 shift 快速操作会导致崩溃的问题
- 修复了炼金供应器以及炼金无线接收器不能工作的问题
- 修复了奥术装配室内部 6 大基础元素 vis 进度条不能正确绘制的问题
- 修复了蒸馏编码台可能造成刷物品 bug 的问题
- 修复了蒸馏编码台可能吞掉空白样板的问题
- 修复了知识记录仪出现多余槽位的问题
- 修复了炼金供应器和炼金无线接收器的命名 id
- 修复了蕴魔观测器的命名 id
- 修复了炼金无线接收器不消耗能量却能工作的问题
- 修复了法杖核心：AE 扳手不能旋转 AE 方块部件的问题
- 修复了源质存储元件配方错误的问题
- 修复了奥术合成终端有时无法合成物品的问题
- 修复了本模组研究页有大量错误的问题
- 修复无线炼金接收器的未绑定提示会复述一整段绑定步骤的问题，现在只说明「尚未绑定」
- 修复炼金供应器与炼金无线接收器无法服务只实现 IEssentiaTransport 的「索取型」机器（例如注魔机）的问题：向它要什么就从网络取什么并交付，此前贴着这类机器一律拒收且没有任何提示
- 修复炼金供应器的 Jade 信息把同一件事说三遍的问题，现在只显示网格状态与已绑定接收器
- 修复无线接收器的绑定坐标用深灰、与 Jade 底色糊在一起的问题，改为白色
- 修复潜行操作会拦截其他部件的问题：现在只对确实装有奥术合成终端的线缆生效，潜行左击清除配对
- 修复蒸馏编码台的源质来源槽会丢失 JEI 写入名字的问题，同时修复破坏方块会把槽内物品带出的问题（只掉回样板）
- 修复知识记录仪槽位过滤不严的问题：核心槽只收知识核心，旁边二十一个只做镜像的槽位不再接受任何输入
- 修复无线奥术合成终端没有注册进 AE2 的 GridLinkables、导致终端绑定槽直接拒收该物品的问题
- 修复换行研究页面丢失行首样式的问题
- 修复有线与无线奥术合成终端共用一个 JEI 传输注册、导致落地终端丢失传输按钮的问题
- 修复 JEI 传输把标签第一个成员当模板、网络有货也报缺失的问题，改为取存量最多的变体
- 修复奥术合成终端把网络也算一份材料、网格内已有材料仍报 INGREDIENTS_CHANGED 的问题
- 修复两个奥术合成终端标题重复显示物品名的问题，改由 AE2 终端样式提供标题
- 修复奥术合成终端灵气花费行的定位，改由终端样式文档放置，随窗口缩放对齐美术
- 修复灵气花费行画到物品栏上并溢出窗口右缘的问题，改画在合成产物下方的木质条内、芯片右对齐
- 修复源质手势吞掉点击的问题：只有真正触发手势的点击才被消费，其余交回 AE2，罐子与安瓿可正常进网络
- 修复源质访问卡拔出后 ME 接口两行未清空、仍会把本行内容交出去的问题
- 修复 ME 接口带源质访问卡时 AE2 仍把源质反向搬进接口存储行的问题
- 修复访问卡抽出的源质写进接口存储行、网络总量虚增而来源容器不减少的问题
- 修复源质访问卡把存储行当过滤器、导致终端凭空付出源质的问题
- 修复咒波传输接口配对时每周期查询落点两次的问题，改为一次
- 修正开发环境运行：主源集不再重复声明自检目录，避免同一份源码被编译两次、服务文件被复制两份导致服务器启动前报错
- 修复蕴魔观测器在自身节点离线时误报「未检测到注魔祭坛」的问题，缺魔导手册的提示也不再被网络离线掩盖，面板日志标签错位两格一并修正
- 修复源质谐振仓过早判定为满的问题（旧逻辑按已处于的状态自我锁定暂停），改为按剩余空间与半刻用量判断，读档路径同样生效
- 修复中文语言文件中四处「韵魔观测器」错字（方块、Jade 配置、研究名、Jade 标题）
- 修复源质元件工作台的元件槽会吞下一整叠元件、升级卡槽会吞下整叠卡的问题
- 修复在玩家背包里 Shift 点击元件或升级卡时的越界报错（可移动范围少算 9 格，未含快捷栏）
- 修复对终端 Shift 点击时只处理光标下那一个的问题，现在会填满手持的整叠容器
- 修复专用服务器启动时因载荷注册引用客户端界面类而触发的 NoClassDefFoundError
- 补上 ME 要素存储元件的中英文名称（该语言键此前缺失）

## 新增

- 现在蕴魔观测器会在注魔合成结束时输出一次强度为 15 的红石信号
- 加入了无线奥术合成终端
- 加入了灵气连接卡
- 加入了源质访问卡
- 加入了概率之箱与概率之箱聚合器：脑槽绑定玩家、认知源质端口、加速卡、经神秘学知识池结算研究点、Jade 行、注魔配方与研究条目
- 源质存储总线改造为咒波传输接口，按网格 tick 工作，每 20 tick 一个周期，由 AE2 内存卡配对决定抽取端与释放端
- 咒波传输接口抽取端从所在区块取咒波，先付 100 AE、1 auram 与 1 ordo 每点，缓存至多 256 点；释放端每周期向前方 3×3×3 排出 4 点咒波
- 咒波传输接口新增 Jade 状态行：服务端写等待原因，客户端画拒绝红字或工作行
- 新增两条概率行为：抽出的咒波有 10% 概率凝为腐化源质入网络（需驱动器与可接收存储），约 1.5% 概率整批排进控制器所在区块
- 新增咒波传输接口的研究页（复用神秘学自身的 thaumaturge:flux）与对应研究条目
- 新增灵气连接卡，作为 AE2 升级卡装入无线奥术合成终端，所需灵气取自玩家所在区块灵气，网络不再收费
- 落地奥术合成终端部件也支持灵气连接卡，新增一格升级槽
- 无线奥术合成终端支持源质访问卡，罐子与安瓿手势与源质终端一致
- 源质访问卡：装入 ME 接口（方块与线缆部件均需一张）后，配置行标记的要素从相邻容器抽入网络
- 源质元件工作台接入 AE2 升级槽系统：元件本体可安装升级卡（三槽），面板随元件内容刷新

## 改动

- 蕴魔观测器恢复读取槽内的魔导手册；其合成配方改为消耗一台魔导透镜
- 咒波传输接口的七页研究文本改写为直述体，并给该页 +2 扭曲
- 源质存储总线改造为咒波传输接口外壳：菜单、界面、JEI 拖放、配置包、传输视图、能力注册与 ME 存储全部移除，改为继承 P2PTunnelPart 以内存卡配对
- 咒波传输接口合成从奥术工作台改为注魔（ae2:interface 催化剂 + 灵气中继器、2 个盐、ordo 与 vitium 水晶、湮灭核心与两个活塞，88 点灵气，不稳定度 7）
- 研究树调整：灵气中继接口上移到无线源质终端之上，咒波传输接口位于其上；基础能源学图标让给 ME 源质元件外壳
- 名称统一为「灵气」：灵气中继接口、灵气连接卡，以及三处自写研究文本
- 两颗升级卡（源质访问卡、灵气连接卡）改为注魔合成，替换原工作台配方，并补齐三个研究条目
- 研究门槛改为消耗要素：24 个条目补上 note_aspects（3~10 点），观测行按条目计费、理论行按 1.7.10 树保留 1~2 行
- AE 扳手核心改走 AE2 自身的部件操作
- 蕴魔观测器待机功耗定为 64 AE/t（此前是从上游继承的 256，一度降到 32）
- 原 infusion_monitor 整体更名为 occult_monitor（方块、包、类、注册 id、资源、配方、研究），中文名蕴魔观测器
- 炼金供应器与炼金无线接收器现在会从网络向「索取型」机器推送源质
- 炼金供应器的 Jade 显示改为文本叠加条形图（此前是 Jade 的箭头样式进度条），数值直接显示在条上
- 无线接收器的 Jade 第二行改为显示所绑定供应器的坐标（此前是每单位 10 AE 的单价说明，随统一收费后已删除）
- 无线连接的源质传输现在双向收费：以前只有从网络取出付 10 AE/单位，回灌供应器是白送；现统一收取并在源质移动前结算，取用改为逐单位付费、付不起即停
- 模组作者行更新为 BeiWuCN, Nividica, Alkusure86, Community
- 蕴魔观测器的气泡渲染距离限制为两区块，注魔气泡仅在八格内绘制
- 把机器相关子系统（源质谐振仓、蕴魔观测器、知识记录仪、源质供应器）连同各自宿主与包内协作者移入子包，blockentity 由一长串平铺类改为按机器分树的目录结构
- 玩偶的第二套模型改名为它所属方块的名字
- 移除大量手写物品/方块说明与用法提示：仅保留状态与实时数据（源质元件容量、知识核心内容、傀儡背包开销、终端绑定坐标、法杖核心名称与文本）
- 源质元件工作台界面改用 AE2 的升级界面样式，加入「按内容分区」与「清空分区」两个工具栏按钮；未装元件时分区格显示为灰色不可用
- 升级卡提示改由 AE2 的升级卡注册表生成，奥术装配室与源质元件工作台保持一致
- 奥术装配室、蕴魔观测器、源质谐振仓的面板读数改为从服务端同步获取

## 移除

- 移除源质输入总线与输出总线（部件、物品、菜单、界面、模型、贴图、两个配方与两项研究奖励），两项研究条目改指向源质存储总线
- 删除 16 张没有任何模型或界面引用、却带在 jar 里的贴图及其动画元数据
- 删除没有任何文件引用的 logo 图片
- 移除仓库内自测：selftest 源集、SelfTestHook／SelfTestProvider 与全部 THAUMICENERGISTICS_*_SELFTEST 开关，测试移到仓库外

## 其他（内部重构、构建、文档）

- 各机器的槽位注册为物品能力，管道只能取回「破坏方块会掉落的部分」；三种源质总线新增 IEssentiaTransport 能力，供应器连接、缓冲与谐振仓储罐改用 BlockCapabilityCache
- 删除四个没有任何地方加载的界面草稿（总线界面实际加载的都是 AE2 自己的样式）
- 更新了模组的 logo
- 更新了版本号
- 引入 compat/thaumaturge 隔离层：注魔祭坛、法杖、要素索引等上游 API 收敛到门面，同时拆分奥术装配室的巨型类并移除死代码
- 源码树重组：自检移入 selftest，奥术装配室相关移入 blockentity.assembler，客户端界面与按钮归入 client.gui，Jade 与 JEI 的客户端半边移出公共树，注册代码与载荷归入 network 包，删除空的 api 与 research 包
- 菜单载荷统一经 MenuNetwork 收发，菜单不再直接点名载荷，GhostGridSlot 改为接收写入器
- 奥术装配室的合成生命周期、灵气搜寻、提示文案与升级记账拆入独立类
- 蕴魔观测器、知识记录仪、源质元件工作台、供应器、源质谐振仓各自的槽位、网格与缓冲逻辑拆出，方块实体只负责 tick 与对外接口
- 注释与 javadoc 整理：长注释改写为散文体并把规则记入 docs/CONVENTIONS.md，超长注释行与过高 javadoc 收拢到项目审计计数归零，成员 javadoc 只保留带数字、断点或库特性说明的部分
- 依赖改为 maven.modrinth 坐标，Thaumaturge 改由抓取脚本从源码构建（会跑数据生成并校验要素 json 数量），新增 GitHub Actions 工作流并把各 action 升到当前主版本
- CI：Thaumaturge 的检出目录与编译产物移出 Actions 缓存，更正上游许可证（MIT）与 README 说明
- 灵气要素索引的哈希、加载、构建与写盘移出服务端线程，客户端在索引未变时跳过重建（以补丁形式放在 tools/patches）
- 为 Java 编译与 Javadoc 指定 UTF-8 编码
- 自检等待 Thaumaturge 灵气索引就绪后再读取，并优先向服务端请求注册表访问
- 精简 build.gradle、gradle.properties、settings.gradle 与相关类的注释
- 问题追踪地址改指向本仓库
- 工作笔记（注释规范清单、奥术合成终端设计笔记）取消跟踪并加入忽略
