# RikkaLLM

RikkaLLM 是一款基于 Android 的本地大语言模型（LLM）客户端，支持接入多家主流 AI 服务商与本地推理引擎，提供多模态对话、对话分支、智能助手、联网搜索、工具调用、语音合成与识别等能力。本项目为 **RikkaHub** 的下游分支（应用包名已迁移为 `com.ninef.rikkallm`，Firebase 相关依赖已移除）。

> 仓库根工程名仍为 `rikkahub`，应用显示名为 **RikkaLLM**。

---

## 目录

- [项目简介](#项目简介)
- [主要功能](#主要功能)
- [与上游 RikkaHub 的差异](#与上游-rikkahub-的差异)
- [技术栈](#技术栈)
- [目录结构说明](#目录结构说明)
- [安装与运行](#安装与运行)
- [配置说明](#配置说明)
- [使用示例](#使用示例)
- [贡献指南](#贡献指南)

---

## 项目简介

RikkaLLM 将多种 AI 能力汇聚到一个 Android 应用中：

- 通过统一抽象层接入云端大模型（OpenAI、Google Gemini、Anthropic Claude、DeepSeek、Grok、OpenRouter 等）以及本地推理服务（Ollama、LM Studio）。
- 内置 **MNN** 本地推理引擎（`:mnn` 模块），可在设备端直接运行大语言模型，无需联网。
- 提供消息转换管线（模板、思考标签、正则替换、文档转提示词、OCR 等），在发送前与生成后灵活加工内容。
- 支持对话树结构（消息分支 / 多候选回复）、智能助手隔离配置、联网搜索与可扩展工具集（工作区文件与 Shell 执行）。

---

## 主要功能

| 类别 | 说明 |
| --- | --- |
| **多服务商对话** | 支持 OpenAI、Google Gemini、Anthropic Claude、DeepSeek、Grok、OpenRouter 等，以及任意 OpenAI 兼容端点（vLLM / 自建网关等）。 |
| **本地推理** | 通过 `:mnn` 模块在设备端运行 LLM，支持离线与隐私场景。 |
| **多模态** | 文本、图片、文档（PDF / DOCX / PPTX / EPUB）等多类型内容输入输出。 |
| **对话分支** | 以消息树（`MessageNode`）组织会话，支持重新生成与在多个分支间切换。 |
| **智能助手** | 每个助手拥有独立的系统提示、模型参数、上下文长度、自定义请求头、工具、记忆与提示注入（lorebook）。 |
| **联网搜索** | 集成 Exa、Tavily、智谱、Bing、Brave、SearXNG 等搜索服务商。 |
| **工具与工作区** | `:workspace` 模块提供沙箱化的文件系统和 Shell 执行环境，作为工具暴露给 AI。 |
| **语音能力** | TTS 支持 System、OpenAI、ElevenLabs、FishAudio、Gemini、Groq、MiMo、MiniMax、Qwen、Step、XAI；STT 支持系统识别。 |
| **Markdown 与代码高亮** | `:highlight` 模块提供代码语法高亮，消息支持富文本渲染。 |
| **嵌入式 Web UI** | `:web` 模块内置 Ktor 服务器，托管由 `web-ui/`（React）构建的静态前端。 |
| **内置代码 IDE** | `:app` 的 `ui/pages/ide` 提供轻量编辑器、文件树、多标签、查找 / 替换、命令面板，并支持外接键盘快捷键（`Ctrl+S` / `Ctrl+Z/Y` / `Ctrl+F/H` / `Ctrl+Shift+P` / `Ctrl+Tab` 等）。 |
| **IDE 插件系统** | `data/plugin` 暴露 `IdePlugin` / `PluginManager`：社区插件可注册侧边面板、编辑器 / 文件动作、补全、语言支持、诊断与命令。 |
| **编程模式** | 一键进入与所选工作区绑定的代码编辑器（文件树、多标签、查找 / 替换、命令面板），直接在移动端读写工作区文件。 |
| **对话 / 思维图谱** | `ui/pages/graph` + `data/graph` 提供可视化对话图谱（Thoughtdag），随对话自动同步并持久化。 |
| **模型市场** | `data/huggingface` 支持从 HuggingFace / ModelScope 发现模型并一键选用（`ModelSourceSelector`）。 |
| **生成式 UI** | `ai` 模块的 `GenerativeCard` 将工具结果渲染为结构化 Compose 卡片（对应 AmberAgent `GenerativeWidget`），经清洗防注入。 |
| **更新通道** | 启动后自动检测一次更新；无新版本或网络异常时静默不提示（不弹错误卡片），可在 `SettingUpdatePage` 手动重试与配置渠道；主题采用 M3 Expressive，并内置 ChatGPT 式记忆。 |

---

## 技术栈

- **语言**：Kotlin 2.1.20
- **UI**：Jetpack Compose + Material 3（Android），Compose Multiplatform（WASM，用于 `web-ui`）
- **异步**：Kotlin Coroutines / Flow
- **依赖注入**：Koin
- **本地存储**：Room（数据库）、DataStore（偏好设置）
- **图片加载**：Coil
- **媒体播放**：AndroidX Media3 / ExoPlayer
- **网络与本地服务**：Ktor（`:web` 嵌入式服务器）
- **本地推理**：MNN（alibaba/MNN，固定 commit `1d535d7`）
- **构建**：Gradle（Version Catalog + Convention Plugins，`build-logic/`），Android Gradle Plugin
- **前端构建**：pnpm（用于 `web-ui/`）

### 运行环境要求

- Android SDK：`compileSdk` / `targetSdk` 35，`minSdk` 26
- JDK 17+
- NDK 25.x 与 Android SDK CMake（仅 `:mnn` 本地引擎需要）
- pnpm（用于 `:web` 模块在 `preBuild` 阶段构建 `web-ui/`）

---

## 目录结构说明

```
RikkaLLM/
├── app/                主应用模块：UI、ViewModel、核心业务逻辑、数据库、Provider 配置
│   └── schemas/        Room 数据库版本迁移 schema
├── ai/                 AI SDK 抽象层：统一消息模型（UIMessage）与各服务商接入
│   └── provider/providers/  OpenAI / Google(Gemini) / Claude / LocalServer 等实现
├── common/             通用工具与扩展
├── document/           文档解析（PDF / DOCX / PPTX / EPUB）
├── highlight/          代码语法高亮
├── material3/          Material 颜色工具扩展
├── search/             搜索能力 SDK（Exa / Tavily / 智谱 / Bing / Brave / SearXNG 等）
├── speech/             语音模块：TTS 与 ASR 实现
├── web/                嵌入式 Ktor Web 服务器，托管 web-ui 静态构建产物
├── workspace/          沙箱化工作区：文件系统与 Shell 执行环境（作为 AI 工具）
├── mnn/                本地推理引擎封装（依赖 vendor/MNN 与预编译 libMNN.so）
├── web-ui/             React 前端源码（经 pnpm 构建后由 :web 托管）
├── build-logic/        Gradle Convention Plugins
├── scripts/            MNN 本地引擎准备脚本（setup-mnn.ps1 / setup-mnn.sh）
├── vendor/MNN/         MNN 源码树（git 忽略，由脚本拉取固定 commit）
└── docs/               内部设计 / 重构笔记
```

> 第三方子项目（如 `vendor/MNN`、`VSCodroid-main/`）为独立上游工程，其 README / 文档不在本仓库维护范围内。

---

## 与上游 RikkaHub 的差异

RikkaLLM 是 **RikkaHub**（`rikkahub/rikkahub`，原生 Android 多 LLM 提供商客户端）的下游分支。在继承上游多服务商对话、助手隔离、对话分支、消息转换管线、联网搜索、MCP、Proot 工作区、语音、代码高亮、嵌入式 Web UI 等能力的基础上，本分支重点向**本地推理**与**开发者工具链**扩展：

- **本地推理**：新增 `:mnn` 引擎（含 Llama 后端）实现设备端离线推理；配套 HuggingFace / ModelScope 模型市场。
- **内置代码 IDE**：编辑器、文件树、命令面板、外接键盘快捷键，以及可扩展的 IDE 插件系统。
- **对话 / 思维图谱（Thoughtdag）**：可视化的对话结构，自动同步并持久化。
- **生成式 UI**：`GenerativeCard` 结构化渲染工具结果。
- **工程化**：包名迁移至 `com.ninef.rikkallm`、移除 Firebase、Room 迁移至 v26、接入 Convention Plugins、新增更新通道与 M3 Expressive 主题。

完整的新增 / 变更 / 移除清单见仓库根目录的 [`CHANGELOG.md`](./CHANGELOG.md)。

---

## 安装与运行

### 方式一：Android Studio

1. 使用 **Android Studio**（建议最新稳定版）打开本仓库根目录。
2. 连接 Android 设备或启动模拟器（需 API 26+）。
3. 点击 **Run**（或 `Shift + F10`）运行 `:app` 模块。

### 方式二：命令行（Gradle）

```bash
# 构建 Debug APK
./gradlew assembleDebug

# 安装到已连接设备
./gradlew installDebug
```

> Windows 下请使用 `gradlew.bat`。Firebase 已移除，构建无需 `google-services.json`。

### 测试

```bash
./gradlew test                   # 运行所有模块的 JVM 单元测试
./gradlew connectedDebugAndroidTest  # 运行设备 / 模拟器上的仪器化测试
./gradlew lint                   # 运行 Android Lint
```

---

## 配置说明

### MNN 本地引擎（`:mnn` 模块）

`:mnn` 模块硬依赖两个被 `.gitignore` 忽略的产物，fresh clone 后必须先准备，否则 Gradle 配置阶段会直接失败（fail fast）：

- `vendor/MNN`：alibaba/MNN 源码树（固定 commit `1d535d7`，提供头文件与 CMake 工程）
- `mnn-prebuilt/arm64-v8a/libMNN.so`：预编译运行时（链接并打包进 APK）

运行对应脚本即可自动完成浅克隆（固定 commit）与构建（需要 NDK 25.x 与 Android SDK CMake）：

```powershell
# Windows
powershell -File scripts/setup-mnn.ps1
```

```bash
# Linux / macOS（CI 的 daily-build 也使用它）
./scripts/setup-mnn.sh
```

脚本具备幂等性：`vendor/MNN` 已处于固定 commit 时会跳过克隆，仅做校验。

### 服务商与密钥

应用内通过 **设置 → 服务商 / 助手** 添加各 AI 服务商的连接配置（API Key、Base URL、模型名等），无需修改代码。搜索、TTS、STT 等能力同样在设置中按需启用并填入凭据。

### Web UI（`:web` 模块）

`:web` 模块在 `preBuild` 阶段会构建 `web-ui/` 并复制静态资源，需本机可用 `pnpm`：

```bash
# 如需单独构建前端
cd web-ui && pnpm install && pnpm build
```

---

## 使用示例

### 1. 发起一次对话

1. 在 **设置** 中添加服务商（如 OpenAI / Gemini）并填入 API Key。
2. 新建或选择一个**助手**，配置系统提示与模型参数。
3. 在会话界面输入消息，支持附带图片或文档；回复支持流式输出与 Markdown 渲染。

### 2. 使用对话分支

- 对助手某条回复点击“重新生成”，会生成新的候选回复（同一节点的多个分支）。
- 在分支间切换即可对比不同回复，会话以消息树形式持久化。

### 3. 启用本地推理（MNN）

1. 按上文 [MNN 本地引擎](#mnn-本地引擎mnn-模块) 准备依赖。
2. 在设置中添加本地模型（如通过 Ollama / LM Studio 的本地端点，或 `:mnn` 提供的设备端模型）。
3. 选择该本地助手进行完全离线的对话。

### 4. 接入搜索与工具

- 在助手配置中启用**搜索**并选择搜索服务商，对话中即可触发联网检索。
- 启用**工作区工具**后，AI 可通过 `:workspace` 在沙箱内进行文件读写与 Shell 执行。

### 代码片段参考（消息抽象）

应用以平台无关的 `UIMessage`（`ai` 模块）封装消息，包含文本 / 图片 / 文档 / 思考过程 / 工具调用与结果等内容部件，支持流式合并更新：

```kotlin
// ai/src/main/java/me/rerere/ai/ui/Message.kt
data class UIMessage(
    val role: Role,                 // USER / ASSISTANT / SYSTEM / TOOL
    val parts: List<UIMessagePart>, // 多类型内容部件
    val modelId: String? = null,
    val tokenUsage: TokenUsage? = null,
)
```

---

## 贡献指南

欢迎通过 Issue 与 Pull Request 参与贡献。

1. **Fork** 本仓库并基于 `master` 创建特性分支。
2. 遵循仓库 `.editorconfig`：Kotlin / Gradle 脚本 4 空格缩进、行长上限 120；XML / JSON / Markdown 2 空格缩进。Kotlin 类使用 PascalCase，测试类以 `Test` 结尾。
3. 新增逻辑请配套单元测试（`FooTest.kt`）；涉及 UI 的改动建议补充仪器化测试。
4. 保持模块边界清晰：`ai` 仅做服务商抽象，`app` 承载 UI 与业务，能力按 `search` / `speech` / `workspace` 等模块划分。
5. 提交前请运行 `./gradlew test lint`（及 `connectedDebugAndroidTest`）确保通过。
6. 在 PR 中说明改动目的与验证方式；如涉及 `:mnn` 或 `web-ui`，请在描述中注明本地构建前置条件。

更多架构、概念（Assistant / Conversation / UIMessage / MessageNode / Message Transformer）与模块说明，参见仓库根目录的 `AGENTS.md` 与 `docs/` 下的设计笔记。
