> **安装前请注意 / Read before installing**
>
> **本版依赖 Thaumaturge 1.0.3 开发源码的固定提交 [`80903dd9`](https://github.com/Leclowndu93150/Thaumaturge/commit/80903dd9724ba20811b6ef3bc056a18082613ad6)。目前官方 CurseForge 的 Thaumaturge 1.0.2 缺少本版所需 API，不能直接搭配使用，可能导致启动失败或运行功能时出现 `NoSuchMethodError`。如果你只有官方 1.0.2，请暂缓安装 TECE 2.7.4.5，等待兼容更新。**
>
> 本仓库的 Gradle 自动获取并构建依赖，只用于从源码构建的开发流程；下载 TECE JAR 的玩家不会自动获得 Thaumaturge。TECE 的发布文件也不包含 Thaumaturge。
>
> **This release was built against the pinned Thaumaturge 1.0.3 development revision [`80903dd9`](https://github.com/Leclowndu93150/Thaumaturge/commit/80903dd9724ba20811b6ef3bc056a18082613ad6). The official CurseForge 1.0.2 file lacks required APIs and is not compatible. Combining it with TECE 2.7.4.5 may fail at startup or produce `NoSuchMethodError` during gameplay. If you only have the official 1.0.2 release, postpone installing this TECE version until a compatible update is available.**
>
> Automatic dependency preparation by Gradle is a source-build feature. Installing the TECE JAR does not download Thaumaturge, and the release does not bundle it.

# TECE 2.7.4.5

## 26.1.2 / NeoForge

This update includes the changes made since 2.7.4.4.

### Additions

- Add a dedicated Essentia Access Card slot to ME Pattern Providers. Providers can send essentia required by processing patterns and collect returned essentia through their native return buffer.
- Add provider integration for ExtendedAE and ExtendedAE Plus, including ExtendedAE block and panel providers and ExtendedAE Plus enhanced and mirror providers. The access card keeps its own inventory without occupying pattern expansion card slots.
- Add independent, live server switches under `[patternProviders]`: `ae2Enabled`, `extendedAeEnabled` and `extendedAePlusEnabled`, all enabled by default. Disabling a provider's integration stops new card insertion and essentia transfers while preserving installed cards and buffered contents; installed cards can still be removed.
- Add AE2 Wireless Terminal Library quantum bridge support to both wireless terminals when the library is installed. Each terminal supports a Quantum Bridge Card and a matching Quantum Entangled Singularity for connections beyond ordinary wireless range, including across dimensions.
- Localize configuration labels and descriptions, along with previously untranslated gameplay messages, in English, Simplified Chinese and Traditional Chinese. Local TOML comments follow the selected game language on the client; configuration keys remain stable.

### Fixes

- Restore direct use of local chunk VIS by placed arcane terminals. A Vis Connection Card is not required to access that VIS; it provides wand charging through a bound output.
- Share Vis Connection Cards and Essentia Access Cards bidirectionally between a wireless arcane terminal and its paired placed terminal. The two existing wireless card slots both map to the placed terminal's inventory, with each card stored only once. Quantum Bridge Cards remain local to the handheld terminal.
- Preserve existing cards during migration and resolve conflicts by returning cards to the player. If the inventory is full, keep the cards and close the shared menu with a localized message. Invalidate stale menus and shared inventories when the terminal connection changes.
- Refresh the wireless arcane terminal's available VIS readout from the player's current chunk, including after VIS changes and movement between chunks.
- Make `arcaneEssentiaAccess` control empty card-slot visibility in both arcane terminals. Turning it on restores both shared card slots; turning it off preserves access to installed cards and leaves the quantum slot available. Cards inserted remotely into the second slot also become visible on the placed terminal.
- Restore JEI recipe transfer to placed arcane terminals by sharing one handler per recipe category between the placed and wireless menus. Fix both ordinary and arcane crafting registration.
- Show the empty upgrade-slot icon and support Shift-click insertion and removal of provider access cards. Extra cards remain in the player inventory when the slot is full.
- Preserve native upgrade tooltips and include Essentia Access Card compatibility for AE2, ExtendedAE and ExtendedAE Plus providers. Tooltip entries follow each provider's live switch without reopening the screen, and the generic empty-slot label no longer obscures the upgrade list.
- Handle provider upgrade panels created or replaced by addon screens, avoiding missing slots and conflicting panel creation.
- Refresh provider return-buffer contents after changes and reject transfers through stale references after a provider block or panel is removed.
- Charge AE for environmental VIS only when the crafting transaction commits. Previewing or cancelling a transaction does not consume that energy.

### Changes

- Update the mod description to: “Connect Thaumaturge to AE2 with essentia storage, wireless terminals, and network-powered alchemy, infusion, and arcane crafting.”
- Remove the obsolete read-only shared-card display and reuse the wireless terminal's existing upgrade slots.

### Other (internal refactors, build, documentation)

- Organize Java packages by conventional NeoForge responsibilities and rename the seven registration classes from `Mod*` to `TECE*`.
- Split arcane terminal upgrade management, crafting runtime state and slot layout into separate components.
- Remove unused trace classes, diagnostic caches, timers, neighbour scans, redundant methods and unused imports from production code. Retain error reporting and keep verification code outside release artifacts.
- Add regression checks for shared cards, connection changes, card-slot visibility, VIS refresh, transaction payment, JEI handler selection and provider integration switches, including actual ExtendedAE and ExtendedAE Plus menus.
- Keep local verification sources and diagnostic scripts out of Git and exclude them explicitly from release artifacts; clean clones can build without local tests.
- Add GitHub Release artifact synchronization to CurseForge and correct its Minecraft version filtering.

# TECE 2.7.4.5

## 26.1.2 / NeoForge

本次更新包含 2.7.4.4 发布后的改动。

### 新增

- ME 样板供应器新增独立的源质访问卡槽，可发送加工样板所需的源质，并通过原生回收缓冲接收回送源质。
- 新增 ExtendedAE 和 ExtendedAE Plus 样板供应器兼容，包括 ExtendedAE 方块型、面板型供应器，以及 ExtendedAE Plus 增强供应器和镜像供应器。源质访问卡单独保存，不占用样板扩容卡槽。
- 新增服务端 `[patternProviders]` 配置：`ae2Enabled`、`extendedAeEnabled`、`extendedAePlusEnabled`，默认全部开启，分别实时控制对应模组的供应器兼容。关闭后停止新装卡和源质收发，保留已装卡及缓冲内容，已有卡片仍可取出。
- 安装 AE2 Wireless Terminal Library 时，两种无线终端支持量子桥卡和匹配的量子缠绕奇点，可超出普通无线范围连接网络，并支持跨维度连接。
- 补全配置名称、说明及此前未翻译的游戏提示，支持英文、简体中文和繁体中文。客户端本地 TOML 注释随游戏所选语言更新，配置键名保持稳定。

### 修复

- 恢复方块奥术终端直接使用所在区块 VIS 的功能。读取这部分 VIS 不需要魔力连接卡；魔力连接卡用于通过绑定输出为法杖充能。
- 无线奥术终端与绑定的方块奥术终端双向共享魔力连接卡和源质访问卡。无线终端原有两个卡槽均可对应方块终端库存，每张卡只保存一份；量子桥卡仍独立保存在无线终端中。
- 迁移旧卡时保留卡片，冲突卡退回玩家背包。背包满时保留卡片并显示本地化提示，关闭共享菜单。连接变化后使旧菜单和旧共享库存失效。
- 无线奥术终端的可用 VIS 提示实时读取玩家所在区块，源质之外的环境 VIS 变化及跨区块移动均能更新读数。
- `arcaneEssentiaAccess` 同时控制方块和无线奥术终端的空卡槽显示。开启后恢复两个共享卡槽；关闭后仍可取出已有卡片，量子卡槽保持可用。通过无线第二槽装入的卡也会在方块终端显示。
- 修复方块奥术终端不能从 JEI 转移配方的问题。方块与无线菜单按配方类别共用处理器，普通合成和奥术合成均可匹配。
- 补齐样板供应器空卡槽的灰色升级图标，支持 Shift 点击放入和取出访问卡；槽位已满时多余卡片留在背包。
- 保留原生升级提示，并为 AE2、ExtendedAE 和 ExtendedAE Plus 供应器补上源质访问卡兼容提示。提示随对应配置开关实时更新，无需重开界面；空槽名称不再遮挡可用升级列表。
- 兼容附属模组创建或替换升级面板的行为，避免缺失卡槽或面板创建冲突。
- 修复源质回收缓冲变更后内容缓存未及时刷新；供应器方块或面板拆除后，旧接口引用不能继续传入源质。
- 使用 AE 抽取环境 VIS 时，只在合成事务提交后扣费。预览或取消事务不会消耗这部分能量。

### 调整

- 将模组简介更新为：“Connect Thaumaturge to AE2 with essentia storage, wireless terminals, and network-powered alchemy, infusion, and arcane crafting.”
- 移除旧的共享卡只读图示，复用无线终端原有升级槽。

### 其他（内部重构、构建、文档）

- 按常见 NeoForge 模组职责整理 Java 包，将七个注册类的 `Mod*` 前缀统一改为 `TECE*`。
- 将奥术终端的升级卡管理、合成运行状态和槽位布局拆为独立组件。
- 清理正式源码中的无用追踪类、诊断缓存、计时、邻居扫描、冗余方法及无用导入，保留错误报告；验证代码不进入发布产物。
- 补充共享卡、连接变化、卡槽显隐、VIS 刷新、事务扣费、JEI 处理器匹配及供应器独立开关的回归检查，覆盖 ExtendedAE 和 ExtendedAE Plus 的实际菜单。
- 测试源码及诊断脚本仅保留在本地，不再纳入 Git，并为发布产物添加明确的排除规则；全新克隆无需本地测试即可构建。
- 增加 GitHub Release 产物同步到 CurseForge 的流程，并修正 Minecraft 版本筛选。
