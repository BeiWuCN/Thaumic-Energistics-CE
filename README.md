# Thaumic Energistics: CE

**把 Thaumaturge 的源质接进 AE2 的 ME 网络 —— 1.21.1 / NeoForge 社区版**
**Bridging Thaumaturge essentia and Applied Energistics 2 ME networks — Community Edition**

| | |
| --- | --- |
| Minecraft | 1.21.1 |
| 加载器 / Loader | NeoForge 21.1.250+ |
| 版本 / Version | 1.4.156.2039 |
| 前置 / Requires | Applied Energistics 2 19.2.x · Thaumaturge 0.4.4+ · GuideME 21.1.x |
| 许可 / License | MIT（见下方说明 / see the note at the end） |

---

## 中文

### 这是什么

Thaumic Energistics 把神秘使的源质体系接进 AE2 的 ME 网络：源质可以像物品一样被存储、传输、自动合成，奥术工作台、注魔祭坛和傀儡都能挂进网络。本仓库是它的 **社区版（CE）**，对应 **Minecraft 1.21.1 / NeoForge**，接续 Nividica 的 1.12.2 原版与 Thaumaturge 移植版继续开发。

### 内容

**存储与网络**

- ME 源质存储元件 1k / 4k / 16k / 64k，外加创造元件与元件外壳
- 源质存储总线、源质输入总线、源质输出总线、源质等级发射器
- 源质终端、无线源质终端

**自动化**

- **奥术装配室（Arcane Assembly Chamber）** —— 把奥术工作台配方交给 AE2 的合成 CPU，由网络自动跑；面板上会显示正在合成什么
- **蒸馏编码器** —— 把物品的要素组成编成样板
- **知识铭刻器 + 知识核心** —— 配方存进核心，一台机器最多 21 条
- **注魔供应器 / 注魔监视器** —— 为 `thaumaturge:infusion` 供料并盯住祭坛
- **源质振动室** —— 拿源质换 AE

**无线**

- 无线连接器、源质供应器 + 无线源质接收器
- **傀儡无线背包** —— 装在傀儡身上，它搬运的东西直接倒进 ME

**奥术相关**

- 奥术合成终端 —— 在终端里直接做奥术合成，魔力不够由网络补
- AE 扳手法杖核心 —— 法杖一敲就连上网络
- Vis 中继接口 —— 把节点的魔力接到装配室

**其它**：完整的 Thaumonomicon 研究树（24 条），中英双语；还有一只 `Alkusure86 Fumo` 🧸

### 安装

1. 装好 NeoForge 21.1.250+、Applied Energistics 2 19.2.x、Thaumaturge 0.4.4+ 以及它的前置 GuideME。
2. 把 `thaumicenergistics-*.jar` 放进 `mods/`。

### 从源码构建

需要 JDK 21：

```bash
./gradlew build          # 产物在 build/libs/
```

依赖不走公网 maven，而是放在 `libs/`（`build.gradle` 用 `fileTree` 直接吃目录）：

- **Thaumaturge** —— 从它的源码树执行 `gradlew jar`，把产物复制进 `libs/`
- **Applied Energistics 2 19.2.17** —— <https://modrinth.com/mod/ae2>
- **GuideME 21.1.x** —— <https://modrinth.com/mod/guideme>

Curios / TerraBlender / JEI 只在开发运行时需要，是公开依赖。

### 鸣谢

- 原项目 **Thaumic Energistics** 的作者 **Nividica** 与社区；本 CE 版建立在 1.21.1 / NeoForge 的移植工作之上。
- **高版本材质由 @麦淇淋 绘制。** 🎨
- 感谢 Thaumaturge 与 Applied Energistics 2 两个项目提供的 API。

### 许可

本仓库以 **MIT** 发布（见 `LICENSE`）。它源自以 **LGPL-3.0** 发布的 Thaumic Energistics（作者 Nividica 及社区），上游部分的版权仍归原作者所有；再分发时请一并遵守上游协议。

---

## English

### What this is

Thaumic Energistics plugs Thaumaturge's essentia system into AE2's ME network: essentia can be stored, moved and autocrafted like any other resource, and arcane workbenches, infusion altars and golems can all be wired into the grid. This repository is the **Community Edition (CE)** of that addon for **Minecraft 1.21.1 / NeoForge**, continuing from Nividica's 1.12.2 original and the Thaumaturge port.

### What's inside

**Storage and network**

- ME Essentia Storage Components 1k / 4k / 16k / 64k, plus a creative component and the cell casing
- Essentia Storage Bus, Import Bus, Export Bus and Level Emitter
- Essentia Terminal and Wireless Essentia Terminal

**Automation**

- **Arcane Assembly Chamber** — hands arcane workbench recipes to an AE2 crafting CPU and runs them automatically, showing what it is crafting on its face
- **Distillation Encoder** — encodes an item's essentia composition into a pattern
- **Knowledge Inscriber + Knowledge Core** — stores recipes on a core, up to 21 per machine
- **Infusion Provider / Infusion Monitor** — feeds `thaumaturge:infusion` and watches the altar
- **Essentia Vibration Chamber** — turns essentia into AE

**Wireless**

- Wireless Connector, Essentia Provider + Wireless Essentia Receiver
- **Golem Wireless Backpack** — put it on a golem and what the golem carries goes straight into the ME network

**Arcane**

- Arcane Crafting Terminal — craft arcane recipes in the terminal, with the network covering the vis you lack
- Focus of the AE Wrench — one tap of the wand links a device to the grid
- Vis Relay Interface — feeds a node's vis into the assembly chamber

**Also**: a complete Thaumonomicon research tree (24 entries) in English and Chinese, and one `Alkusure86 Fumo` 🧸

### Installing

1. Install NeoForge 21.1.250+, Applied Energistics 2 19.2.x, Thaumaturge 0.4.4+ and its dependency GuideME.
2. Drop `thaumicenergistics-*.jar` into `mods/`.

### Building from source

JDK 21 is required:

```bash
./gradlew build          # output lands in build/libs/
```

Dependencies are not resolved from public maven but read from `libs/` (`build.gradle` consumes the folder with `fileTree`):

- **Thaumaturge** — run `gradlew jar` in its source tree and copy the result into `libs/`
- **Applied Energistics 2 19.2.17** — <https://modrinth.com/mod/ae2>
- **GuideME 21.1.x** — <https://modrinth.com/mod/guideme>

Curios, TerraBlender and JEI are needed for dev runs only and are publicly resolvable.

### Credits

- **Nividica** and the community behind the original **Thaumic Energistics**; this CE builds on the 1.21.1 / NeoForge port.
- **High-version textures drawn by @麦淇淋.** 🎨
- Thanks to the Thaumaturge and Applied Energistics 2 teams for their APIs.

### License

Released under the **MIT** license (see `LICENSE`). It derives from **Thaumic Energistics**, published under **LGPL-3.0** by Nividica and contributors; copyright of the upstream portions remains with them, and redistributions should honour that license as well.
