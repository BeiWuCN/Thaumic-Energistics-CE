[English](README.en.md) · [Repository](https://github.com/beiwucn/Thaumic-Energistics-CE)

# Thaumic Energistics: CE

把 Thaumaturge 的源质系统接进 AE2 的 ME 网络，1.21.1 / NeoForge 上的社区版。

原版 Thaumic Energistics 停在 1.12.2，这个是在 Thaumaturge（1.21.1 的神秘时代移植）上重做的版本：配方、研究、机器界面基本都是重写的。CE 是 Community Edition，本来只是自己和朋友在整合包里用，顺手发出来。

需要：Minecraft 1.21.1、NeoForge 21.1.250 以上、Applied Energistics 2 19.2.x、Thaumaturge 0.4.4 以上（以及它的前置 GuideME）。jar 丢进 mods 目录就行。

## 内容

**存储和总线**：ME 源质存储元件 1k / 4k / 16k / 64k（另有一个创造元件）、源质存储总线、输入总线、输出总线、源质终端和无线源质终端。

**机器**：

- 奥术装配室 —— 把奥术工作台的配方丢给 AE2 的合成 CPU 自动跑，正面上会显示正在合成什么
- 蒸馏编码器 —— 把某个物品的要素组成编成样板
- 知识铭刻器 + 知识核心 —— 配方写在核心上，一台机器最多记 21 条
- 注魔供应器 / 注魔监视器 —— 前者自动给 `thaumaturge:infusion` 供料，后者挂在祭坛旁边报风险
- 源质谐振仓 —— 把多余的源质烧成 AE

**无线**：源质供应器配无线源质接收器，另外有个傀儡无线背包，装在傀儡身上，它搬的东西会直接进网络。

**奥术**：奥术合成终端（在终端里做奥术合成，缺的魔力由网络补）、AE 扳手法杖核心、Vis 中继接口。

以及完整的 Thaumaturge 研究树。

## 编译

需要 JDK 21，然后 `gradlew build`。

依赖里 8 个走 Modrinth 的 maven 仓库，版本固定在 `gradle.properties`。clone 下来直接就能编，不需要你手工准备任何东西：

- Applied Energistics 2 19.2.17：<https://modrinth.com/mod/ae2>
- GuideME 21.1.17：<https://modrinth.com/mod/guideme>
- JEI 19.57.0.444：<https://modrinth.com/mod/jei> —— 配方转移按钮
- Jade 15.10.6：<https://modrinth.com/mod/jade> —— 机器顶部信息面板
- Curios 9.5.1 / TerraBlender 4.1.0.8 / Lithostitched 1.8.0 / Apollib 1.2.0：Thaumaturge 的运行前置，本模组不 import 它们

**剩下那个 Thaumaturge 得你自己编一次。** 它是 All Rights Reserved：既没有 maven 制品，许可也禁止分发它、以及由它构建出的任何二进制（§3.1 连「fork 上的 GitHub Releases」都点名禁止），所以这个仓库没法替你带上它。许可 §2.4 允许你自己构建、自己使用，跑一次就行：

```sh
tools/fetch-thaumaturge.sh                      # Linux / macOS / CI
powershell -File tools/fetch-thaumaturge.ps1    # Windows
```

它把 <https://github.com/Leclowndu93150/Thaumaturge/> 在 `gradle.properties` 里 `thaumaturge_commit` 指定的那个 commit clone 下来、编成 jar 放进 `libs/`。没有这个 jar 时 `gradlew build` 会在配置阶段直接停下并告诉你该跑哪条命令，而不是退化成几百条找不到符号的报错。**别提交这个 jar，也别转发给别人。**

## 鸣谢

原版 Thaumic Energistics 是 Nividica 和社区做的，这个 CE 版接着 1.21.1 的移植往下做；Thaumaturge 是 Leclowndu93150 的项目，本模组依赖它的 API。高版本材质由 @麦淇淋 绘制。

## 许可

MIT，见 `LICENSE`。代码源自 Nividica 的 Thaumic Energistics，后者同样以 MIT 发布（上游 `LICENSE` 的版权行是 Chris 与 BrockWS）。那部分的版权仍在原作者手里，所以 `LICENSE` 里已一并写上上游的版权声明，再分发时请连着保留。
