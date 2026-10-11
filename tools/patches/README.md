# 研究文本格式重置

`0001-research-text-reset.patch` 对应 Thaumaturge 1.0.3，提交 `e7544869d1a5117a9d8e709d61ebfad45d9ad6e5`。

研究页面换行后会把上一行的格式作为下一行的初始样式。原来的三参数 `iterateFormatted` 同时把这个样式当作 `§r` 的重置目标，因此重置后仍可能保持粗体、下划线或颜色。

补丁保留换行继承的初始样式，将重置目标单独指定为 `Style.EMPTY`。不需要修改研究 JSON，也不需要恢复旧的分页器 mixin。

构建 Thaumaturge 的脚本会自动应用本目录中的补丁。此修复在 Thaumaturge 依赖中，运行时也必须使用打过补丁的依赖 jar；只替换 TECE jar 不会修复格式问题。原始依赖保存在本地 `libs/backup`。

运行 `gradlew.bat --offline researchFormattingCheck` 会直接调用依赖 jar 中研究页面渲染器的文本函数，检查换行继承、格式重置和后续标题。升级依赖后应重新运行；渲染器结构变化时检查会失败。
