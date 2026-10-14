# Thaumic Energistics: CE

Thaumic Energistics: CE（神秘能源 CE）将 Thaumaturge 的源质与魔力接入 Applied Energistics 2 的 ME 网络。你可以集中存储源质，通过终端查找和取用要素，让网络为炼金、注魔与奥术合成提供材料。

项目延续原版 Thaumic Energistics 的玩法，并基于 Thaumaturge 适配新的 Minecraft 版本。当前分支面向 **Minecraft 26.1.2 / NeoForge**；1.21.1 版本请查看 [1.21.1 分支](https://github.com/BeiWuCN/Thaumic-Energistics-CE/tree/1.21.1)。

[English](README.en.md) · [下载](https://github.com/BeiWuCN/Thaumic-Energistics-CE/releases) · [更新记录](CHANGELOG-2.7.4.6.md) · [问题反馈](https://github.com/BeiWuCN/Thaumic-Energistics-CE/issues)

<img src="https://raw.githubusercontent.com/BeiWuCN/Thaumic-Energistics-CE/26.1.2/src/main/resources/icon.png" alt="Thaumic Energistics: CE" width="420">

## 主要功能

- **源质存储与传输**：使用 ME 源质存储单元保存要素，给 ME 输入、输出和存储总线装上源质访问卡，即可与外部源质容器交互。
- **样板供应器兼容**：AdvancedAE 的方块与面板供应器，以及 AE2 Lightning Tech Reborn 26.1.2 开发分支的普通、扩展和 Pigmee 供应器，可识别奥术样板；安装源质访问卡后可收发源质。两项兼容可分别在配置中关闭。
- **源质终端**：查看网络中的源质，按当前语言搜索名称，并通过容器存取源质。无线源质终端可以远程访问网络。
- **奥术合成**：有线和无线奥术合成终端提供法杖、水晶槽与合成费用提示，奥术装配室用于自动合成。
- **魔力中继**：魔力访问卡可以绑定魔力中继接口的输出端，为终端槽位中的法杖充能。方块奥术合成终端直接消耗所在区块的 VIS，无需装卡。无线终端无需绑定方块终端，合成材料、水晶、法杖和升级卡均保存在手持终端自身。绑定端必须处于加载且在线的状态。
- **炼金与注魔供给**：通过炼金供应器、注魔供应器等设备，让 ME 网络参与 Thaumaturge 的制作流程。
- **傀儡无线背包**：让傀儡与 ME 网络交换物品，并使用支持的材料选择背包外观。
- **研究与配置**：通过魔导手册研究解锁内容；服务端可以调整机器数值和功能开关。

## 安装与运行

从 [Releases](https://github.com/BeiWuCN/Thaumic-Energistics-CE/releases) 下载名称包含 `26.1.2-neoforge` 的模组 JAR，放入实例的 `mods` 文件夹。`-sources.jar` 是源码包，不用于安装。

当前版本的构建与测试环境：

| 项目 | 版本 |
| --- | --- |
| Minecraft | 26.1.2 |
| NeoForge | 26.1.2.112 |
| Java | 25 |
| Applied Energistics 2 | 26.1.13-beta |
| Thaumaturge | 1.0.3，源码提交 `ee2abf29`（旧 1.0.3 构建不适用） |

AE2 和 Thaumaturge 为必需依赖。JEI 提供配方查看与转移，Jade 提供设备提示，Curios 提供无线终端的饰品栏支持。无线终端的打开按键可在 AE2 按键分类中设置，默认未绑定。

两种无线终端未绑定网络时也可打开，网络库存留空；无线奥术终端中的材料、水晶、法杖和升级卡仍可管理。安装量子桥接卡与有效的量子缠绕态奇点后，通过已加载且供电的量子桥连接网络，无需绑定无线访问点。

服务端配置位于 `config/thaumicenergistics_ce-server.toml`。`terminals.restrictEssentiaVisibility` 默认关闭，开启后普通 ME 终端不显示源质。奥术终端的源质存取默认关闭；开启 `terminals.arcaneEssentiaAccess` 并装入源质访问卡后才可使用；无线奥术终端直接取用玩家所在区块的 VIS，方块终端取用自身所在区块的 VIS。两种访问卡的用途不同。

## 从源码构建

需要 JDK 25 和 Git。直接执行 Gradle 构建即可，首次导入时自动在线准备固定版本的 Thaumaturge 依赖。

Windows：

```powershell
.\gradlew.bat build
```

Linux / macOS：

```sh
./gradlew build
```

依赖版本与 Thaumaturge 提交固定在 `gradle.properties`。Gradle 会获取 Thaumaturge 源码、应用 `gradle/patches` 中的补丁、生成数据，并将构建出的依赖 JAR 放入 `libs`。首次准备需要联网；之后可复用本地依赖。使用 `prepareThaumaturge -PrebuildThaumaturge` 可重新准备。本模组的产物位于 `build/libs`，不包含 Thaumaturge 的二进制。

## 问题反馈

请在 [Issues](https://github.com/BeiWuCN/Thaumic-Energistics-CE/issues) 提交问题或建议。报告问题时请附上 Minecraft、NeoForge、TECE、AE2 和 Thaumaturge 的版本，以及复现步骤、日志或崩溃报告。涉及界面问题时可以附上截图。

此仓库处理 CE 版本的问题；原版 Thaumic Energistics 的问题请反馈给对应项目。

## 许可与致谢

本项目使用 [MIT 许可](LICENSE)。部分代码源自 Nividica 的 [Thaumic Energistics](https://github.com/Nividica/ThaumicEnergistics)，原作者 Chris、BrockWS 的版权声明保留在许可文件中。

Thaumaturge 由 Leclowndu93150 开发，高版本材质由 @麦淇淋 绘制。Thaumaturge 使用独立许可，请遵守其上游许可；本仓库不分发其二进制。
