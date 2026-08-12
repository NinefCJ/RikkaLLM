# UI 重构方案：简洁 / 美观 / 一致的现代视觉

> 现状诊断（基于代码探查）：
> - 地基已是 **Material 3 Expressive**（`MaterialExpressiveTheme` + `MotionScheme.expressive()`），无需更换技术栈。
> - 色彩已主题化：页面几乎无硬编码色值，"色彩杂乱"非主因。
> - **核心缺口**：① 缺少共享 UI 原语（设置行/卡片/按钮各自手写）；② 无统一间距 / 排版 token；③ 旗舰页未做响应式（平板 / 横屏仍是单栏堆叠）。
>
> 原则：**地基优先、原语复用、旗舰落地、随时回滚**。不盲目改写 400+ 文件，而是用设计 token + 可复用组件让一致性自动扩散。

## 阶段 0 — 设计系统地基（低风险，高杠杆）
- 新增 `ui/theme/Dimens.kt`：`Spacing`（4/8/12/16/24/32 dp）与 `Radius`、`Elevation` 常量，通过 `CompositionLocal`（`LocalSpacing`）注入，页面改用 `Spacing.md` 等语义命名。
- 升级 `ui/theme/Type.kt`：启用 `MaterialExpressiveTheme` 的 emphasized 排版阶梯，定义清晰的标题/正文/标签层级（hierarchy），统一 `label`/`title`/`body` 的用法。
- 在 `ui/theme/presets/MinimalTheme.kt` 基础上打磨一套更克制、轻量的默认配色（降低饱和、提升层级对比），作为新默认主题候选。

## 阶段 1 — 共享组件原语（核心，统一 90% 视觉）
在 `ui/components/ui/` 新增/统一定义（页面迁移到这些组件后自动获得一致性）：
- `RikkaCard`：统一圆角（`Radius.lg`）、容器色（`surfaceContainerLow`）、轻量描边/阴影。
- `SettingsItem`（替代散落的 `ListItemDefaults.colors(...)`）：图标 + 标题 + 副标题 + 尾部控件的统一设置行，支持分组 `SettingsSection`。
- `PrimaryButton` / `TextButton` / `IconButton`：统一交互态（按压、禁用、加载）、命中区域 ≥ 48dp。
- `SectionHeader` / `EmptyState` / `TopBar` 脚手架：统一页眉间距与信息密度。

## 阶段 2 — 旗舰页落地（用户最常访问，先示范新语言）
优先重构高频页面，验证新系统：
1. `pages/chat/ChatPage.kt` + `ChatList.kt` + `ChatDrawer.kt`：气泡层级、输入栏、抽屉间距。
2. `pages/chat/ConversationList.kt`：会话列表密度与选中态。
3. `pages/setting/SettingPage.kt` 及其子页：用 `SettingsItem`/`SettingsSection` 统一。
4. `pages/assistant/AssistantPage.kt` + `AssistantDetailPage.kt`。
5. `pages/translator/TranslatorPage.kt`。

## 阶段 3 — 响应式与信息层级
- 引入 `WindowSizeClass`（`androidx.compose.material3.windowsizeclass`）：
  - 平板 / 横屏：聊天页改为**双栏**（会话列表 + 对话区），设置页改为**两列网格**。
  - 手机：保持单栏，但约束最大内容宽度（如 840dp 居中）避免大屏拉伸。
- 收敛信息层级：弱化次要文字（`onSurfaceVariant` + 更小 `label`）、突出主操作、减少分隔线改用容器色差。

## 阶段 4 — 验证
- `./gradlew :app:compileDebugKotlin` 编译。
- `./gradlew test` 跑单测。
- `assembleDebug` 装模拟器/平板，截图核对明暗主题、横竖屏、各旗舰页。

## 回滚
- 每阶段独立提交；阶段 0–1 形成 `ui-refactor-foundation` 分支，验证后再并入 `master`。
- 失败可 `git revert` 单阶段提交，不污染其他功能（MNN / 记忆）。

## 待确认决策
- **默认主题**：是否将打磨后的轻量/Expressive 风格设为新默认（当前默认是 Sakura）？
- **范围**：先交付阶段 0–2（地基 + 原语 + 旗舰页）作为可预览成果，还是一并推进响应式（阶段 3）？
