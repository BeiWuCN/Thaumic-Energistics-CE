# TECE 2.7.3.65

## 新增

- 机器槽位的悬停框改成 AE2 风格：青色描边加半透明填充，不再用原版的白色方块
- 奥术合成终端改为先扣六槽里的魔力水晶、不够才掏法杖，此前是法杖优先
- 奥术合成终端的费用条带会画出这次要动用的水晶，颗数与要素并排显示
- 水晶槽改为按住 Shift 才说明这一格收哪种水晶，提示不再常驻挡住旁边的格子
- 奥术装配室的四个装备槽补上空图标
- 十二台机器补上战利品表：用镐子直接挖会掉回机器本体，此前挖掉什么都不剩

## 修复

- 收起傀儡（空手潜行右键）时先把背包、无线链接与贴皮摘下来还给玩家，此前会被一起吞掉
- 终端未绑定时点替身槽改为明确拒收并提示，此前会静默收下，等于把物品吃掉
- 源质手势的两处遗漏：shift 左键取整排被拦下、shift 滚轮没有挡
- 法杖槽此前不检查放进去的是不是法杖
- 概率之箱的给奖提示原本写的是 Thaumaturge 的知识类型名（观测／理论），看不出实际给了什么，改为「原始／复合」
- 终端未绑定的提示此前没有文案，玩家看到的是键名本身

## 改动

- 跟进 Thaumaturge 上游 1.21.1 分线的新版本：法杖焦点整体换到上游新的法术 API，源质容器改用物品能力视图，若干部件的成员随之易名
- 奥术合成终端第二页与源质访问卡第二页的研究文本改写
- 对 Thaumaturge 的依赖下限明确为 1.0.0。更旧的版本没有这批类名，装上去会在类加载时崩，而不是在加载时被拦下

## 其他（内部重构、构建、文档）

- 自建 Thaumaturge 补丁重新推导到新上游。保留索引构建移出服务端线程、索引载荷只编码一次、客户端跳过内容相同的索引三项；放弃分配无关指纹那一项 —— 上游自己重写了指纹并改成分节比对，动它的风险是缓存该失效时不失效，那是拿陈旧索引换一点速度
- 焦点现在是数据包驱动的法术行为，随代码补上法术部件与焦点数据映射两份文件，缺了它们焦点不生效
- 焦点不再自己收费。法杖路径已按法术复杂度统一扣费，再收一次就是双扣
- JEI 钉回 Thaumaturge 编译所用的 19.36。JEI 后来给 `ListElementInfo.createFromElement` 加了一个参数，Thaumaturge 的搜索索引仍按旧的三参签名调用，用更新的 JEI 一打开 JEI 搜索框就 `NoSuchMethodError`

---

# TECE 2.7.3.65

## Additions

- Machine slot highlights now use AE2's style: a cyan outline over a translucent fill instead of the vanilla white block
- Arcane crafting terminals spend the six crystal slots before the wand, where they used to prefer the wand
- The arcane terminal's cost strip draws the crystals a craft will use, next to the aspect icons
- The crystal slots explain which crystal they take only while shift is held, instead of covering the neighbouring slots
- The arcane assembler's four gear slots show their empty icons
- Twelve machines gained loot tables: a pickaxe returns the machine itself, where it used to drop nothing

## Fixes

- Collapsing a golem with an empty hand now hands its backpack, wireless link and skin back first; they used to disappear with it
- Clicking a placeholder slot on a terminal that is not bound now refuses the item and says so, instead of silently swallowing it
- Two gaps in the essentia gestures: shift left-click took the whole row and was blocked, and shift scrolling was not blocked at all
- The wand slot did not check that what went into it was a wand
- The gacha payout line named Thaumaturge's knowledge types (observation and theory), which does not say what was actually granted, so it now says primal and compound
- The unbound terminal warning had no text, so players saw the key itself

## Changes

- Followed the new head of Thaumaturge's 1.21.1 branch: the wand focus moved onto upstream's new spell API, essentia containers are read through an item capability view, and several parts renamed their members
- Rewrote the arcane crafting terminal's and the essentia access card's second research pages
- The dependency floor on Thaumaturge is now 1.0.0. Older builds have none of these class names and would die at class load rather than being refused at startup

## Other (internal refactors, build, documentation)

- Re-derived the vendored Thaumaturge patch onto the new upstream. The build moving off the server thread, encoding the index once, and the client skipping an unchanged index are kept; the allocation-free fingerprint is dropped, because upstream rewrote the fingerprint as a sectioned comparison and touching it risks a cache that does not invalidate, which trades a stale index for a little speed
- The focus is a datapack-driven spell behaviour now, so the spell part and the focus data map come with it; without them the focus does nothing
- The focus no longer charges vis itself. The wand path charges once by spell complexity, so charging again would take it twice
- JEI is pinned back to the 19.36 that Thaumaturge compiles against. JEI later added a parameter to `ListElementInfo.createFromElement` and Thaumaturge's search index still calls the older three-argument form, so a newer JEI throws `NoSuchMethodError` the moment JEI's search box is opened

---

# TECE 2.7.3.64

## 其他（内部重构、构建、文档）

- 跟进了 Thaumaturge 的版本

---

# TECE 2.7.3.64

## Other (internal refactors, build, documentation)

- Caught up with the Thaumaturge version

---

# TECE 2.7.3.63

## 其他（内部重构、构建、文档）

- 规范了代码注释，让它读起来不像是做梦梦到的天书

---

# TECE 2.7.3.63

## Other (internal refactors, build, documentation)

- Brought the comments in line with how a person would write them

---

# TECE 2.x 版本更新（2.7.3.62）

## 其他（内部重构、构建、文档）

- 重构了 API 隔离层，把要素键收进 compat/thaumaturge 的 TcAspects 门面。此前有 5 个文件在隔离层之外直接引用上游的 TCAspects，共 19 处
- 拆分概率之箱的方块实体，把 AE 缓冲与取电拆为 GachaPower、把归属玩家与取脑规则拆为 GachaOwner，方块实体从 435 行降到 342 行
- README 的图标改由仓库根目录的 logo.png 提供

---

# TECE 2.x Release Notes (2.7.3.62)

## Other (internal refactors, build, documentation)

- Reworked the API isolation layer so every aspect key goes through the TcAspects facade in compat/thaumaturge. Five files outside the layer had been naming the upstream TCAspects directly, across 19 references
- Split the gacha box's block entity, moving the AE buffer and its grid charging into GachaPower and the bound player and the brain rules into GachaOwner; the block entity drops from 435 lines to 342
- The README icon now comes from logo.png at the repository root

---

# TECE 2.x 版本更新（2.7.3.61）

## 修复

- 重构了代码结构，使其更适合现代化的环境
- 修复了源质元件工作台没有功能的问题
- 修复了源质元件工作台不能快捷操作的问题
- 修复了在源质元件工作台进行 shift 快速操作会导致崩溃的问题
- 修复了炼金供应器以及炼金无线接收器不能工作的问题
- 修复了奥术装配室内部 6 大基础元素 vis 进度条不能正确绘制的问题
- 修复了蒸馏编码台可能造成刷物品 bug 的问题
- 修复了蒸馏编码台可能吞掉空白样板的问题
- 修复了知识记录仪出现多余槽位的问题
- 修复了炼金供应器和炼金无线接收器的命名 id
- 修复了蕴魔观测器的命名 id
- 修复了炼金无线接收器不消耗能量却能工作的问题
- 修复了法杖核心：AE 扳手不能旋转 AE 方块部件的问题
- 修复了源质存储元件配方错误的问题
- 修复了奥术合成终端有时无法合成物品的问题
- 修复了本模组研究页有大量错误的问题
- 修复无线炼金接收器的未绑定提示会复述一整段绑定步骤的问题，现在只说明「尚未绑定」
- 修复炼金供应器与炼金无线接收器无法服务只实现 IEssentiaTransport 的「索取型」机器（例如注魔机）的问题：向它要什么就从网络取什么并交付，此前贴着这类机器一律拒收且没有任何提示
- 修复炼金供应器的 Jade 信息把同一件事说三遍的问题，现在只显示网格状态与已绑定接收器
- 修复无线接收器的绑定坐标用深灰、与 Jade 底色糊在一起的问题，改为白色
- 修复潜行操作会拦截其他部件的问题：现在只对确实装有奥术合成终端的线缆生效，潜行左击清除配对
- 修复蒸馏编码台的源质来源槽会丢失 JEI 写入名字的问题，同时修复破坏方块会把槽内物品带出的问题（只掉回样板）
- 修复知识记录仪槽位过滤不严的问题：核心槽只收知识核心，旁边二十一个只做镜像的槽位不再接受任何输入
- 修复无线奥术合成终端没有注册进 AE2 的 GridLinkables、导致终端绑定槽直接拒收该物品的问题
- 修复换行研究页面丢失行首样式的问题
- 修复有线与无线奥术合成终端共用一个 JEI 传输注册、导致落地终端丢失传输按钮的问题
- 修复 JEI 传输把标签第一个成员当模板、网络有货也报缺失的问题，改为取存量最多的变体
- 修复奥术合成终端把网络也算一份材料、网格内已有材料仍报 INGREDIENTS_CHANGED 的问题
- 修复两个奥术合成终端标题重复显示物品名的问题，改由 AE2 终端样式提供标题
- 修复奥术合成终端灵气花费行的定位，改由终端样式文档放置，随窗口缩放对齐美术
- 修复灵气花费行画到物品栏上并溢出窗口右缘的问题，改画在合成产物下方的木质条内、芯片右对齐
- 修复源质手势吞掉点击的问题：只有真正触发手势的点击才被消费，其余交回 AE2，罐子与安瓿可正常进网络
- 修复源质访问卡拔出后 ME 接口两行未清空、仍会把本行内容交出去的问题
- 修复 ME 接口带源质访问卡时 AE2 仍把源质反向搬进接口存储行的问题
- 修复访问卡抽出的源质写进接口存储行、网络总量虚增而来源容器不减少的问题
- 修复源质访问卡把存储行当过滤器、导致终端凭空付出源质的问题
- 修复咒波传输接口配对时每周期查询落点两次的问题，改为一次
- 修正开发环境运行：主源集不再重复声明自检目录，避免同一份源码被编译两次、服务文件被复制两份导致服务器启动前报错
- 修复蕴魔观测器在自身节点离线时误报「未检测到注魔祭坛」的问题，缺魔导手册的提示也不再被网络离线掩盖，面板日志标签错位两格一并修正
- 修复源质谐振仓过早判定为满的问题（旧逻辑按已处于的状态自我锁定暂停），改为按剩余空间与半刻用量判断，读档路径同样生效
- 修复中文语言文件中四处「韵魔观测器」错字（方块、Jade 配置、研究名、Jade 标题）
- 修复源质元件工作台的元件槽会吞下一整叠元件、升级卡槽会吞下整叠卡的问题
- 修复在玩家背包里 Shift 点击元件或升级卡时的越界报错（可移动范围少算 9 格，未含快捷栏）
- 修复对终端 Shift 点击时只处理光标下那一个的问题，现在会填满手持的整叠容器
- 修复专用服务器启动时因载荷注册引用客户端界面类而触发的 NoClassDefFoundError
- 补上 ME 要素存储元件的中英文名称（该语言键此前缺失）

## 新增

- 现在蕴魔观测器会在注魔合成结束时输出一次强度为 15 的红石信号
- 加入了无线奥术合成终端
- 加入了灵气连接卡
- 加入了源质访问卡
- 加入了概率之箱与概率之箱聚合器：脑槽绑定玩家、认知源质端口、加速卡、经神秘学知识池结算研究点、Jade 行、注魔配方与研究条目
- 源质存储总线改造为咒波传输接口，按网格 tick 工作，每 20 tick 一个周期，由 AE2 内存卡配对决定抽取端与释放端
- 咒波传输接口抽取端从所在区块取咒波，先付 100 AE、1 auram 与 1 ordo 每点，缓存至多 256 点；释放端每周期向前方 3×3×3 排出 4 点咒波
- 咒波传输接口新增 Jade 状态行：服务端写等待原因，客户端画拒绝红字或工作行
- 新增两条概率行为：抽出的咒波有 10% 概率凝为腐化源质入网络（需驱动器与可接收存储），约 1.5% 概率整批排进控制器所在区块
- 新增咒波传输接口的研究页（复用神秘学自身的 thaumaturge:flux）与对应研究条目
- 新增灵气连接卡，作为 AE2 升级卡装入无线奥术合成终端，所需灵气取自玩家所在区块灵气，网络不再收费
- 落地奥术合成终端部件也支持灵气连接卡，新增一格升级槽
- 无线奥术合成终端支持源质访问卡，罐子与安瓿手势与源质终端一致
- 源质访问卡：装入 ME 接口（方块与线缆部件均需一张）后，配置行标记的要素从相邻容器抽入网络
- 源质元件工作台接入 AE2 升级槽系统：元件本体可安装升级卡（三槽），面板随元件内容刷新

## 改动

- 蕴魔观测器恢复读取槽内的魔导手册；其合成配方改为消耗一台魔导透镜
- 咒波传输接口的七页研究文本改写为直述体，并给该页 +2 扭曲
- 源质存储总线改造为咒波传输接口外壳：菜单、界面、JEI 拖放、配置包、传输视图、能力注册与 ME 存储全部移除，改为继承 P2PTunnelPart 以内存卡配对
- 咒波传输接口合成从奥术工作台改为注魔（ae2:interface 催化剂 + 灵气中继器、2 个盐、ordo 与 vitium 水晶、湮灭核心与两个活塞，88 点灵气，不稳定度 7）
- 研究树调整：灵气中继接口上移到无线源质终端之上，咒波传输接口位于其上；基础能源学图标让给 ME 源质元件外壳
- 名称统一为「灵气」：灵气中继接口、灵气连接卡，以及三处自写研究文本
- 两颗升级卡（源质访问卡、灵气连接卡）改为注魔合成，替换原工作台配方，并补齐三个研究条目
- 研究门槛改为消耗要素：24 个条目补上 note_aspects（3~10 点），观测行按条目计费、理论行按 1.7.10 树保留 1~2 行
- AE 扳手核心改走 AE2 自身的部件操作
- 蕴魔观测器待机功耗定为 64 AE/t（此前是从上游继承的 256，一度降到 32）
- 原 infusion_monitor 整体更名为 occult_monitor（方块、包、类、注册 id、资源、配方、研究），中文名蕴魔观测器
- 炼金供应器与炼金无线接收器现在会从网络向「索取型」机器推送源质
- 炼金供应器的 Jade 显示改为文本叠加条形图（此前是 Jade 的箭头样式进度条），数值直接显示在条上
- 无线接收器的 Jade 第二行改为显示所绑定供应器的坐标（此前是每单位 10 AE 的单价说明，随统一收费后已删除）
- 无线连接的源质传输现在双向收费：以前只有从网络取出付 10 AE/单位，回灌供应器是白送；现统一收取并在源质移动前结算，取用改为逐单位付费、付不起即停
- 模组作者行更新为 BeiWuCN, Nividica, Alkusure86, Community
- 蕴魔观测器的气泡渲染距离限制为两区块，注魔气泡仅在八格内绘制
- 把机器相关子系统（源质谐振仓、蕴魔观测器、知识记录仪、源质供应器）连同各自宿主与包内协作者移入子包，blockentity 由一长串平铺类改为按机器分树的目录结构
- 玩偶的第二套模型改名为它所属方块的名字
- 移除大量手写物品/方块说明与用法提示：仅保留状态与实时数据（源质元件容量、知识核心内容、傀儡背包开销、终端绑定坐标、法杖核心名称与文本）
- 源质元件工作台界面改用 AE2 的升级界面样式，加入「按内容分区」与「清空分区」两个工具栏按钮；未装元件时分区格显示为灰色不可用
- 升级卡提示改由 AE2 的升级卡注册表生成，奥术装配室与源质元件工作台保持一致
- 奥术装配室、蕴魔观测器、源质谐振仓的面板读数改为从服务端同步获取

## 移除

- 移除源质输入总线与输出总线（部件、物品、菜单、界面、模型、贴图、两个配方与两项研究奖励），两项研究条目改指向源质存储总线
- 删除 16 张没有任何模型或界面引用、却带在 jar 里的贴图及其动画元数据
- 删除没有任何文件引用的 logo 图片
- 移除仓库内自测：selftest 源集、SelfTestHook／SelfTestProvider 与全部 THAUMICENERGISTICS_*_SELFTEST 开关，测试移到仓库外

## 其他（内部重构、构建、文档）

- 各机器的槽位注册为物品能力，管道只能取回「破坏方块会掉落的部分」；三种源质总线新增 IEssentiaTransport 能力，供应器连接、缓冲与谐振仓储罐改用 BlockCapabilityCache
- 删除四个没有任何地方加载的界面草稿（总线界面实际加载的都是 AE2 自己的样式）
- 更新了模组的 logo
- 更新了版本号
- 引入 compat/thaumaturge 隔离层：注魔祭坛、法杖、要素索引等上游 API 收敛到门面，同时拆分奥术装配室的巨型类并移除死代码
- 源码树重组：自检移入 selftest，奥术装配室相关移入 blockentity.assembler，客户端界面与按钮归入 client.gui，Jade 与 JEI 的客户端半边移出公共树，注册代码与载荷归入 network 包，删除空的 api 与 research 包
- 菜单载荷统一经 MenuNetwork 收发，菜单不再直接点名载荷，GhostGridSlot 改为接收写入器
- 奥术装配室的合成生命周期、灵气搜寻、提示文案与升级记账拆入独立类
- 蕴魔观测器、知识记录仪、源质元件工作台、供应器、源质谐振仓各自的槽位、网格与缓冲逻辑拆出，方块实体只负责 tick 与对外接口
- 注释与 javadoc 整理：长注释改写为散文体并把规则记入 docs/CONVENTIONS.md，超长注释行与过高 javadoc 收拢到项目审计计数归零，成员 javadoc 只保留带数字、断点或库特性说明的部分
- 依赖改为 maven.modrinth 坐标，Thaumaturge 改由抓取脚本从源码构建（会跑数据生成并校验要素 json 数量），新增 GitHub Actions 工作流并把各 action 升到当前主版本
- CI：Thaumaturge 的检出目录与编译产物移出 Actions 缓存，更正上游许可证（MIT）与 README 说明
- 灵气要素索引的哈希、加载、构建与写盘移出服务端线程，客户端在索引未变时跳过重建（以补丁形式放在 tools/patches）
- 为 Java 编译与 Javadoc 指定 UTF-8 编码
- 自检等待 Thaumaturge 灵气索引就绪后再读取，并优先向服务端请求注册表访问
- 精简 build.gradle、gradle.properties、settings.gradle 与相关类的注释
- 问题追踪地址改指向本仓库
- 工作笔记（注释规范清单、奥术合成终端设计笔记）取消跟踪并加入忽略

---

# TECE 2.x Release Notes (2.7.3.61)

## Fixes

- Reworked the code structure for a more modern environment
- Fixed the essentia cell workbench having no function at all
- Fixed quick operations not working in the essentia cell workbench
- Fixed a crash when shift-clicking in the essentia cell workbench
- Fixed the alchemy provider and the wireless alchemy receiver not working
- Fixed the six primal vis progress bars inside the Arcane Assembler not drawing correctly
- Fixed the distillation encoder being able to duplicate items
- Fixed the distillation encoder being able to swallow a blank pattern
- Fixed the knowledge inscriber showing surplus slots
- Fixed the registered ids of the alchemy provider and the wireless alchemy receiver
- Fixed the registered id of the Occult Monitor
- Fixed the wireless alchemy receiver working without consuming energy
- Fixed the wand focus AE Wrench being unable to rotate AE block parts
- Fixed a wrong essentia cell recipe
- Fixed the arcane crafting terminal sometimes failing to craft
- Fixed the large number of errors in this mod's research pages
- Fixed the wireless alchemy receiver's unbound hint reciting a whole binding procedure; it now just says "not bound yet"
- Fixed the alchemy provider and the wireless alchemy receiver being unable to serve "pull-type" machines that only implement IEssentiaTransport (the infusion machine, for instance): whatever is asked for is now drawn from the network and delivered, where previously standing next to such a machine was refused silently
- Fixed the alchemy provider's Jade information stating the same thing three times; it now shows only the grid state and the bound receivers
- Fixed the wireless receiver's bound coordinates being drawn in dark grey and smearing into Jade's background; they are white now
- Fixed sneak interactions intercepting other parts: they now only apply to cables that actually carry an arcane crafting terminal, and sneak-left-click clears the pairing
- Fixed the distillation encoder's essentia source slot losing the name JEI writes into it, and fixed breaking the block taking the slot's contents with it (only the pattern drops back)
- Fixed the knowledge inscriber's loose slot filtering: the core slot accepts knowledge cores only, and the twenty-one mirror-only slots beside it accept no input at all
- Fixed the wireless arcane crafting terminal not being registered in AE2's GridLinkables, which made the terminal binding slot reject the item outright
- Fixed line-leading style being lost on wrapped research pages
- Fixed the wired and wireless arcane crafting terminals sharing one JEI transfer registration, which lost the transfer button on the placed terminal
- Fixed JEI transfer treating a tag's first member as the template and reporting a shortage even when the network had stock; it now takes the variant with the largest amount
- Fixed the arcane crafting terminal counting the network as one more ingredient and still reporting INGREDIENTS_CHANGED when the grid already held the material
- Fixed both arcane crafting terminals showing the item name twice in the title; the title now comes from AE2's terminal style
- Fixed the placement of the arcane crafting terminal's vis cost row, now laid out by the terminal style document so it tracks the artwork as the window is resized
- Fixed the vis cost row drawing over the inventory and overflowing the window's right edge; it is now drawn inside the wooden bar below the crafting output, right-aligned with the chip
- Fixed essentia gestures swallowing clicks: only a click that actually fires a gesture is consumed, the rest goes back to AE2, and jars and ampoules enter the network normally again
- Fixed the ME interface's two rows not being cleared after the essentia access card was pulled, so the row's contents were still handed out
- Fixed AE2 still moving essentia backwards into the interface's storage row while the essentia access card was fitted
- Fixed essentia drawn by the access card being written into the interface's storage row, inflating the network total while the source container never drained
- Fixed the essentia access card treating the storage row as a filter, which made the terminal pay out essentia out of thin air
- Fixed the flux transfer interface querying its destination twice per cycle while paired; now once
- Fixed the development-environment run: the main source set no longer declares the self-test directory again, so the same sources are not compiled twice and service files are not copied twice, which used to error out before server start
- Fixed the Occult Monitor falsely reporting "no infusion altar detected" while its own node was offline, and the missing-thaumonomicon hint is no longer masked by the network being offline; the panel's log label misalignment by two spaces is fixed too
- Fixed the essentia resonance chamber declaring itself full too early (the old logic locked itself out by reading the state it was already in); it now judges by remaining space and half-tick usage, and the load path behaves the same way
- Fixed four "韵魔观测器" typos in the Chinese language file (block, Jade config, research name, Jade title)
- Fixed the essentia cell workbench's cell slot swallowing a whole stack of cells and its upgrade slot swallowing a whole stack of cards
- Fixed an out-of-bounds error when shift-clicking a cell or upgrade card in the player's inventory (the movable range was nine slots short, excluding the hotbar)
- Fixed shift-clicking a terminal handling only the one stack under the cursor; it now fills a whole held stack of containers
- Fixed a NoClassDefFoundError on dedicated server startup caused by payload registration referencing a client screen class
- Added the missing Chinese and English names for the ME Essentia Storage Component (the language keys were absent)

## Additions

- The Occult Monitor now emits a redstone signal of strength 15 once when an infusion craft finishes
- Added the wireless arcane crafting terminal
- Added the vis link card
- Added the essentia access card
- Added the gacha box and the gacha box aggregator: brain slot binding a player, the cognitio essentia port, speed cards, research points settled through Thaumaturge's knowledge pool, Jade lines, infusion recipes and research entries
- Reworked the essentia storage bus into the flux transfer interface, working on grid ticks with a 20-tick cycle, its extraction and release ends decided by AE2 memory card pairing
- The flux transfer interface's extraction end draws flux from its chunk at 100 AE plus 1 auram and 1 ordo per point, buffering up to 256 points; its release end vents 4 flux per cycle into the 3×3×3 in front of it
- Added a Jade status line for the flux transfer interface: the server writes the waiting reason, the client draws the refusal in red or the working line
- Added two probabilistic behaviours: 10% of the flux drawn condenses into corrupted essentia entering the network (needs a drive and accepting storage), and about 1.5% of it vents the whole batch into the controller's chunk
- Added the flux transfer interface's research page (reusing Thaumaturge's own thaumaturge:flux) and its research entries
- Added the vis link card, fitted into the wireless arcane crafting terminal as an AE2 upgrade card; the vis it needs comes from the chunk the player stands in, and the network is no longer charged
- The placed arcane crafting terminal part supports the vis link card too, with one more upgrade slot
- The wireless arcane crafting terminal supports the essentia access card, with jar and ampoule gestures matching the essentia terminal
- Essentia access card: once fitted to an ME interface (one per block and per cable part), the aspects marked in the configuration rows are drawn from adjacent containers into the network
- The essentia cell workbench joined AE2's upgrade slot system: upgrade cards go on the cell itself (three slots) and the panel refreshes with the cell's contents

## Changes

- The Occult Monitor reads the thaumonomicon in its slot again, and its recipe now consumes a thaumometer
- Rewrote the flux transfer interface's seven research pages in plain declarative prose and gave the page +2 warp
- The essentia storage bus was reworked into the flux transfer interface shell: menu, screen, JEI drag-and-drop, configuration payload, transport view, capability registration and ME storage all removed, now extending P2PTunnelPart for memory card pairing
- The flux transfer interface's recipe moved from the arcane workbench to infusion (ae2:interface catalyst plus vis relay, 2 salt, ordo and vitium crystals, annihilation core and two pistons, 88 vis, instability 7)
- Research tree reshuffled: the vis relay interface moved above the wireless essentia terminal, the flux transfer interface above that, and the basic energetics icon was given up to the ME Essentia Component Casing
- Unified naming on "vis": vis relay interface, vis link card, and three hand-written research texts
- Both upgrade cards (essentia access card, vis link card) became infusion recipes, replacing their workbench recipes, with three research entries filled in
- Research gates became aspect costs: 24 entries gained note_aspects (3–10 points), observation rows billed per entry while theory rows keep the 1–2 rows of the 1.7.10 tree
- The AE Wrench focus now goes through AE2's own part operations
- The Occult Monitor's idle draw is now 64 AE/t (it was 256 inherited from upstream, briefly 32)
- Renamed infusion_monitor to occult_monitor throughout (block, package, classes, registered ids, resources, recipes, research), with the Chinese name 蕴魔观测器
- The alchemy provider and the wireless alchemy receiver now push essentia from the network to "pull-type" machines
- The alchemy provider's Jade display became a text overlay bar chart (previously Jade's arrow-style progress bar), with values shown directly on the bar
- The wireless receiver's second Jade line now shows the coordinates of the bound provider (previously a 10 AE/unit price note, dropped along with the unified charge)
- Wireless essentia transfer is now billed in both directions: it used to charge 10 AE per unit only when drawing from the network, while feeding the provider back was free; the charge is now uniform and settled before the essentia moves, and drawing pays per unit and stops when it cannot pay
- The author line was updated to BeiWuCN, Nividica, Alkusure86, Community
- The Occult Monitor's bubble render distance is capped at two chunks, and infusion bubbles are drawn only within eight blocks
- Moved the machine subsystems (essentia resonance chamber, Occult Monitor, knowledge inscriber, essentia provider) together with their hosts and in-package collaborators into sub-packages, turning blockentity from one long run of flat classes into a per-machine directory tree
- Renamed the puppet's second model after the block it belongs to
- Removed a large amount of hand-written item and block descriptions and usage hints, keeping only state and live data (essentia cell capacity, knowledge core contents, golem backpack cost, terminal bound coordinates, wand focus name and text)
- The essentia cell workbench screen switched to AE2's upgrade screen style, with two toolbar buttons ("partition by contents" and "clear partitions"); the partition cells draw greyed out and unusable while no cell is fitted
- Upgrade card tooltips are now generated by AE2's upgrade card registry, keeping the Arcane Assembler and the essentia cell workbench in step
- The Arcane Assembler, Occult Monitor and essentia resonance chamber now take their panel readings synchronised from the server

## Removals

- Removed the essentia import and export buses (parts, items, menus, screens, models, textures, two recipes and two research rewards), and pointed the two research entries at the essentia storage bus instead
- Deleted 16 textures and their animation metadata that no model or screen referenced but that rode along in the jar
- Deleted the logo image that nothing referenced
- Removed the in-repository self-test: the selftest source set, SelfTestHook/SelfTestProvider and every THAUMICENERGISTICS_*_SELFTEST switch, with testing moved out of the repository

## Other (internal refactors, build, documentation)

- Each machine's slots are registered as item capabilities so pipes can only withdraw what breaking the block would drop; the three essentia buses gained an IEssentiaTransport capability, and provider links, buffers and the resonance chamber tank moved to BlockCapabilityCache
- Deleted four screen drafts nothing ever loaded (the bus screens actually load AE2's own styles)
- Updated the mod logo
- Bumped the version number
- Introduced the compat/thaumaturge isolation layer: infusion altars, wands and the aspect index and other upstream APIs were funnelled into a facade, the Arcane Assembler's giant class was split up, and dead code removed
- Source tree reorganisation: self-test moved into selftest, Arcane Assembler pieces into blockentity.assembler, client screens and buttons into client.gui, the client halves of Jade and JEI out of the common tree, registration code and payloads into the network package, and the empty api and research packages deleted
- Menu payloads now all go through MenuNetwork, menus no longer name payloads directly, and GhostGridSlot takes a writer
- The Arcane Assembler's craft lifecycle, vis search, tooltip text and upgrade accounting were split into their own classes
- The slots, grids and buffer logic of the Occult Monitor, knowledge inscriber, essentia cell workbench, provider and resonance chamber were split out, leaving the block entities responsible only for ticking and their outward interfaces
- Comment and javadoc tidying: long comments rewritten as prose with the rules recorded in docs/CONVENTIONS.md, over-long comment lines and over-tall javadoc brought down until the project's audit counts read zero, and member javadoc cut back to the parts carrying a number, a break, or a library quirk
- Dependencies moved to maven.modrinth coordinates, Thaumaturge switched to being built from source by a fetch script (which runs data generation and checks the aspect json count), and a GitHub Actions workflow added with every action bumped to its current major version
- CI: Thaumaturge's checkout directory and build output moved out of the Actions cache, and the upstream licence (MIT) and README notes corrected
- The vis aspect index's hashing, loading, building and writing moved off the server thread, with the client skipping the rebuild when the index is unchanged (shipped as a patch under tools/patches)
- Specified UTF-8 encoding for Java compilation and Javadoc
- Self-test now waits for Thaumaturge's vis index to be ready before reading it, and prefers requesting registry access from the server
- Trimmed the comments in build.gradle, gradle.properties, settings.gradle and the related classes
- The issue tracker URL now points at this repository
- Working notes (the comment convention checklist and the arcane crafting terminal design notes) are untracked and ignored
