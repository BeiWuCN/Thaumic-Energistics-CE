# Thaumic Energistics: CE 2.7.5.1

Minecraft **26.1.2** / NeoForge **26.1.2.122** / Java **25**.
Requires AE2 **26.1.13-beta** and Thaumaturge **1.1.0**, built from source revision `c71c2a275ca12278984bee4c7b3918e41f17033f`. Thaumaturge is not included in this release; older 1.0.3 builds are incompatible.

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
- Modifications and port code: Copyright (c) 2026 **AlkSur**, **GNU LGPL-3.0**. Original source by **Chris, BrockWS** retains **MIT** notices. Complete license texts are supplied.
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
- 移植与修改代码：Copyright (c) 2026 **AlkSur**，采用 **GNU LGPL-3.0**；**Chris、BrockWS**的原始代码保留 **MIT** 授权声明，附完整协议文本。
- 福鲁伊克斯脑部造型参考 **sparkskye** 的 **Pixel Brain**（**CC BY 4.0**），重新建模、配色并增加植入结构。

## Validation / 验证

Local Java 25 build passed. Dedicated-server checks passed for normal/Fluix brain charging and exact energy debit, insufficient-energy conservation, vibration-chamber generation and ME output, brain upgrade/persistence/removal, 96 AE2 crystal-shape comparisons and old-ID aliases, and assembler bar synchronization. Client rendering still depends on the installed resource packs and graphics environment.
