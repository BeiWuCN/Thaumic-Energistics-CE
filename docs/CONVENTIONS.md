# TECE 注释与文本约定

> 这份文件是 `PLAN.md` 引用的 `docs\CONVENTIONS.md`。规则以仓库里的脚本为准；本文只解释口径与出处。
> 判据脚本是 `F:\Deepseek Harness\TECE 2.0\build\cmd\comment-audit.ps1`（硬编码审计 `<仓库>\src\main\java`）。
> 改完注释跑一次，要求**规则类全 0**。脚本自身退出码 1 是正常的（它用退出码表示"有违规"）。

## 一、写不写：默认不写

只留三类（2026-10-04 18:00 的 r4 决定，成员 javadoc 的默认值由"有"改成"无"）：

| 类别 | 什么时候写 |
| --- | --- |
| NUMBER | 这个数字从哪来，数与数之间的关系（例如 `AE_PER_VIS = 1_000.0` 是 AE 与 vis 的汇率） |
| BREAK | 改了它、忽略它、或者把它填上会坏什么（例如"不注册这个 capability，机器会自成一个孤立网格"） |
| QUIRK | 外部库的非显然行为，最好点名出处（例如"AE2 只认 `c:tools/wrench` 这个 tag"） |

稀有例外：环境变量 / 系统属性开关（例如 `ThaumicEnergistics` 的自检开关、`thaumicenergistics.aewrench.debug`）。
**不要**给每个常量、每个方法都写注释；代码能自证的重复说明一律删掉。

## 二、怎么写：八行以内，散文

| 规则 | 上限 | 脚本 |
| --- | --- | --- |
| 类/接口/枚举/record 的 javadoc | **8 行**（含 `/**` 与 `*/`） | `JTall` |
| 成员 javadoc 的正文 | **2 行**（`@param`/`@return` 等 `@tag` 行不计） | `JMeth` |
| 连排的 `//` 注释 | **2 行** | `Run` |
| 单行宽度（注释与代码都算） | 110 字符 | `Len`（`Code` 类只报告、不算违规） |
| javadoc 里的裸 ` *` 空行 | 夹在两端散文之间算违规 | `JBlank` |
| javadoc 里的 `<p>` | 违规 | `JP` |
| 同行代码后面跟 `//` | 违规 | `Trail` |

写法：

- **用连贯散文**：`/**` + 标题行 + 2~5 行正文 + `*/`。不要 `<ul>`/`<li>` 清单，不要 `<p>`，也不要行内 HTML
  强调标签（`<em>`、`<b>`、`<code>`、`<strong>`、`<br>`）；要强调就直接写词，类型/方法引用用 `{@code X}` 或
  `{@link X}`（这两个是 javadoc inline tag，保留）。混用 HTML 只会让同一句话有两种风格。
  清单式写法（`/**` + 标题 + `<ul>` + 3 条 `<li>` + `</ul>` + `*/` = 正好 8 行）虽然合规，但读起来是碎句；
  165 处类 javadoc 已统一改写成散文。
- 空间不够时**砍次要的枚举式说明，保留"为什么这样写 / 改了什么会坏 / 外部库的怪癖"**。
- **`JTall` 数的是物理行数**：`/**`、`*/`、结构性的裸 ` *` 都算在内。按"正文行"估算必然少算 2~3 行。
- 一行 110 字符是硬上限，含 8 格缩进时正文实际只有约 102 列可用；写的时候按 100 列更安全。
- 技术标识符（类名、方法名、字段名、数值、包名）原样保留，不要翻译、不要改写。

## 三、文本编码

- 除含中文的 `.ps1`（必须带 BOM，否则 PowerShell 5.1 读乱码）以外，**一律 UTF-8 无 BOM + LF**。
- 用字节比较判断重复文件，不要用 `Get-Content -Raw` 文本比较：PS 5.1 对无 BOM 文件按 gb2312 解码，会报假差异。

## 四、改完注释的闸门

1. `build\cmd\comment-audit.ps1` → 规则类（Len/Trail/Run/JBlank/JP/JTall/JMeth）全 0；`Code` 类只报告，不修。
2. `mc_gradle classes` → BUILD SUCCESSFUL。
3. 纯注释改动要证明零字节码影响：`build\cmd\compare-bytecode-per-class.ps1`（吃 jar，不吃目录）或
   `build\cmd\class-surface-diff.ps1`（公开面变化必须逐条列进 `-Allow` / `-AllowPublic`，两条判据互相独立 —— 删掉 public 字段也算公开面变化）。
4. 行为改动（不只是注释）要另外用 `build\cmd\classify-staged-diff.ps1` 证明 CODE=0，或跑 `server-gate.ps1`。

> 2026-10-06：`src/selftest` 源集、`init/SelfTestHook`、`init/SelfTestProvider` 与全部 `THAUMICENERGISTICS_*_SELFTEST`
> 开关已从仓库删除（含 `compileSelftestJava` 任务与 `runServer` 自检闸门）。闸门只剩审计脚本、编译、字节码/公开面比对，以及游戏内手测。

更细的历史与踩坑记在 `F:\Deepseek Harness\TECE 2.0\协作须知-给另一个Agent.md` 的 §4b 与 §5。
