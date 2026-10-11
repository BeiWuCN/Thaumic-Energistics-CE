# Thaumaturge 1.0.3 适配检查

检查日期：2026-10-10。仅处理 26.1.2。

## 实施与验证

后续修复：源质存入失败时改为提取已存入部分；撤回不完整时，返回的容器只保留未存入的源质，堆叠容器按剩余内容拆分。终端合并已有水晶和法杖费用后重新分配，支持混合支付；卸下傀儡背包时立即发送无背包状态并清理同步缓存。新增转移和支付检查通过。背包视觉清除仍需客户端观察确认。

已将依赖更新到下述 1.0.3 提交，旧 1.0.2 JAR 保存在 `libs/backup`。节点容量、装备折扣调用已更新；傀儡任务查询通过 TcGolem 封装；旧分页 Mixin 已移除。依赖元数据最低版本改为 1.0.3。

奥术与坩埚材料转换共用 IngredientStacks，保留组件并验证材料匹配，无法解析的组件材料不会退化为错误的裸物品。Windows 获取脚本已改用新版的两项 datagen 任务。

新上游分别执行客户端、服务端 datagen 后重新打包；JAR 包含 37 个要素数据文件。TECE 编译及隔离检查通过。独立服务器成功加载新依赖，新增材料和事务检查通过，83 个坩埚催化剂转换后仍匹配原配方；现有检查中 310 个奥术配方、310 份 JEI 模板、310 份存储样板全部读回成功，28 个研究条目通过。

运行中另有 Jade 收集剪切方块时报 `Registry minecraft:loot_table not found`，以及本地旧自测的编码器掉落检查失败（未观察到纸张掉落）。这两项未在此次修改中解决。客户端研究页排版、真实终端支付和傀儡背包仍需交互验证。

## 比较范围

- TECE 当前固定提交：`de63133232483fce39a8e9fc1499f8f4e7e82379`，对应 Thaumaturge 1.0.2。
- 此次拉取 main：`3c4649c35ebedf2e09a42f04f06da6ecddce123e`，对应 Thaumaturge 1.0.3。
- 上游源码保存在 `F:\Deepseek Harness\04-移植与参考\Thaumaturge-upstream-audit`，独立克隆，不覆盖已有带修改的参考仓库。
- 两提交之间有 28 个提交，1165 个文件变化。TECE 显式导入 83 个上游源码类型，其中 24 个文件发生变化。
- 方法名扫描包含同名方法误报，以下只列核对过的实际调用。尚未用新上游 JAR 编译或启动游戏，不作为完整兼容认证。

## 确认需要修改

| 项目 | TECE 调用位置 | 上游变化 | 处理方式 |
| --- | --- | --- | --- |
| 节点容量 | `compat/thaumaturge/TcAura.java:84`、`:131` | `BlockEntityNode.getAspectsBase()` 移除；`capacity()` 返回基础容量列表 | 两处调用改为 `capacity()`；检查节点筛选和诊断显示 |
| 装备折扣 | `compat/thaumaturge/TcArcanePayment.java:39` | `CasterManager.getTotalVisDiscount(player)` 改为 `visDiscountOf(player)` | 更新兼容层调用。虽然当前晶体灵气计价辅助方法未接入主流程，仍会阻止编译 |
| 傀儡任务 | `golem/GolemBackpackTickHandler.java:85` | `EntityThaumaturgeGolem.getTask()` 改为 `activeJob()` | 更新任务判断，最好通过新的 TcGolem 封装；保证工作中的傀儡不会把任务物品交给 ME 网络 |
| 研究页面 Mixin | `mixin/PageLineStyleMixin.java:19` | `PageParser$Paginator`、`feed(String)` 和 `appendLine(String, Style)` 已移除，分页由 `PageParser$Layout` 的 `flushParagraph` / `placeLine` 实现 | 重新评估原样式修复是否仍需要；若需要，按新分页实现重写注入，不宜只改类名。配置 required=true、defaultRequire=1，必须做客户端启动检查 |

前三项涉及 4 处旧方法调用。第四项属于运行期注入断点，普通 Java 编译不能验证它。

## 需要回归验证

1. **带要素组件的水晶配方。** 上游 `TTRecipeProvider` 引入按组件匹配的水晶材料。TECE 的坩埚输入转换仍通过 `Ingredient.items()` 枚举裸物品：核对组件材料的枚举行为，避免把不同要素的水晶编码成同一种裸物品。检查奥术和注魔配方的 JEI 转移、样板编码、网络备料与实际合成。
2. **节点供给和事务回滚。** 上游将 centivis 状态拆到 `NodeCentivis`，新增充能节点干扰行为调整。`NodeVisRelaySource`、`VisRelayNetwork` 在此区间没有源码差异，但应验证直接节点、中继链、供给不足和事务取消后储量恢复。
3. **法杖和终端支付。** `ItemWand` 与 `WandVisHelper` 重构并增加分要素成本支持。`WorkbenchPayment` 改用 `payExact` / `costFactor`；TECE 导入的费用事件和计划接口在此次区间未变化。测试晶体优先、法杖补差、装备折扣、手持终端以及材料不足时不扣费。
4. **傀儡背包。** 测试执行任务时不存入 ME、空闲时存入、返回岗位和重新分配任务后的行为。
5. **研究页面显示。** 测试粗体跨行、格式重置、段落、图片和翻页，决定移除还是迁移旧 Mixin。

## 更新顺序

先处理上述接口与 Mixin，再独立构建上游固定提交（含 runData），使用新 JAR 验证 TECE 编译和客户端启动，最后完成回归并同步更新 `gradle.properties` 与 `tools/build-thaumaturge.ps1` 中的提交固定值。保留旧依赖用于比较。

初次审查未替换依赖；本次实施已在独立上游克隆中完成构建、替换依赖并保留旧 JAR。

## 2026-10-10 晚间上游更新

本地上游克隆已快进到 `e7544869d1a5117a9d8e709d61ebfad45d9ad6e5`（北京时间 22:21），比此前固定的 `3c4649c3` 新增两次提交：

- `f208d0fc`：重写腐化传播、菌丝、外壳、喷泉，以及咒波黏液与气体的接触行为。
- `e7544869`：重写腐化种子、爬行者和腐化瓶，新增种子伏击及掉落。

共改变18个源码文件。研究渲染、奥术配方、工作台支付和傀儡接口没有源码变化，TECE 也没有直接引用本次改动的腐化类，因此无需新增 API 适配。实体掉落发生变化，已重新执行服务端和客户端 datagen，再单独构建 jar。

研究格式重置补丁 `tools/patches/0001-research-text-reset.patch` 仍适用，已重新应用；上游尚未包含这项修复。项目固定提交与本地依赖已同步更新，之前带格式补丁的依赖保存在 `libs/backup/thaumaturge-26.1.2-NeoForge-1.0.3-3c4649c3-text-reset.jar`。

生成数据时不要将 `runServerData runClientData` 放在同一次 Gradle 调用中：上游开启并行执行，两次生成共用输出目录，空的服务端生成任务可能清理客户端已写入的注册数据。本次初次合并调用出现缺失注册数据，已按现有构建脚本的方式分开执行、最后运行客户端生成，再单独构建 jar。安装前确认 jar 包含37个要素和5个群系。

最终验证日志：`build/upstream-e7544869-check.log`。构建、研究格式回归、隔离服务器启动通过；310个奥术配方解析、28个研究条目、83个坩埚催化剂组件保留、事务和混合支付检查通过。原有 `item-bands` 掉落自检仍失败，因此不能宣称整个自检套件全部通过。腐化传播及实体行为尚未游戏内实测。
