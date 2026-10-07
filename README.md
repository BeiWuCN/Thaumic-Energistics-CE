![Thaumic Energistics: CE](https://cdn.jsdelivr.net/gh/BeiWuCN/Thaumic-Energistics-CE@2.0-Renewed/logo.png)

[English](README.en.md) · [仓库](https://github.com/beiwucn/Thaumic-Energistics-CE) · [问题反馈](https://github.com/beiwucn/Thaumic-Energistics-CE/issues)

# Thaumic Energistics: CE

## 简介

一个关于源质与网络的 Minecraft 模组。它把 Thaumaturge 的源质接进 AE2 的 ME 网络：要素存在网络上、在网络上搬运、由网络自动合成。

本模组是原版 Thaumic Energistics（止于 1.12.2）在 1.21.1 上的重做，基于 Thaumaturge 的 API 编写。

运行环境：Minecraft 1.21.1、NeoForge 21.1.250 或更高、Applied Energistics 2 19.2.x、Thaumaturge 0.4.4 或更高

内容包含源质存储元件、源质终端、若干源质机器、无线连接与奥术合成，以及一套完整的 Thaumaturge 研究树。

## 编译

编译使用 JDK 21 与 `gradlew build`。

依赖由 Modrinth 的 maven 仓库提供，版本固定在 `gradle.properties`：Applied Energistics 2、JEI、Jade、Thaumaturge。

Thaumaturge 需要本地构建一次。它以 All Rights Reserved 发布，没有 maven 制品，其许可也禁止分发它以及由它构建的任何二进制，所以本仓库不包含它。许可 §2.4 允许自行构建并使用：

```sh
tools/fetch-thaumaturge.sh                      # Linux / macOS / CI
powershell -File tools/fetch-thaumaturge.ps1    # Windows
```

脚本按 `gradle.properties` 中 `thaumaturge_commit` 指定的 commit 克隆 <https://github.com/Leclowndu93150/Thaumaturge/>，并把构建出的 jar 放进 `libs/`。该 jar 存在时不需要上述命令；缺少它时 `gradlew build` 在配置阶段停止并给出这两条命令。这个 jar 不提交、不转发。

## 许可

MIT，见 `LICENSE`。

代码源自 Nividica 的 Thaumic Energistics，后者同样以 MIT 发布。那部分版权仍属原作者，`LICENSE` 中已一并保留上游的版权声明（Chris 与 BrockWS），再分发时请保留该声明。Thaumaturge 是 Leclowndu93150 的项目。高版本材质由 @麦淇淋 绘制。

## 问题反馈

崩溃、建议与 bug 都在 [issues 页](https://github.com/beiwucn/Thaumic-Energistics-CE/issues) 提交。

提交前确认使用最新版本，确认该问题尚未被回答或修复，并确认它是一个有效的问题。原版 Minecraft、AE2 或 Thaumaturge 本身可以实现的功能，这类建议视为无效；要求更小、更紧凑或更高效的版本，同样视为无效。

点击 New Issue 开始。仓库提供模板时按模板填写，模板中列有需要补充的信息。填写完成后点击 Submit New Issue，然后等待回复。

信息越完整，问题定位越快，修复版本到手越早。不符合上述要求的 issue 可能被直接关闭。

本仓库只处理 1.21.1 / 26.1.2 上的 CE 版本。止于 1.12.2 的原版 Thaumic Energistics 是另一个项目。
