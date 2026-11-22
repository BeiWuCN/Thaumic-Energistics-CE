# Thaumic Energistics CE 2.7.5.5 / Minecraft 26.1.2 / NeoForge

## 中文

- 样板供应器界面复用现有升级面板，修复安装 Applied Flux 时重复注册面板的问题。（#8）
- 奥术合成终端能匹配小于九宫格的有序配方，并从正确的槽位消耗材料。无法支付时仍显示配方开销，并提示合成失败的原因。已绑定中继器的 Vis 变化时，终端会刷新合成结果。
- JEI 会标出奥术配方缺少的材料。神秘能源 CE 配方所需的原始要素水晶碎片从顶部连续排列。普通工作台配方也可一键转入奥术合成终端，合成时无需消耗 Vis。
- 可用只含一种要素的安瓿标记源质存储元件的分区槽，标记时不会消耗安瓿。
- 源质元件工作台的禁用分区槽不会再遮挡工具栏提示。
- 研究笔记的要素与设备功能对应。入门研究的谜题较简单，高级存储与自动化研究的笔记更复杂。
- 标准源质存储元件与 MEGA 源质存储元件装入 ME 驱动器时使用不同的贴图。
- 奥术合成终端会记住每位玩家选择的是水晶优先还是法杖优先。
- 源质存储元件在专用的源质元件工作台配置。工作台采用 AE2 的布局，并可保留分区设置供下一个元件使用。
- 模组名称和创造模式物品栏标签去掉了冒号。奥术合成终端、源质终端、源质输入与输出总线以及源质发信器的名称和界面标题统一使用 ME 前缀。
- 源质存储组件的配方接受任意要素的魔力水晶碎片。在源质元件工作台用安瓿标记时，标记对象是瓶内的源质。
- 在 ME 终端用鼠标拿着魔导透镜时，可按 Thaumaturge 的物品栏扫描规则逐个扫描网络里的物品。已发现的要素显示在物品提示中。
- 奥术合成终端新增两个按钮，可将合成材料和水晶退回 ME 网络或玩家背包。放不下的物品留在原槽位。
- 部分机器、无线终端和水晶生长相关配方使用了与其功能对应的原始要素水晶碎片。
- JEI 可从 ME 网络或玩家背包补入配方所需的水晶碎片。法杖优先时，只补支付预览仍需要的水晶碎片。
- 游戏内指南提供简体中文、繁体中文、英文和俄文，涵盖入门、源质存储、终端、奥术合成、自动化和水晶生长，并演示如何用 ME 网络为注魔供料及接收完成信号。
- 库存和槽位容量允许时，按住 Shift 从 JEI 转移配方，会向奥术合成终端填入多份材料及所需的水晶碎片。
- JEI 可将坩埚配方与产物固定的注魔配方转为处理样板。输入依次为被转化的物品、其他材料，以及各类源质及其用量。
- 闪电科技的水晶催化器和过载处理工厂支持神秘水晶生产链。ExtendedAE 与 ExtendedAE Plus 的对应机器可切出神秘电路板并装配神秘处理器。
- 奥术工作台可消耗 5 Vis，将 2 份神秘水晶粉重新凝成 1 颗神秘水晶。
- 奥术合成终端放大了可用 Vis 读数。灵气不足以完成当前配方时，下方以红字显示「可用vis不足」。终端还会显示当前配方所需的 Vis 和减免比例，悬停费用图标可查看对应要素及消耗量。

- 修复 26.1.2 加载考古掉落修饰器时报告旧版注册格式错误。

## English

- Pattern provider screens reuse an existing upgrade panel, preventing duplicate registration when Applied Flux is installed. (#8)
- Arcane Crafting Terminals match smaller shaped recipes and consume ingredients from the correct grid positions. They retain cost information when payment is unavailable, show why crafting was refused, and refresh the result when linked relay Vis changes.
- JEI highlights missing ingredients for arcane recipes. Required primal Vis Crystals in Thaumic Energistics CE recipes are listed from the top without gaps. The Arcane Crafting Terminal also accepts ordinary crafting recipes from JEI and crafts them without spending Vis.
- A phial containing a single aspect can mark an essentia cell partition slot without consuming the phial.
- Disabled partition slots no longer cover toolbar tooltips in the Essentia Cell Workbench.
- Research notes use aspects that reflect what each device does, with easier introductory puzzles and more involved notes for advanced storage and automation.
- Standard and MEGA essentia cells have distinct textures when installed in an ME Drive.
- The Arcane Crafting Terminal remembers whether each player prefers crystals or wand Vis for payment.
- The Essentia Cell Workbench uses AE2's workbench layout and can retain and copy a partition to the next cell. Essentia cells are configured in this dedicated workbench.
- The mod name and creative tab display without a colon. The Arcane Crafting Terminal, Essentia Terminal, Essentia Import and Export Buses, and Essentia Level Emitter use the ME prefix in their item names and screen titles.
- Essentia storage components accept any Vis Crystal aspect, and their workbench only offers to mark the essentia inside a phial.
- A Thaumometer held on the cursor scans individual stored items in ME terminals using Thaumaturge's inventory scanning rules. Discovered aspects appear inside the item tooltip.
- Two buttons in the Arcane Crafting Terminal return ingredients and crystals to the ME network or the player's inventory. Items that do not fit stay in their slots.
- Selected machine, wireless terminal, and crystal growth recipes use primal Vis Crystals that reflect their functions.
- JEI fills recipe crystal slots from ME storage or the player inventory. In wand-first mode it fills crystals that the payment preview still requires.
- An in-game guide covers getting started, essentia storage, terminals, arcane crafting, automation, and crystal growth in English, Simplified Chinese, Traditional Chinese, and Russian. It includes a worked example of ME-fed infusion and explains the completion signal.
- Shift-transfer in JEI fills the Arcane Crafting Terminal with multiple sets of ingredients and their required crystals when stock and slot capacity allow.
- JEI transfers crucible and fixed-output infusion recipes into processing patterns with the catalyst first, followed by any other items and the required essentia amounts.
- The Crystal Catalyzer and Overload Processing Factory support the Thaumic Crystal production chain. ExtendedAE and ExtendedAE Plus machines can cut Thaumic Circuit Boards and assemble Thaumic Processors.
- The Arcane Workbench reforms two Thaumic Crystal Dust into one Thaumic Crystal for 5 Vis.
- The Arcane Crafting Terminal displays the available Vis more prominently and shows a red warning when there is too little aura to craft. It also shows the required Vis and any discount, and each cost icon has a tooltip with its aspect and amount.
- Removed the legacy registration file that caused a loot modifier loading error on 26.1.2.
