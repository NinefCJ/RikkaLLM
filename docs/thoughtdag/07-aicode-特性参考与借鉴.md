# 07 · aicode 特性参考与借鉴

> 本文档分析开源项目 **`jieapi/aicode`**（运行在 Android 上的 AI 代码编辑器）的独有功能，并与本仓库 RikkaLLM 的现有能力做精确对照，提炼其对**既有 RikkaLLM×ThoughtDAG 方案（01~06）**的借鉴点。
> 本文为**纯分析补充**，不写代码、不改源码。

---

## 1. aicode 核心定位与目标用户

| 维度 | 说明 |
|---|---|
| 产品形态 | **Android 端 AI 代码编辑器**（editor-first），而非纯对话助手 |
| 核心定位 | 把大语言模型与**本地 Linux 开发环境**深度集成，让 AI 不只是"聊天"，而是能"动手改代码、跑构建、看结果" |
| 目标用户 | 移动端开发者、想在手机上做轻量开发的用户、需要在无桌面环境下做 agentic coding 的人 |

**与 RikkaLLM 的差异**：RikkaLLM 是**对话助手优先**（chat-first），通过 `workspace` 模块具备 agentic 能力但 UX 以聊天为主；aicode 是**编辑器优先**，把"编辑文件→执行→观察"作为一等公民体验。

---

## 2. aicode 主要功能模块与特色

| 模块 | 特色 | 对 RikkaLLM 的参照意义 |
|---|---|---|
| **代码编辑器** | Android 内嵌代码编辑 UI，直接读写/修改源码文件 | RikkaLLM 仅有基础文件编辑器（`ide/IdePage`、`CodeEditor`），非完整 IDE |
| **内置 Alpine Linux 容器** | 在 Android 内跑完整 Linux 环境，AI 可直接操作文件系统、装包、构建 | RikkaLLM 用 **PRoot + Rootfs（Termux 移植）** 实现等价隔离（见 §3 对照） |
| **终端模拟器** | 内嵌终端，AI 与用户都能执行 Shell 命令、跑构建工具 | RikkaLLM 已有交互式终端（`WorkspaceTerminalPage` + `TerminalView`/`TerminalSession`） |
| **远程 SSH 执行后端** | 除本地容器外，可把执行环境切到远程 SSH 服务器 | RikkaLLM **缺失**此能力 |
| **AI Agent 执行闭环** | AI 读写文件、执行命令、运行构建并观察结果（agentic coding loop） | RikkaLLM 已有 `workspace_shell` 工具的 Agent 循环（见 §3） |
| **MCP 协议** | 支持 Model Context Protocol，接入工具/集成生态 | RikkaLLM 已有完整 **MCP 客户端**（官方 Kotlin SDK），无 MCP server |

---

## 3. 与 RikkaLLM 能力对照（基于代码核实）

> 以下事实均经代码勘探核实，路径可溯源。

| 能力 | aicode | RikkaLLM 现状 | 结论 |
|---|---|---|---|
| 隔离 Linux 环境 | Alpine 容器 | **PRoot + Rootfs**（`libproot_exec.so` + `libproot_loader.so`，Termux 移植；`WorkspaceTerminalSession.kt:35-86` 启动 `/bin/bash` 并 bind mount `/workspace`、`/skills`） | **能力等价**，实现路径不同（PRoot 非容器） |
| 交互式终端 | 有 | **有**（`WorkspaceTerminalPage.kt` 用 `TerminalView`/`TerminalSession` + `termux_pty.cpp`） | RikkaLLM 已具备 |
| Agent 自动 Shell | 有 | **有**（`workspace_shell` 工具 → `WorkspaceManager.executeCommand` → `HostShellRunner`，见 `WorkspaceTools.kt:209,257`） | RikkaLLM 已具备 |
| 代码编辑器 | 完整编辑器型 UI | **基础文件编辑器**（`ide/IdePage.kt`、`CodeEditor.kt`、`WorkspaceFileEditor`） | RikkaLLM 较弱 |
| 远程 SSH 后端 | 有 | **无** | RikkaLLM 缺失 |
| MCP | 协议支持 | **完整 MCP 客户端**（官方 Kotlin SDK，`io.modelcontextprotocol.kotlin.sdk`；SSE + Streamable HTTP + OAuth 2.1，见 `McpManager.kt`/`McpConfig.kt`）；**无 MCP server** | RikkaLLM 已具备（客户端侧） |
| 本地推理 | — | **`:mnn`**（`libMNN.so`，OpenAI 兼容本地 server，`LocalMnnManager.kt`）；**只推理不执行代码** | RikkaLLM 独有优势 |
| Web 服务 | — | **Ktor + JWT**（`WebApiModule.kt`：对话/文件/设置/静态资源/SSE 事件；**无**终端/代码执行端点） | 不涉及 |
| Agent 循环机制 | 编辑器内驱动 | `GenerationHandler.generateText` 的 `for (stepIndex in 0 until maxSteps)` 多步工具循环（`GenerationHandler.kt:80,290-369`） | RikkaLLM 已具备内核 |

**关键结论**：RikkaLLM 在"agentic 执行"底层能力上**已经高度覆盖 aicode**（隔离 Linux、终端、Agent Shell、MCP 客户端、本地推理）。两者本质差距不在"能不能做"，而在**UX 重心**（aicode 编辑器优先、把 coding 闭环做透）与**两个缺口**（远程 SSH、完整 IDE 编辑器）。

---

## 4. 对 RikkaLLM×ThoughtDAG 方案的借鉴点

基于 01~06 的 M1~M6 模块，aicode 启示如下：

### 4.1 节点类型扩展（M1 GraphModel）
在 `GraphNodeKind`（现状 `MESSAGE/SOURCE/TOOL/REASONING`）基础上，借鉴 aicode 的"编辑→执行→观察"闭环，新增：
- `EDIT`：文件编辑动作（调用 `IdePage`/`WorkspaceFileEditor` 与 `EditorSessionManager`）。
- `TERMINAL`：Shell 执行（复用 `workspace_shell` / `WorkspaceManager.executeCommand`，`WorkspaceShellRunner.kt`）。
- `SSH`：远程执行后端（对应 aicode 的**缺失项**，可作为 P2 新能力）。
这样 DAG 即可表达"编辑→构建→测试"的 agentic coding 流程，而不仅是聊天分支。

### 4.2 编排器驱动 agentic coding（M3 GraphOrchestrator）
`GenerationHandler` 的 `maxSteps` 工具循环已是现成的 Agent 内核。M3 编排器可把"EDIT/TTERMINAL 节点"作为图节点调度，复用 `toolDef.execute(args)`（`GenerationHandler.kt:290-369`）与 `Tool.execute` lambda，无需新执行引擎——与 aicode 的"读写文件→执行→观察"闭环同构。

### 4.3 MCP 在图节点细粒度绑定（M2/M5）
现状 MCP 是**全局设置**（启用 server 的 ID 集合存于 `Assistant.mcpServers`）。借鉴 aicode 的"工具生态标准化"，可在 `GraphNode` 上加 `mcpBinding` 字段，使**单个图节点**绑定特定 MCP server/工具，从而在 DAG 中表达"此步骤调用某 MCP 工具"，而非会话级笼统开启。

### 4.4 画布增加执行日志/终端面板（M4 GraphCanvas）
aicode 的"终端 + 编辑器"交互启发：在 `GraphCanvas` 中对 `TERMINAL`/`EDIT` 节点增加**内联执行日志面板**（展示 `WorkspaceCommandResult` 的 `exitCode/stdout/stderr/timedOut`，结构见 `Workspace.kt`），使画布不仅是结构图，也是"可观测的 coding 流水线"。

### 4.5 编辑器优先的可选 UX（不与现状冲突）
aicode 证明"移动端 agentic coding"是真实需求。RikkaLLM 可在**不破坏对话主形态**前提下，从"对话 + 可展开 workspace 终端/编辑器"渐进到"对话与编辑器并排"，把 DAG 画布作为两种视图的纽带。

---

## 5. 落地建议（与 M1~M6 衔接）

| 借鉴项 | 归属模块 | 优先级 | 说明 |
|---|---|---|---|
| `EDIT`/`TERMINAL` 节点类型 | M1 | P1 | 复用现有 `workspace`/编辑器，无需新 native |
| `SSH` 节点类型 | M1 + 新执行器 | P2 | 填补 aicode 对照中的唯一"环境缺口" |
| 编排器调度 coding 闭环 | M3 | P1 | 复用 `GenerationHandler` 内核 |
| 节点级 MCP 绑定 | M2/M5 | P2 | 细化现有全局 MCP 配置 |
| 画布执行日志面板 | M4 | P1 | 展示 `WorkspaceCommandResult` |
| 编辑器优先 UX 渐进 | UI | P2 | 不破坏对话主形态 |

**优先级建议**：先落地 P1（节点类型扩展 + 编排调度 + 画布日志），它们**完全复用 RikkaLLM 已有能力**（PRoot/Rootfs、终端、`workspace_shell`、本地推理），零新增 native、零重型库，符合 06 的"轻量化"约束；P2 的 SSH 后端与节点级 MCP 作为后续迭代。

---

## 6. 关键文件索引（对照引用）

| 主题 | 真实路径 |
|---|---|
| 隔离环境/Shell | `workspace/src/main/java/me/rerere/workspace/{WorkspaceManager,WorkspaceShellRunner,Workspace}.kt` |
| 终端原生 | `workspace/src/main/cpp/termux_pty.cpp`、`CMakeLists.txt` |
| 交互式终端 UI | `app/.../ui/pages/extensions/workspace/{WorkspaceTerminalPage,WorkspaceTerminalSession}.kt` |
| 代码编辑器 UI | `app/.../ui/pages/ide/{IdePage,CodeEditor}.kt` |
| Agent 工具循环 | `app/.../data/ai/GenerationHandler.kt:80,290-369`；`app/.../service/ChatService.kt:630-693` |
| workspace 工具 | `app/.../data/ai/tools/WorkspaceTools.kt:209,257`；`LocalTools.kt` |
| Tool 模型 | `ai/src/main/java/me/rerere/ai/ui/UIMessagePart.kt:134` |
| MCP 客户端 | `app/.../data/ai/mcp/{McpManager,McpConfig,McpSessionRegistry}.kt`；`transport/{SseClientTransport,StreamableHttpClientTransport}.kt` |
| MCP 配置 | `app/.../data/datastore/PreferencesStore.kt:588`；`Assistant.kt:41,42` |
| Web 服务 | `app/.../web/WebApiModule.kt`；`web/build.gradle.kts` |
| 本地推理 | `mnn/src/main/java/com/alibaba/mnnllm/android/server/LocalMnnManager.kt`；`mnn/build.gradle.kts` |
