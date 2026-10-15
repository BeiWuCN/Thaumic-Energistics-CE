![Thaumic Energistics: CE](https://cdn.jsdelivr.net/gh/BeiWuCN/Thaumic-Energistics-CE@1.21.1/logo.png)

[English](README.en.md) · [仓库](https://github.com/beiwucn/Thaumic-Energistics-CE) · [问题反馈](https://github.com/beiwucn/Thaumic-Energistics-CE/issues)

# Thaumic Energistics: CE

## 简介

一个关于源质与网络的 Minecraft 模组。它把 Thaumaturge 的源质接进 AE2 的 ME 网络：要素存在网络上、在网络上搬运、由网络自动合成。

本模组是原版 Thaumic Energistics（止于 1.12.2）在 1.21.1 上的重做，基于 Thaumaturge 的 API 编写。

运行环境：Minecraft 1.21.1、NeoForge 21.1.250 或更高、Applied Energistics 2 19.2.x、Thaumaturge 1.0.2（本次适配目标）

内容包含源质存储元件、源质终端、若干源质机器、无线连接与奥术合成，以及一套完整的 Thaumaturge 研究树。

## 编译

编译使用 JDK 21 与 `gradlew build`。

AE2、JEI、Jade 等依赖由 Modrinth Maven 提供，版本固定在 `gradle.properties`。

本分支使用已发布的 Thaumaturge 1.0.2，将自行取得的文件放到 `libs/thaumaturge-1.21.1-NeoForge-1.0.2.jar`，或通过 `-Pthaumaturge_jar=绝对路径` 指定。此依赖不会打包进 TECE。

Windows 可运行 `tools/fetch-thaumaturge.ps1 -SourceJar '文件路径'` 安装本地依赖；该脚本不再从旧源码修订构建。

联动开发环境运行 `tools/install-compat-mods.ps1` 安装锁定版本，详见 `tools/compat-mods.lock.json`。构建验证使用 `gradlew portRegression build`；真实游戏回归使用 `gradlew -PportRuntimeRegression runPortRuntimeRegression`，追加 `-PwithoutCompatibilityMods` 可检查未装联动模组的情况。

## 许可

MIT，见 `LICENSE`。

代码源自 Nividica 的 Thaumic Energistics，后者同样以 MIT 发布。那部分版权仍属原作者，`LICENSE` 中已一并保留上游的版权声明（Chris 与 BrockWS），再分发时请保留该声明。Thaumaturge 是 Leclowndu93150 的项目。高版本材质由 @麦淇淋 绘制。

## 问题反馈

崩溃、建议与 bug 都在 [issues 页](https://github.com/beiwucn/Thaumic-Energistics-CE/issues) 提交。

提交前确认使用最新版本，确认该问题尚未被回答或修复，并确认它是一个有效的问题。原版 Minecraft、AE2 或 Thaumaturge 本身可以实现的功能，这类建议视为无效；要求更小、更紧凑或更高效的版本，同样视为无效。

点击 New Issue 开始。仓库提供模板时按模板填写，模板中列有需要补充的信息。填写完成后点击 Submit New Issue，然后等待回复。

信息越完整，问题定位越快，修复版本到手越早。不符合上述要求的 issue 可能被直接关闭。

本仓库只处理 1.21.1 / 26.1.2 上的 CE 版本。止于 1.12.2 的原版 Thaumic Energistics 是另一个项目。
