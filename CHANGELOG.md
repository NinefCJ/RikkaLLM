# Changelog

本文件记录 **RikkaLLM**（`com.ninef.rikkallm`）相对上游 **RikkaHub**（`rikkahub/rikkahub`，原生 Android 多 LLM 提供商客户端）的主要差异与演进。

> 基线：上游 `rikkahub/rikkahub` 最新主线（云端 API 聚合客户端，含 OpenAI / Gemini / Claude、Proot 工作区、MCP、网页搜索、技能、语音、图片生成）。
> 本 fork 在保留上游对话 / 助手 / 工作区 / MCP / 搜索等能力的基础上，重点向**本地推理**与**开发者工具链**方向扩展。

---

## Added（相对上游新增）

### 本地推理与模型生态
- **`:mnn` 本地推理引擎**：集成 alibaba/MNN（固定 commit `1d535d7`），在设备端运行大模型，支持离线 / 隐私场景，提供 OpenAI 兼容 API（请求翻译、编排、SSE、Ktor 路由）。
- **Llama 后端增强**：新增 `LlamaEngine` / `LlamaNativeChat` / `LocalLlmBackend`，支持 GGUF 头读取（`GgufHeaderReader`）、图片输入（`LlamaImageInput`）、Chat 模板渲染（`ChatTemplateRenderer`）、模型发现（`ModelDiscovery`）。
- **HuggingFace / ModelScope 模型市场**：`data/huggingface` 新增 `ModelSourceManager`、`HuggingFaceApi`、`ModelScopeApi`、`ModelAutoConfig`、`ModelMarketSource` 等，支持从社区源发现模型并在设置中一键选择（`ModelSourceSelector`）。

### 内置代码 IDE（`:app` · `ui/pages/ide`）
- 轻量代码编辑器（`CodeEditor` / `IdePane`），支持多标签、撤销 / 重做、查找 / 替换。
- 文件树（`FileTree`）、活动栏（`ActivityBar`）、侧边面板（`PanelSheet` / `PluginSidePanelHost`）。
- **命令面板**（`CommandPalette`）：可搜索触发操作，键盘优先。
- **外接键盘快捷键**：`Ctrl+S` 保存、`Ctrl+Z/Y` 撤销重做、`Ctrl+F/H` 查找替换、`Ctrl+N` 新建、`Ctrl+O` 打开、`Ctrl+W` 关闭标签、`Ctrl+Shift+P` 命令面板、`Ctrl+Tab` 切换标签、`Esc` 关闭查找栏。
- 基于 SAF（`DocumentFile`）的会话管理（`EditorSessionManager`）。

### 插件系统（IDE 扩展）
- `data/plugin` 提供 `IdePlugin` / `PluginManager` 与 `PluginContext`：社区插件可注册侧边面板、编辑器 / 文件动作、补全、语言支持、诊断、命令执行（`extensions/`、`builtin/`）。

### 对话 / 思维图谱（Thoughtdag）
- `ui/pages/graph` + `data/graph`：可视化对话 / 思维图谱，含 `GraphCanvas`、`GraphOrchestrator`、`GraphAutoSync`、`GraphStore` 与 `Room` 持久化（`GraphNodeEntity` / `GraphEdgeEntity` / `GraphDAO`）。

### 生成式 UI
- `ai` 模块新增 `GenerativeCard`（结构化生成式卡片），对应 AmberAgent 的 `GenerativeWidget`，将工具结果（搜索、文件列表、设备状态、代码片段等）渲染为统一 Compose 卡片，并经 `GenerativeUiSanitizer` 清洗防注入。

### 其他
- **更新通道**：`SettingUpdatePage` 支持更新检查与渠道配置。
- **M3 Expressive 主题**与 **ChatGPT 式记忆**。

---

## Changed（变更）

- 应用包名由 `me.rerere.rikkahub` 迁移为 `com.ninef.rikkallm`（部分底层 SDK 仍为 `me.rerere.ai`，保留兼容）。
- Room 数据库 schema 迁移至版本 **26**，全部 schema 文件包名同步更新。
- 构建体系接入 `build-logic/`（Convention Plugins）与 `compose_compiler_config.conf`。
- `ai` 模块消息模型扩展：`Message.kt` / `UIMessagePart.kt` 增强（新增生成式卡片等内容部件）。
- 仓库根工程名仍为 `rikkahub`，应用显示名改为 **RikkaLLM**。

---

## Removed（移除）

- 移除 **Firebase** 相关依赖与 `google-services.json`，构建不再需要 Firebase 配置。
- 取消内置 Linux 终端面板计划（保留 `:workspace` 的 Proot 工作区能力，作为 AI 工具暴露）。

---

## 上游保留能力（本 fork 继承 / 扩展）

多服务商对话、助手隔离配置、对话分支（消息树）、消息转换管线、联网搜索、MCP 工具、Proot 工作区、语音（TTS/STT）、Markdown 与代码高亮、嵌入式 Web UI 等。
