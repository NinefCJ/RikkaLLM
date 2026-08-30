# AmberAgent 与 RikkaHub 功能对比分析报告

> 分析日期：2026-08-13
> 分析对象：AmberAgent（`forks/amber/`，本地完整源码）+ RikkaHub（工作区当前 HEAD）
> 方法：基于 AmberAgent 本地源码（`feature/`、`core/`、`data/agent/`、`native/`）、其官方 README、`Agent-Harness 边界审查文档`，以及 RikkaHub 全库 14 维度能力核查结果，逐项比对"AmberAgent 有而 RikkaHub 缺失"的功能模块。

---

## 0. 结论摘要

AmberAgent 是 RikkaHub 的**深度 fork**，其演进方向与当前 RikkaHub（本工作区）已出现明显分化：

- **RikkaHub 当前强项**（AmberAgent 反而弱）：MNN 本地推理 + 模型市场、OpenAI 兼容 Ktor Web 服务 + React Web 控制台、WebDAV/S3 同步、MCP 完整客户端、记忆 RAG + 分级压缩。
- **AmberAgent 独有而 RikkaHub 缺失**（本次报告重点，共 16 项）：

| 层级 | 缺失功能 | 现状 | 优先级 |
|---|---|---|---|
| 核心 Agent 编排 | ① ReAct 主循环 + 工具发现/分发 | 无（仅单次补全编排） | **P0** |
| 核心 Agent 编排 | ② 工具权限审批引擎（风险分级/自动批准/run-trust） | 部分（仅 AskUserTool） | **P0** |
| 核心 Agent 编排 | ③ SubAgent 子智能体编排 | 无 | **P0** |
| 核心 Agent 编排 | ④ 模型议会 ModelCouncil（多模型协作） | 无 | P1 |
| 核心 Agent 编排 | ⑤ 技能系统（Skills/插件式能力） | 无 | P1 |
| 核心 Agent 编排 | ⑥ 外部 CLI 席位（Claude Code/Gemini CLI 等） | 无 | P2 |
| 业务功能 | ⑦ 今日看板 Board（多源信号聚合） | 无 | P1 |
| 业务功能 | ⑧ 深度阅读 DeepRead | 无 | P2 |
| 业务功能 | ⑨ 无人值守自动化（Cron 任务） | 部分（仅 24h 记忆压缩） | P1 |
| 业务功能 | ⑩ 网页挂载 WebMount（站点适配器+OAuth） | 无 | P1 |
| 业务功能 | ⑪ 屏幕自动化（模拟点击/滑动/输入） | 部分（仅 OCR 读取） | P2 |
| 业务功能 | ⑫ 生成式 UI（工具卡片/Live HTML/PPT 预览） | 无 | P1 |
| 业务功能 | ⑬ 小说创作系统 Novel | 无 | P3 |
| 业务功能 | ⑭ 小程序 Miniapp 嵌入 | 无 | P3 |
| 基础设施 | ⑮ Agent Store（Room 持久化运行状态） | 无 | P1 |
| 基础设施 | ⑯ iCloud 同步 / 原生 Rust 模块 | 无 / 部分 | P3 |

---

## 1. 项目背景与关系

- **AmberAgent**：面向手机使用场景的个人 Android Agent 应用，起源于 RikkaHub 的深度 fork（`app.amber.agent` 包名，AGPL v3 + 商业双许可）。核心定位："能看见过程的 Agent 对话"、"SubAgent 分工"、"今日看板与深度阅读"、"手机友好的工具界面"、"可选本地 CLI 席位"。
- 其模块结构经过 M1.5 起的大规模重构：将 RikkaHub 里扁平在 `app/data/agent/*` 的子系统拆分为顶层 `:feature/*`（17 个业务 feature）+ `:core/*`（17 个基础设施 core）+ `:native/*`（Rust 模块）+ `:app` 集成层。
- 与 RikkaHub 的重叠基础：Assistant/Conversation/MessageNode/UIMessage、Transformer 管道、Koin/Compose/Room/DataStore 技术栈。**对话基础架构同源，Agent 能力层完全不同**。

---

## 2. AmberAgent 独有功能逐一分析（缺失项）

### 2.1 核心 Agent 编排层

#### ① ReAct 主循环 + 工具发现/分发机制（P0）

- **作用**：让 LLM 在"思考→调用工具→观察结果→再思考"的循环中自主完成任务。AmberAgent 的 `GenerationHandler` 维护 `for step in 0 until maxSteps` 主循环，配合 `AgentToolDispatcher` 分发工具、`ToolSearch` 做工具发现（resident 常驻 + lazy 懒加载两种暴露模式），工具结果以结构化 JSON 写回对话流。
- **AmberAgent 实现方式**：`core/agent-runtime`（`GenerationHandler`、`AgentToolDispatcher`、`ToolSearch`、`ToolInvocationHooks`、`ToolExposureState`）、`data/agent/tools/ToolRegistry`（集中注册所有工具 + 元数据）、`AgentCronWorker` 等。错误契约成熟：`ToolFailure`（status/message/recoverable）+ `GenerationFailureClassifier`（NETWORK/TIMEOUT/RATE_LIMIT/...）。
- **RikkaHub 现状**：`ChatOrchestrator.kt` 只是单次补全的参数编排；有 `Tool.kt`/`AskUserTool.kt` 等工具定义与审批状态，但**没有主循环、没有工具发现、没有统一的 ToolRegistry 注册表**。
- **集成改动**：
  1. 新增 `data/agent/` 包：`AgentLoop`（ReAct 循环：步数闸、可选挂钟超时）、`ToolRegistry`（汇总现有 `WorkspaceTools`/`McpManager`/`CalendarTool`/`AskUserTool` 等工具，补齐 `name/description/schema/risk/category/mutates` 元数据）。
  2. 新增 `ToolSearch`：resident/lazy 两档暴露，`tools_list` 常驻 + `tool_search` 懒加载，避免工具数过多时提示词爆炸。
  3. `ChatService` 的发送链路在"补全一次"与"ReAct 循环"之间增加模式开关（普通聊天不启用，避免回归）。
  4. 工具结果统一走 `ToolResultPart`，复用现有 streaming 合并。
- **优先级理由**：无循环即无 Agent；这是所有上层能力（SubAgent/看板/Cron/议会）的地基。

#### ② 工具权限审批引擎（P0）

- **作用**：对"agent 能否调用某个工具、是否需要用户批准、能否自动批准、一次批准管多久"做统一决策，是"让 agent 放手干活又不失控"的承重墙。
- **AmberAgent 实现方式**：`PermissionDecisionResolver`（决策树：`hardBlocked` → `mandatoryApproval` → `alwaysAsk` → high-risk → `autoApproveTools && autoApprovable` → ASK/ALLOW/DENY）；`ToolRegistry.risk()` 按名字启发式分级 Normal/Sensitive/High；`allowsAutoApproval`、`needsApproval` 标志；`trustedRunToolNames` 逐工具 run-trust（本轮内免重复询问）；`ToolInvocationContext`（Normal/SubAgent/Cron/ModelCouncil）分场景决策；`SubAgentValidator` 按 5 档 `tool_profile`（NONE/READ_ONLY/WORKSPACE_READ/WEB_READ/HISTORY_READ）收窄子 agent 工具集。
- **RikkaHub 现状**：`Tool.kt` 有审批状态、`AskUserTool.kt` 人工确认，但**无风险分级、无自动批准开关、无 run-trust、无场景区分、无不可绕过层**。
- **集成改动**：
  1. 移植 `PermissionDecisionResolver` + `ToolRisk` 枚举，为现有工具补风险声明。
  2. 设置页新增"工具权限"页：全局自动批准开关（普通/高危两级，高危需二次确认）。
  3. 把 `AskUserTool` 接入决策树（当前是硬编码询问）。
- **优先级理由**：不解决授权边界，任何 agent 编排上线后都会出现"不敢放权"或"放了收不住"两个极端。Harness 审查文档也确认这是该 fork 最成熟、最值得先迁移的模块。

#### ③ SubAgent 子智能体编排（P0）

- **作用**：让主 agent 把任务拆给多个"子 agent"（固定角色 + 动态角色）并行/串行执行，各自汇报进度，结果合回同一段对话。是"一个 agent 解决不了的问题交给一群 agent"的核心编排能力。
- **AmberAgent 实现方式**：`feature/subagent`（`SubAgentManager`：admission 并发上限 `maxConcurrentRuns`、超时 `withTimeout`、输出预算 `outputBudgetChars`；`SubAgentRunner`；`SubAgentValidator`：任务边界/输出格式强校验、动态角色黑白名单与 profile 工具收窄；`SubAgentTools`：`subagent_start/subagent_list/subagent_result` 等工具注入 roster）。子 agent 通过 `tool_allowlist` 限定工具、`tool_profile` 5 档限定权限。
- **RikkaHub 现状**：完全缺失（搜索无 subagent 实现）。
- **集成改动**：
  1. 新增 `SubAgentManager`（并发闸 + 超时 + 预算，直接复用 `coroutineScope`）。
  2. 工具面：注册 `subagent_start/subagent_list/subagent_result` 三件套。
  3. UI：对话内子 agent 运行卡片（进度/结果树），消息模型可复用 `MessagePart` 扩展一种 `AgentProgressPart`。
  4. 权限：先按"只读工具集"白名单起步，后续接入审批引擎。
- **优先级理由**：AmberAgent 官方亮点第 2 条；用户重点维度"智能体管理"的核心。

#### ④ 模型议会 ModelCouncil（P1）

- **作用**：把多个模型（座位）拉进同一议题，串行/并行推理、交叉质询、主持人汇总裁决，做多模型协作决策（"三个臭皮匠"）。含两条路径：legacy 批量路径 + 全功能 Council Room 房间路径（EXPLORE/DEBATE/SYNTHESIZE 三模式状态机）。
- **AmberAgent 实现方式**：`feature/modelcouncil`（`CouncilRoomManager` 114KB 状态机、`CouncilRoomExecutor`、`ModelCouncilManager`、`ExternalCliModelCouncilRunner`、`ProviderModelCouncilTextRunner`、`CouncilRoomPrompts`）+ `data/agent/modelcouncil`。支持运行时可增减/切换/合成参与者、`CouncilHostToolProvider` 只读工具补信息。
- **RikkaHub 现状**：完全缺失（无 council/multi-model/ensemble）。
- **集成改动**：
  1. 新 feature 模块，复用 `ai/` 多 Provider 抽象（现有 OpenAI/Google/Anthropic 三 provider 直接可作座位）。
  2. 先做轻量 `ModelCouncilManager`（多座位串行生成 → 汇总，约 300 行），Council Room 状态机后置。
  3. UI：议会页（座位列表、发言时间线、汇总卡片）。
- **优先级理由**：用户重点"多模态交互/智能体管理"范畴；实现成本适中、产品差异明显。

#### ⑤ 技能系统 Skills（插件式能力）（P1）

- **作用**：把"知识+工具+提示词"打包成可动态加载的技能（skill），agent 通过 `skills_list` 看到已装技能目录、`use_skill` 按需加载执行。本质是手机上的"插件市场 + 运行时"。
- **AmberAgent 实现方式**：`SkillsTools`（`skills_list`/`use_skill`/`tool_search("skill")`）+ `ToolExposureState.isResidentTool`（技能默认懒暴露，避免占提示词）；Harness 审查专门论证了 `skills_list` 必须常驻注入 `<available_skills>` 目录。
- **RikkaHub 现状**：无技能系统；但**已有 MCP 客户端**（`data/ai/mcp/`，McpConfig/McpManager/SSE/StreamableHttp + 设置页），MCP 工具与技能是互补关系。
- **集成改动**：
  1. 定义 skill 包格式（目录 + SKILL.md + tools.json）。
  2. `use_skill` 执行器 = 动态注入提示词 + 挂载该技能声明的工具。
  3. `skills_list` 常驻注入目录。
- **优先级理由**：用户重点"插件系统"；与现有 MCP 形成完整插件矩阵，是 P1 中最贴合用户列举项的功能。

#### ⑥ 外部 CLI 席位（P2）

- **作用**：把 Claude Code、Gemini CLI、Antigravity CLI、Codex CLI、Kimi CLI 等本地 CLI 工具纳入模型议会，让"不需要 API Key 的命令行模型"参与决策。
- **AmberAgent 实现方式**：`ExternalCliModelCouncilRunner` 探测/登录/验证 CLI 可用性后作为 council 座位。
- **RikkaHub 现状**：完全缺失（Codex/Claude 仅出现在中继商广告文案）。
- **集成改动**：需先有 PTY 执行能力（RikkaHub 已有 `termux_pty.cpp`/`ProotShellRunner`，可复用）；探测并解析 CLI 输出；接入 council runner。
- **优先级理由**：依赖 ④ 先落地；且手机场景 CLI 可用性有限，故 P2。

### 2.2 业务功能层

#### ⑦ 今日看板 Board（P1）

- **作用**：每天按时聚合多源信号（应用使用、日历、聊天历史、飞书文档/消息、系统通知、时间锚点、热榜），经评分/去重/静默策略生成"今日待办卡片"，附每日回顾与 DeepRead 入口。把手机上的被动信息变成主动的"今日计划"。
- **AmberAgent 实现方式**：`feature/board`（`BoardRepository` 4 张 DAO、`SignalAggregator` 打分、`BoardSignalCollector`/`AppUsageCollector`/`CalendarSignalCollector`/`NotificationSignalCollector`/`FeishuDocSignalCollector` 等 collector、`BoardAgent` LLM 提炼、`BoardWorker`/`BoardScheduler` 08:00/12:00/18:00 锚点触发、`BoardNotifier` 通知、`BoardTaskRunner` 只读白名单无人值守执行）。含"硬静默阈值 -10""连续忽略 3 次自动静默"策略。
- **RikkaHub 现状**：完全缺失（无 Board/Scheduler/collector；仅 `CalendarTool` 单一工具）。
- **集成改动**：
  1. 新 `BoardRepository` + 信号表（Room）。
  2. collector 起步集：日历 + 聊天未读 + 系统通知（后两者需 NotificationListener 权限）。
  3. `BoardScheduler` 用 WorkManager 固定锚点触发（RikkaHub 已注册 MemoryConsolidationWorker，可同处扩展）。
  4. `BoardAgent` prompt 提炼 + 卡片 UI（今日看板页）。
- **优先级理由**：官方亮点第 3 条；用户重点"任务调度"；为 RikkaHub 增加"主动助理"产品心智。

#### ⑧ 深度阅读 DeepRead（P2）

- **作用**：基于给定材料由 LLM 生成结构化"深度阅读报告/长文"：热点收集→来源抓取→结构规划→分节写作→证据记录。
- **AmberAgent 实现方式**：`DeepReadAgentRunManager`、`DeepReadSourcePrefetcher`、`DeepReadSectionWriterTools`、`DeepReadWorker`、`DeepReadTemplateRenderer`（37KB 模板渲染）+ `feature/deepread`（`DeepReadAgentAdapter` 接入 agent 内核）。
- **RikkaHub 现状**：完全缺失。
- **集成改动**：依赖 ① 主循环（DeepRead 本质是一个特殊 agent）；抓取源可复用 `OkHttpClient` + `document/` 解析；模板渲染器独立实现。
- **优先级理由**：依赖 ① 与 ⑦；先做看板，DeepRead 作为看板卡片的内容增强。

#### ⑨ 无人值守自动化 Cron（P1）

- **作用**：让 agent 按 cron 表达式定时无人值守执行任务（复用对话开关、分场景权限）。与"任务调度"直接相关。
- **AmberAgent 实现方式**：`AgentCronWorker`（WorkManager）+ `ToolInvocationContext.Cron` 场景 + `CoreAgentCronRuntimeSetting`。Harness 审查建议 Cron 档"在 Normal 安全基线之上更克制"：仅自动放行 `autoApprovable && risk==Normal && !mutates` 子集。
- **RikkaHub 现状**：仅 `MemoryConsolidationWorker`（24h 固定周期），无通用 Cron 框架。
- **集成改动**：通用 Cron 调度器（WorkManager 周期性 + 秒级灵活窗口）；Cron 任务 UI（表达式输入、任务列表）；与 ② 审批引擎联动。
- **优先级理由**：用户重点"任务调度"；与看板、记忆压缩共用 Worker 基建。

#### ⑩ 网页挂载 WebMount（站点适配器 + OAuth）（P1）

- **作用**：把 Gmail、飞书文档/消息、GitHub、Reddit 等站点通过 OAuth 登录态"挂载"为 agent 可读写的结构化工具（`feishu_docs_*`、`github_*`、`reddit_*`），浏览器适配器体系。
- **AmberAgent 实现方式**：`data/agent/webmount`（7 个站点适配器 + OAuth 流程）+ `feature/webview`（登录承载）+ 工具暴露（`FeishuDocsTools` 等，写类工具 `needsApproval=true`）。
- **RikkaHub 现状**：完全缺失（仅有 MCP OAuth 回调，非站点适配器）。
- **集成改动**：新增 `webmount/` 适配器框架 + OAuth 管理；首批适配器按需（如 GitHub、Feishu）；复用 `webview/`（RikkaHub 的 web 模块是服务端不是登录 WebView，需新增客户端 WebView 页）。
- **优先级理由**：用户重点"插件系统 + 数据存储与检索"；生态价值高但授权流程重，故 P1 靠后。

#### ⑪ 屏幕自动化（P2）

- **作用**：通过无障碍服务模拟点击/长按/滑动/输入文字/打开应用/按文本查找/滚动，让 agent 能操作任意 App（"数字员工"）。
- **AmberAgent 实现方式**：`ScreenAutomationTools`（click/long_click/swipe/input_text/open_app/tap_text/scroll_until/read_ui/find_text/screenshot/wait_for_text）+ AccessibilityService 后端。
- **RikkaHub 现状**：部分（仅 OCR 读取 `OcrTransformer`），无无障碍服务、无模拟操作。
- **集成改动**：新增 AccessibilityService + 屏幕工具族；权限配置页；高危操作接审批引擎。需在 manifest 声明无障碍服务。
- **优先级理由**：权限/隐私敏感 + 实现量大；但 RikkaHub 已有 OCR 基础，可先做只读类（read_ui/screenshot）再补动作类。

#### ⑫ 生成式 UI（工具卡片/Live HTML/PPT 预览）（P1）

- **作用**：模型可生成结构化卡片、Live HTML 页面、PPT 骨架等"生成式 UI"展示工具结果，并用适合手机的卡片形式渲染（搜索结果卡、文件卡、设备操作卡、浏览器式卡片）。
- **AmberAgent 实现方式**：`GenerativeWidgetSanitizer`（代码层硬拦截 iframe/script/外链 CDN）+ `GuizangHtmlDeckValidator` + `buildGenerativeUiPrompt`（按模型追加绘图细则）+ live HTML 渲染。
- **RikkaHub 现状**：无生成式 UI 渲染器（有 document 预览：PDF/DOCX/PPTX/EPUB 解析）。
- **集成改动**：新增安全白名单渲染器（复用 Compose `WebView`/`AndroidView`）；`GenerativeUiPrompt` 注入；工具结果卡片化。
- **优先级理由**：官方亮点第 4 条"适合手机的工具界面"；产品观感提升最大、改动相对独立，可先行。

#### ⑬ 小说创作系统 Novel（P3）

- **作用**：完整的长篇小说创作系统：文档模型、章节规划、续写（ghostwrite）、润色、分支还原、手动同步、一致性审计，并与 iOS(Swift) 端 JSON 序列化互通。是 AmberAgent 体量最大的 feature（约 61 文件）。
- **AmberAgent 实现方式**：`feature/novel`（`NovelReducer` 状态机、`NovelPromptCatalog` 分版本 prompt、`NovelProjectRepository`、`NovelSwiftCompatibleJson`）+ `NovelWorkspacePage`（182KB 巨型页面）。
- **RikkaHub 现状**：完全缺失。
- **集成改动**：全新 feature；文档模型 + reducer + 专用工作区 UI，工作量大。
- **优先级理由**：垂直小众功能，放 P3（除非用户有小说方向诉求）。

#### ⑭ 小程序 Miniapp 嵌入（P3）

- **作用**：在应用内嵌入小型程序页面（类似小程序容器）。
- **RikkaHub 现状**：完全缺失。
- **集成改动**：需自研容器或嵌入式 WebView 方案。
- **优先级理由**：定位不明、投入产出比低，P3 或砍掉。

### 2.3 基础设施层

#### ⑮ Agent Store（Room 持久化运行状态）（P1）

- **作用**：把 agent 运行状态（runs、sub-agent、council 房间、cron 任务、工具信任记录）持久化到 Room，支持恢复、历史查看与审计。
- **AmberAgent 实现方式**：`core/agent-store-room`（AgentRun/AgentSubRun/AgentCronRun/CouncilRoom 等实体）+ `core/event`（跨模块事件总线）+ `core/context`（上下文预算管理）+ `core/usage`（用量追踪，含 permission trace 序列化）。
- **RikkaHub 现状**：无 Agent 运行状态存储（对话在 Room，但 agent run 状态无）；有 `AppEventBus` 全局事件总线、`StatsPage` 用量统计、记忆 FTS。
- **集成改动**：新增 `AgentRunStore` 等 Room 实体 + DAO；与 ①-④ 的运行时对接；事件总线复用现有 `AppEventBus`。
- **优先级理由**：与 ①-④ 深度耦合，随主循环一并落地；是"能看见过程的 Agent 对话"（官方亮点 1）的持久化基础。

#### ⑯ iCloud 同步 / 原生 Rust 模块（P3）

- **作用**：(a) iCloud 云盘读写（写类工具 `icloud_write` 高危，覆盖真实云盘）；(b) 用 Rust 编译 NDK 模块（markdown-parser、regex-transformer、office-parsers、highlight-parser）提升性能。
- **AmberAgent 实现方式**：`ICloudDriveClient` + `ICloudDriveTools`（OAuth + iCloud Drive API）；`native/` 四个 Rust crate + JNI 桥。
- **RikkaHub 现状**：同步已有 WebDAV/S3（无 iCloud，属平台差异）；native 为 C/C++（MNN、mupdf、proot、quickjs），无 Rust。
- **集成改动**：iCloud 依赖 Apple 生态，Android 侧价值低，建议不迁移；Rust 模块仅在明确性能瓶颈（如 markdown 解析）时引入，成本高、收益不显著。
- **优先级理由**：P3，建议跳过（iCloud 为 iOS 平台特性；Rust 重构性价比低）。

---

## 3. 按用户重点维度的缺口汇总

| 用户重点维度 | AmberAgent 独有能力 | RikkaHub 现状 | 落地优先级 |
|---|---|---|---|
| 智能体管理 | SubAgent、模型议会、Agent 运行时、CLI 席位、Agent Store | 仅单助手+单补全 | P0：主循环+SubAgent；P1：议会 |
| 任务调度 | 今日看板、Cron 无人值守、看板锚点触发 | 仅 24h 记忆压缩 | P1：Cron+看板 |
| 多模态交互 | 屏幕自动化（操作）、生成式 UI 卡片、Live HTML | 仅 OCR 读取+文档解析 | P1：生成式 UI；P2：屏幕自动化 |
| 插件系统 | 技能系统、WebMount 站点适配器 | 有 MCP，无技能 | P1：技能系统；P1：WebMount |
| 数据存储与检索 | Agent 运行状态 Room 存储、记忆双预算 | 记忆 FTS+RAG 已有 | P1：Agent Store |
| 用户权限控制 | 风险分级+审批决策树+run-trust+场景区分 | 仅 AskUserTool 硬询问 | **P0（承重墙，建议最先）** |
| API 接口设计 | （AmberAgent 反而不如 RikkaHub：无 Ktor 服务/Web 控制台） | 有 Ktor+React 控制台 | —（RikkaHub 已领先） |
| 前端界面组件 | 工具卡片、子 agent 进度卡、议会时间线、看板卡片 | 传统聊天 UI | P1：生成式 UI 与卡片化 |
| 日志与监控 | permission trace、usage 追踪、失败分类器 | 有 StatsPage，无 trace | P1：随审批引擎加 trace |

---

## 4. 集成路线图建议

### 阶段一（P0，Agent 化地基，约 2-3 周）
1. **工具权限审批引擎**（②）：先迁移决策树 + 风险分级 + 自动批准开关，把现有 `AskUserTool` 接入。**这是唯一"不先做就会出事"的模块**。
2. **ReAct 主循环 + ToolRegistry**（①）：聚合现有全部工具，加主循环与步数闸。
3. **SubAgent 编排**（③）：三件套工具 + 并发闸/超时/预算 + 对话内进度卡片。
4. **Agent Store**（⑮）：run/sub-run 状态 Room 持久化，随 ①③ 落地。

### 阶段二（P1，差异化产品功能，约 3-4 周）
5. **生成式 UI 工具卡片**（⑫）：独立、见效快，先行。
6. **Cron 无人值守**（⑨）+ **今日看板**（⑦）：共享 WorkManager 基建，一起上。
7. **技能系统**（⑤）：与 MCP 互补，补全插件矩阵。
8. **模型议会**（④）：轻量版先行。

### 阶段三（P1 长尾 / P2）
9. **WebMount**（⑩）：首批 1-2 个适配器试点。
10. **深度阅读**（⑧）：作为看板增强。
11. **屏幕自动化**（⑪）：只读类先行，动作类接审批。

### 明确不建议（P3）
- iCloud 同步（Android 平台错配）
- 原生 Rust 重构（性价比低）
- 小说系统 / 小程序（垂直小众，除非有明确诉求）

---

## 5. 关键参考文件索引

- AmberAgent 本地源码：`forks/amber/feature/`（board、subagent、modelcouncil、novel、terminal、tools、webview、workspace 等 17 个）
- AmberAgent 核心：`forks/amber/core/`（agent-runtime、agent-store-room、automation、context、event、memory、sync、usage）
- AmberAgent 审查文档：`forks/amber/AmberAgent-Harness-边界审查与建议.md`（含 PermissionDecisionResolver/ToolRegistry/GenerationHandler 详细证据行号）
- RikkaHub 现有 Agent 基础：`app/.../data/ai/`（transformers、mcp、tools）、`app/.../data/mnn/`（模型市场）、`workspace/`（沙箱）、`web/`（Ktor 服务）、`app/.../data/sync/`（WebDAV/S3）、`app/.../data/event/AppEventBus.kt`
