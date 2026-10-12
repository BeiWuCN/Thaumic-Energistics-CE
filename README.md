# Thaumic Energistics: CE

Thaumic Energistics: CE（神秘能源 CE）将 Thaumaturge 的源质与魔力接入 Applied Energistics 2 的 ME 网络。你可以集中存储源质，通过终端查找和取用要素，让网络为炼金、注魔与奥术合成提供材料。

项目延续原版 Thaumic Energistics 的玩法，并基于 Thaumaturge 适配新的 Minecraft 版本。当前分支面向 **Minecraft 26.1.2 / NeoForge**；1.21.1 版本请查看 [1.21.1 分支](https://github.com/BeiWuCN/Thaumic-Energistics-CE/tree/1.21.1)。

[English](README.en.md) · [下载](https://github.com/BeiWuCN/Thaumic-Energistics-CE/releases) · [更新记录](CHANGELOG-2.7.4.5.md) · [问题反馈](https://github.com/BeiWuCN/Thaumic-Energistics-CE/issues)

<img src="https://raw.githubusercontent.com/BeiWuCN/Thaumic-Energistics-CE/26.1.2/src/main/resources/icon.png" alt="Thaumic Energistics: CE" width="420">

## 主要功能

- **源质存储与传输**：使用 ME 源质存储单元保存要素，给 ME 输入、输出和存储总线装上源质访问卡，即可与外部源质容器交互。
- **源质终端**：查看网络中的源质，按当前语言搜索名称，并通过容器存取源质。无线源质终端可以远程访问网络。
- **奥术合成**：有线和无线奥术合成终端提供法杖、水晶槽与合成费用提示，奥术装配室用于自动合成。
- **魔力中继**：魔力访问卡可以绑定魔力中继接口的输出端，为终端槽位中的法杖充能。方块奥术合成终端直接消耗所在区块的 VIS，无需装卡。无线奥术终端会只读同步绑定方块终端的魔力访问卡；卡仍保存在方块终端内，其他升级卡保持独立。绑定端必须处于加载且在线的状态。
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
| Thaumaturge | 1.0.3，源码提交 `80903dd9` |

AE2 和 Thaumaturge 为必需依赖。JEI 提供配方查看与转移，Jade 提供设备提示，Curios 提供无线终端的饰品栏支持。无线终端的打开按键可在 AE2 按键分类中设置，默认未绑定。

服务端配置位于 `config/thaumicenergistics_ce-server.toml`。`terminals.restrictEssentiaVisibility` 默认关闭，开启后普通 ME 终端不显示源质。奥术终端的源质存取默认关闭；开启 `terminals.arcaneEssentiaAccess` 并装入源质访问卡后才可使用；无线奥术终端耗电取用玩家所在区块 VIS 的功能默认关闭；方块奥术终端直接取用所在区块 VIS，不受此开关影响。两种访问卡的用途不同。

## 从源码构建

需要 JDK 25。先按项目固定的版本构建 Thaumaturge，再构建本模组。

Windows：

```powershell
powershell -File tools/fetch-thaumaturge.ps1
.\gradlew.bat build
```

Linux / macOS：

```sh
./tools/fetch-thaumaturge.sh
./gradlew build
```

依赖版本与 Thaumaturge 提交固定在 `gradle.properties`。脚本会获取 Thaumaturge 源码、应用项目补丁、生成数据，并将构建出的依赖 JAR 放入 `libs`。本模组的产物位于 `build/libs`，不包含 Thaumaturge 的二进制。

## 问题反馈

请在 [Issues](https://github.com/BeiWuCN/Thaumic-Energistics-CE/issues) 提交问题或建议。报告问题时请附上 Minecraft、NeoForge、TECE、AE2 和 Thaumaturge 的版本，以及复现步骤、日志或崩溃报告。涉及界面问题时可以附上截图。

此仓库处理 CE 版本的问题；原版 Thaumic Energistics 的问题请反馈给对应项目。

## 许可与致谢

本项目使用 [MIT 许可](LICENSE)。部分代码源自 Nividica 的 [Thaumic Energistics](https://github.com/Nividica/ThaumicEnergistics)，原作者 Chris、BrockWS 的版权声明保留在许可文件中。

Thaumaturge 由 Leclowndu93150 开发，高版本材质由 @麦淇淋 绘制。Thaumaturge 使用独立许可，请遵守其上游许可；本仓库不分发其二进制。
