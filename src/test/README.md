# 1.0.3 适配检查

运行：

```powershell
.\gradlew.bat --offline runServer -PupgradeVerification '-Pselftests=UPGRADE_SELFTEST,RECIPE_SELFTEST,RESEARCH_SELFTEST'
```

测试使用 `run/upgrade-verification`，不打开日常开发存档。新增检查在服务器启动后执行，验证水晶材料的数据组件、要素过滤，以及 ThETransaction 的预览、提交、取消、嵌套预览和异常回滚。执行后保留 100 tick 供其他检查完成，再自动退出。

TransferAndPaymentChecks 另外检查源质部分存入后的撤回、撤回被拒时的源质守恒、堆叠容器拆分与自定义名称保留，以及水晶加法杖合付、法杖储量不足时拒绝、纯水晶支付和规划阶段不扣费。

OccultMonitorRemovalChecks 使用生存模式玩家和铁镐检查空置及带手册观测器的真实破坏流程，验证方块被移除、观测器与手册各掉落一次；另外检查替换为其他方块时不会被观测器状态更新覆盖，以及重复清理不会重复掉书。通过物品实体生成事件统计掉落，避免新加载区块的实体尚未进入查询索引时得到零数量。

ArcaneQuickMoveChecks 在固定与无线奥术合成终端菜单中调用真实 quickMoveStack，使用可接收物品的网络替身验证六种水晶和法杖先进入工作台槽位、水晶补满与溢出、法杖组件保留、占用槽位不替换、普通物品仍存入网络，以及法杖能够快捷取回。

EssentiaSearchChecks 使用 AE2 的实际 RepoSearch 和资源中的名称，检查37种源质的简体、繁体、英文及拉丁名搜索；未支持语言按英文资源回退，并检查切换语言后同一共享键的显示名更新。名称使用 `thaumicenergistics_ce.essentia_name.<namespace>.<aspect>` 翻译键，资源包可以添加其他语言。中文名称直接显示，如 `火`、`地`；英文及未支持语言的回退名称使用 `Ignis`、`Terra` 等拉丁名，不附加括注。不需要额外的搜索注入。

TerminalVisibilityChecks 验证实际菜单注入：普通终端隐藏源质、两种源质终端显示源质、两种奥术菜单随访问卡插拔改变权限、关闭显示限制后恢复，以及概率配置生效。测试捕获真实广播产生的库存包，验证装卡时重新发送数量未变的源质、取卡时替换清单、空清单清除缓存、条目编号保持稳定，且权限不变时不重复全量同步。测试结束恢复配置。客户端注入加载检查：`gradlew.bat --offline runClient -Pselftests=CLIENT_VISIBILITY_CHECK`，确认注入字段存在并等待初始界面加载后自动退出。

`RECIPE_SELFTEST` 和 `RESEARCH_SELFTEST` 来自现有本地 `_scratch/selftest` 源集；该目录不在版本控制内，缺少时这两项不会运行。新增检查保存在这里，并随仓库维护，不打入发布 JAR。以日志中的 PASS 和各项失败计数确认执行结果，不能只看 Gradle 退出码。

尚需游戏内检查：研究页排版、终端真实合成及支付、傀儡背包在执行任务时的行为。

研究格式回归检查：`gradlew.bat --offline researchFormattingCheck`。它直接调用依赖 jar 中的页面文本渲染函数，验证换行继承的粗体、下划线和颜色能够由 `§r` 清除，后续标题仍能设置样式。原版 1.0.3 会失败，应用 `tools/patches/0001-research-text-reset.patch` 后通过。这项检查也随 `build` 执行，不需要启动游戏；实际字体换行和分页仍需游戏内确认。

## ME 总线源质接入

`UPGRADE_SELFTEST` 同时运行 `EssentiaBusChecks`，检查三个原生总线的第六升级槽、接口拒卡、要素过滤载荷、反相过滤、耗电和容量限制，以及实际源质罐的挂载和取卡停用。异常检查覆盖部分接收、网络拒收、传输及提交回调报错、实际电量不足、外层事务和电价变动，验证源质守恒及退款金额。存档检查覆盖类型选择、余量重载、掉落物与重新放置、内存卡不复制余量。还会运行原生输出循环，验证9点消耗两次操作，并检查总线倍率未改变终端存储耗电。查看 `[essentia-buses] PASS` 及三个 `[essentia-bus-fixes/persistence/budget] PASS`，不能仅凭 Gradle 的成功退出判断专项测试通过。
