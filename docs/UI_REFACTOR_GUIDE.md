# UI 设计系统迁移指南（阶段 0–2 落地）

> 本次重构目标：简洁、美观、一致。策略是"地基优先 + 组件复用 + 旗舰示范"，
> 而非全盘重写 400+ 文件。已完成的部分让一致性可自动扩散，其余页面按本指南增量迁移。

## 一、已交付的设计系统

### 1. 间距 / 圆角 token（`ui/theme/Dimens.kt`）
统一替代散落的硬编码 dp：
```kotlin
import me.rerere.rikkahub.ui.theme.Spacing
import me.rerere.rikkahub.ui.theme.Radius

Modifier.padding(Spacing.lg)          // 16.dp 页面标准间距
Modifier.padding(Spacing.screenHorizontal) // 16.dp 左右安全边距
RoundedCornerShape(Radius.lg)         // 16.dp 卡片圆角
RoundedCornerShape(Radius.xl)         // 24.dp 分组大圆角
```
尺度：`xxs=2 sm=4 md=8 lg=12 xl=16 xxl=24`（注：本仓库 Spacing 语义映射如上，
实际数值见 `Dimens.kt`）+ `screenHorizontal=16`、`cardPadding=16`。

### 2. 缺口组件（`ui/components/ui/RikkaCard.kt`）
- `RikkaCard(...)`：统一内容卡片（surfaceContainerLow 容器色 + 大圆角），
  支持 `filled` 强调态与 `onClick` 整卡点击。
- `SectionTitle(text)`：统一区块小标题（primary 色 + titleSmallEmphasized）。
- 既有可复用组件（勿重复造轮子）：`CardGroup`（分组设置行）、`FormItem`
  （标签+描述+尾部控件）、`Select`、`Switch`、`Tag`。

### 3. 默认主题改为克制轻量的 Minimal（原 Sakura）
`PresetThemes` 顺序已调整，Minimal 居首即默认；配色主色调为更克制的蓝
（`0xFF3B66D6`），其余主题（Sakura/Ocean/Expressive 等）保留可选。

### 4. 排版层级（`ui/theme/Type.kt`）
在 M3 Expressive 默认基础上微调：收紧 label 字距弱化次要层级，舒展 body 行高。

## 二、已应用的统一化
- `CardGroup` 内部圆角/间距已切换到 `Radius`/`Spacing` token —— 所有使用
  `CardGroup` 的页面（设置、助手、文档等）自动获得一致外观。
- `AssistantPage` 顶部结构性间距已改用 `Spacing` token。

## 三、其余旗舰页增量迁移清单（机械、低风险）
逐页把裸 dp 与手写 Surface/Card 替换为 token 与 `RikkaCard`/`CardGroup`：
1. `pages/setting/SettingPage.kt` 及其子页：LazyColumn 侧边距改用
   `Spacing.screenHorizontal`；零散设置行优先并入 `CardGroup`。
2. `pages/assistant/AssistantDetailPage.kt`：表单区用 `RikkaCard` 包裹。
3. `pages/chat/ChatList.kt` + `ConversationList.kt`：列表项密度与选中态用
   `surfaceContainer*` 层级区分，间距统一 `Spacing`。
4. `pages/translator/TranslatorPage.kt`：输入/输出区用 `RikkaCard`，按钮命中区 ≥48dp。
5. `pages/chat/ChatPage.kt`：气泡层级、输入栏、抽屉间距统一 token。

## 四、验证
- `./gradlew :app:compileDebugKotlin` 编译校验
- `./gradlew test` 单测
- `assembleDebug` 装模拟器/平板核对明暗主题与横竖屏

## 五、回滚
每步独立提交；本次改动均位于 `ui/theme/*`、`ui/components/ui/*` 与少量页面，
失败可 `git revert` 单提交，不污染 MNN / 记忆功能。

---

## 六、模型格式 / 来源兼容性适配层（新增）

为支持 Google Play 上的 **Mobile LLM Server** 以及 LM Studio、Ollama、
llama.cpp server、GPT4All 等本地推理服务，新增了统一的本地服务适配层。

### 设计要点（零网络层重复、原有功能 100% 不受影响）
- 上述本地服务**底层模型格式各异**（GGUF / MNN / Safetensors），但**协议完全统一**：
  均对外暴露 OpenAI 兼容的 Chat Completions HTTP 接口。
- 新增 `ProviderSetting.LocalServer` 类型（`ai/.../ProviderSetting.kt`），
  字段与 `OpenAI` 完全一致，**仅预设本地友好默认值**：回环地址
  `http://127.0.0.1:8080/v1`、无需鉴权、关闭 Responses API。
- 新增 `LocalServerProvider`（`ai/.../providers/LocalServerProvider.kt`）：
  **不重写任何请求/解析逻辑**，而是把 `LocalServer` 原位投影为等价 `OpenAI`，
  直接委托给久经考验的 `OpenAIProvider`。这就是"不同格式之间的转换逻辑"——
  无论本地服务基于哪种格式，只要说出 OpenAI 兼容协议即可无缝复用现有的
  流式生成、工具调用、嵌入、模型列举与图像生成等全部能力。
- **未引入任何新依赖**：完全复用现有 OkHttp + OpenAI 兼容客户端。

### 接入点（已补齐全部穷举分支，编译安全）
- `ProviderManager`：注册 `localServer` 并在 `getProviderByType` 路由。
- `ProviderConfigure`：分段切换按钮新增 "Local Server"；新增
  `ProviderConfigureLocalServer` 配置页（密钥可选提示、本地地址默认提示）。
- `ProviderSetting.Types`：加入 `LocalServer`，首次安装默认内置一个禁用状态
  的 "Local LLM Server"（`DefaultProviders.kt`），启用即连。
- `PreferencesStore` / `ChatboxImporter` / `CherryStudioProviderImporter`：
  穷举序列化分支已补全，导入/导出不遗漏。

### 使用方式
设置 → 提供商 → 新增 → 选择 **Local Server** → 填写本地服务地址
（如 Mobile LLM Server 的 `http://<设备IP>:8080/v1`）→ 在模型管理中
「从服务端拉取」即可自动发现该服务暴露的模型。

## 七、已继续迁移的旗舰页（本轮）
- `pages/chat/ConversationList.kt`：列表间距、空状态卡片圆角/间距、日期/置顶
  头部与列表项内边距统一改用 `Spacing`/`Radius` token。
- `pages/translator/TranslatorPage.kt`：主列间距、进度条/结果/按钮内边距统一
  改用 `Spacing` token。
- 上述页面原本已采用语义色（`surfaceContainerLow`/`primary`），本次仅收敛
  散落 dp，零布局偏移、零硬编码色值引入。
