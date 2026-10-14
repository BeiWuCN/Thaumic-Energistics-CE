# TECE 2.7.4.6

## Minecraft 26.1.2 / NeoForge

Changes since 2.7.4.5.

**Required versions:** AE2 26.1.13-beta and Thaumaturge 1.0.3 built from source revision `ee2abf2915fc86c28663737bb4c621ccc5f46bb4`. Older Thaumaturge 1.0.3 builds are incompatible. Thaumaturge is not bundled with this download.

### Added

- Add pattern provider support for AdvancedAE and AE2 Lightning Tech Reborn's 26.1.2 development branch, including AdvancedAE block and part providers and Lightning Tech regular, extended and Pigmee providers. Essentia Access Cards enable essentia delivery and return through the providers' return buffers. Each integration has its own configuration switch.
- Add the xiao_leng doll and update the Beiwu_CN doll model.
- Replace golem backpack geometry and all ten material variants with the new artwork. Geometry, UVs and attachment settings now load from a resource JSON and can be changed through resource packs and F3+T reloads.

### Fixed

- Wireless arcane terminals now keep their own crafting ingredients, crystals, wand and upgrade cards. They no longer share inventories or cards with a placed terminal, and neither wireless terminal requires a placed terminal binding.
- Both wireless terminals can open without a network connection, showing an empty network inventory while keeping local slots accessible. A Quantum Bridge Card and matching entangled singularity can connect through a loaded, powered quantum bridge without binding to a wireless access point.
- Replace the wireless terminal binding tooltip with “Connected” when a wireless or quantum link is configured.
- Correct AdvancedAE provider upgrade-panel positioning and avoid duplicate panels when ExtendedAE Plus is installed.
- Preserve ordinary item transfer when an Essentia Access Card is installed. Correct essentia handling for addon provider storage targets and return buffers, including ExtendedAE Plus mirror providers.
- Adapt infusion diagnostics, wand access and essentia container handling to the pinned Thaumaturge API. Refresh neighbour state and preserve transfer remainders when containers change.
- Correct the golem backpack antenna UVs and back-face culling so the textured cube and thin outline render properly. Lower the backpack by one model pixel. The antenna lights briefly after a successful transfer and stays dark while idle or when a transfer is refused.

### Changes

- Simplify server configuration to common settings and numerical values. Remove redundant core-feature switches and the option to spend AE to make up missing environmental VIS. Arcane crafting requires sufficient VIS; existing supported configuration values are retained when the file is corrected.
- Rename the mod entry class to `ThaumicEnergisticsCE` and verify the local Thaumaturge dependency against the pinned source revision before building.

AdvancedAE was tested with version 26.1.7. Lightning Tech compatibility was tested with its 26.1.2 development source; the test build needed a local fix for early recipe ingredient initialization. These optional mods are not bundled.

---

# TECE 2.7.4.6

## Minecraft 26.1.2 / NeoForge

本次更新包含 2.7.4.5 发布后的改动。

**前置版本：** AE2 26.1.13-beta，以及由源码修订 `ee2abf2915fc86c28663737bb4c621ccc5f46bb4` 构建的 Thaumaturge 1.0.3。较早的 Thaumaturge 1.0.3 构建不兼容，本次下载不包含 Thaumaturge。

### 新增

- 新增 AdvancedAE 和 AE2 Lightning Tech Reborn 26.1.2 开发分支的样板供应器兼容，覆盖 AdvancedAE 的方块型、面板型供应器，以及 Lightning Tech 的普通、扩展和 Pigmee 供应器。源质访问卡允许发配源质，并通过供应器原生回收缓冲接收回送源质；各项兼容均有独立配置开关。
- 新增 xiao_leng 玩偶，更新 Beiwu_CN 玩偶模型。
- 更新傀儡背包模型及十种材质外观。形状、UV 和挂载位置改为读取资源 JSON，可通过资源包修改并用 F3+T 重载。

### 修复

- 无线奥术合成终端独立保存合成材料、水晶、法杖和升级卡，不再与方块终端共享库存或卡片。两种无线终端均不再要求绑定方块终端。
- 两种无线终端未连接网络时也能打开，网络物品区为空，本地槽位仍可使用。装入量子桥接卡和匹配的量子缠绕态奇点后，可通过已加载、已供电的量子桥接入网络，无需再绑定无线访问节点。
- 配置了无线或量子连接时，终端物品提示统一显示“已连接”。
- 修正 AdvancedAE 样板供应器升级侧栏的位置，避免安装 ExtendedAE Plus 后重复创建面板。
- 安装源质访问卡后保留普通物品传输。修正附属供应器存储目标与回收缓冲的源质处理，补齐 ExtendedAE Plus 镜像供应器兼容。
- 适配指定 Thaumaturge 修订中的注魔诊断、法杖和源质容器 API。刷新邻接状态，保留容器变化时的传输余量。
- 修正背包天线 UV 和背面剔除，恢复方格贴图与细边框，背包整体下移一个模型像素。成功传输后天线短暂发光，空闲或传输失败时不发光。

### 调整

- 精简服务端配置，仅保留常用功能和数值。移除冗余的核心功能开关，以及消耗 AE 补足环境 VIS 的选项；奥术合成需要足够的 VIS。配置修正时保留仍受支持的自定义值。
- 模组主类改名为 `ThaumicEnergisticsCE`。构建前校验本地 Thaumaturge 依赖是否与指定源码修订一致。

AdvancedAE 使用 26.1.7 测试；Lightning Tech 使用其 26.1.2 开发源码测试，测试构建需要本地修正配方材料过早初始化的问题。这些可选联动模组均不随本模组发布。
