# Thaumic Energistics: CE 2.7.5

Minecraft **26.1.2** / NeoForge **26.1.2.122** / Java **25**.
Requires AE2 **26.1.13-beta** and Thaumaturge **1.1.0**, built from source revision `c71c2a275ca12278984bee4c7b3918e41f17033f`. Thaumaturge is not included in this release; older 1.0.3 builds are incompatible.

## English

### Added and changed
- Add Thaumic Crystals, dust, circuit boards and processors, four budding-block qualities, four growth stages, infusion restoration, and 256k essentia storage and components.
- Reorganize the research tree and recipe progression. Circuit boards use AE2's existing Inscriber press; revise shaped layouts and research gates.
- Add the infusion-crafted Fluix Brain as forbidden research after the Box of Probability. Normal-brain payout chances range from 45% to 67%; Fluix Brain chances range from 60% to 85%. Existing speed-card timing is preserved.
- Update machine, GUI, cell, component, core and card artwork. Rebuild the Box of Probability as a complete two-block-tall machine, with a separate upgraded brain model and knowledge-essentia culture liquid.
- Update to Thaumaturge 1.1.0 APIs and move Java packages to `com.beiwu.thaumicenergistics_ce`.

### Fixes
- Repair Knowledge Inscriber save/delete actions, recipe preview synchronization, slot feedback and recipe-display registration.
- Correct probability-box model UVs, inventory rendering, collision/interaction across both block spaces and status-screen artwork. Upgrading returns the ordinary zombie brain; removing the upgraded brain jar returns an empty jar.
- Prevent partial energy loss when a probability-box round cannot be funded; use simulated network extraction to check available power.
- Align crystal names with AE2: flawless, flawed, chipped and damaged. Rename their block/item registry IDs to `*_budding_thaumic_crystal`, with aliases for old saved IDs. Only flawless budding blocks resist growth decay; other tiers follow AE2's 1/12 decay chance and end as a Thaumic Crystal Block. Compress/decompress crystals at 4:1.
- Match all crystal-bud and cluster collision/selection shapes to AE2 in six directions, including waterlogged states.
- Restore the Arcane Assembly Chamber's own frame and correct its glass rendering; show internal light strips while crafting.
- Fix the assembler's six aspect bars: exact small values, decreasing/zero readings, shared-capacity scale and crafting-progress reset. Keep the artist's gradient textures and concise Aer/Aqua/Ignis/Ordo/Perditio/Terra tooltips.
- Correct machine active-state artwork, vibration-chamber side UVs and creative-cell animation metadata.

### Licensing and credits
- Textures created by **麦淇淋**: **CC BY-NC-SA 3.0**. See `LICENSE-ASSETS.md` for scope and third-party attribution.
- Modifications and port code: Copyright (c) 2026 **AlkSur**, **GNU LGPL-3.0**. Original source by **Chris, BrockWS** retains **MIT** notices. Complete license texts are supplied.
- Fluix brain anatomy references **Pixel Brain by sparkskye**, **CC BY 4.0**; geometry redrawn and recolored with added neural implants.

## 中文

### 新增与调整
- 新增神秘水晶、粉、电路板、处理器，四品质母岩、四阶段晶芽、注魔修复与 256k 源质存储组件和元件。
- 重排研究树和配方进度，电路板使用 AE2 现有压印模板，调整有序合成布局及研究门槛。
- 概率之箱后新增福鲁伊克斯之脑禁忌研究，通过注魔制作。普通脑成功率为 45%～67%，升级后为 60%～85%，保留加速卡原有耗时规则。
- 更新机器、界面、元件、组件、核心和访问卡美术。概率之箱改为完整的两格高机器，增加升级脑模型和知识源质培养液，英文名改为 Box of Probability。
- 适配 Thaumaturge 1.1.0，Java 包名迁移至 `com.beiwu.thaumicenergistics_ce`。

### 修复
- 修复知识记录仪保存、删除、预览栏同步、槽位反馈及查看配方时的网络显示类型注册问题。
- 修复概率之箱模型 UV、物品栏显示、上下两格碰撞与交互和状态屏幕。升级返还普通僵尸之脑；取下升级后的缸中之脑得到空罐。
- 修复概率之箱电量不足时可能丢失部分能量的问题，启动前通过模拟提取确认可用电量。
- 母岩名称统一为无瑕、有瑕、开裂、损坏，注册 ID 改为 `*_budding_thaumic_crystal`，添加旧存档 ID 兼容映射。无瑕母岩不退化，其余按 AE2 的十二分之一概率退化，最终成为神秘水晶块；压缩与拆解改为四合一和一拆四。
- 四阶段晶芽、晶簇的六方向碰撞箱与选取框对齐 AE2，并检查含水状态。
- 恢复奥术装配室自己的框架，修正玻璃并增加合成时的内部灯带。
- 修复六元素条的微量数值、消耗后下降、清零、共享容量刻度和合成进度复位，保留画师渐变贴图及简洁的拉丁元素名提示。
- 修复机器工作状态美术、源质谐振仓侧面 UV 和创造元件动画元数据。

### 协议与署名
- **麦淇淋**创作的材质采用 **CC BY-NC-SA 3.0**，范围和第三方署名见 `LICENSE-ASSETS.md`。
- 移植与修改代码：Copyright (c) 2026 **AlkSur**，采用 **GNU LGPL-3.0**；**Chris、BrockWS**的原始代码保留 **MIT** 授权声明，附完整协议文本。
- 福鲁伊克斯脑部造型参考 **sparkskye** 的 **Pixel Brain**（**CC BY 4.0**），重新建模、配色并增加植入结构。

## Validation / 验证

Local Java 25 build passed. Dedicated-server checks passed for normal/Fluix brain charging and exact energy debit, insufficient-energy conservation, vibration-chamber generation and ME output, brain upgrade/persistence/removal, 96 AE2 crystal-shape comparisons and old-ID aliases, and assembler bar synchronization. Client rendering still depends on the installed resource packs and graphics environment.
