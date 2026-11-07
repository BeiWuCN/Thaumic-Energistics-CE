# Thaumic Energistics: CE 2.7.5.2

Minecraft **26.1.2** / NeoForge **26.1.2.97+** / Java **25**.

## English

### Fixed
- Remove `ConfigCommentI18nMixin`; NeoForge loads `ModConfigSpec$ValueSpec` before mixin preparation, so the mixin aborted startup with `MixinTargetAlreadyLoadedException`.
- Localize config comments from `ValueSpec.getTranslationKey()` on the client tick instead, without mixing into the config class.
- Lower the required NeoForge version to **26.1.2.97** and build against it, matching the current mainstream 26.1.2 pack baseline (Craftoria 2).

## 中文

### 修复
- 移除 `ConfigCommentI18nMixin`：NeoForge 在 Mixin 准备前就加载了 `ModConfigSpec$ValueSpec`，导致启动时抛 `MixinTargetAlreadyLoadedException`。
- 配置注释改为在客户端 tick 中通过 `ValueSpec.getTranslationKey()` 本地化，不再混入配置类。
- NeoForge 最低版本下调到 **26.1.2.97**，并改为按该版本编译，对齐当前主流 26.1.2 整合包（Craftoria 2）的基线。
