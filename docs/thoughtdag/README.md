# RikkaLLM × ThoughtDAG 架构分析与实现方案（总索引）

> 本目录为 **RikkaLLM**（Android/Kotlin AI 聊天应用）引入 **ThoughtDAG** 理念（"图即上下文"的 DAG 上下文管理）的**分析 + 设计文档**集合。
> 交付物为纯文档（不写代码、不改文件），落地深度为"完整 DAG 上下文图 + 可拖拽可视化画布 + 轻量化部署"。

---

## 文档导航

| # | 文档 | 核心内容 |
|---|---|---|
| 01 | [技术栈分析](01-技术栈分析.md) | 模块/依赖/构建/DI/ORM/导航技术栈，与 ThoughtDAG 差异对比 |
| 02 | [数据流说明](02-数据流说明.md) | 从 UI 输入到 AI 响应的完整链路、关键数据结构与接口 |
| 03 | [可扩展点识别](03-可扩展点识别.md) | 适合插入 DAG/编排/画布的 7 个挂点与侵入度评估 |
| 04 | [新增改造模块清单](04-新增改造模块清单.md) | M1–M6 模块职责与交互方式 |
| 05 | [功能列表](05-功能列表.md) | 按模块功能点 + P0/P1/P2 优先级 + 依赖关系 |
| 06 | [实现方案](06-实现方案.md) | 每模块实现思路、关键步骤、涉及文件与轻量化策略 |
| 07 | [aicode 特性参考与借鉴](07-aicode-特性参考与借鉴.md) | 对照 `jieapi/aicode` 的独有功能，提炼对 RikkaLLM×ThoughtDAG 方案的借鉴点 |

---

## 〇、aicode 借鉴小结（速览）

`jieapi/aicode` 是 **Android 端 AI 代码编辑器**（editor-first），内置 Alpine Linux 容器、终端模拟器、远程 SSH 执行、AI Agent 执行闭环与 MCP 协议。经代码核实，**RikkaLLM 在 agentic 执行底层已高度覆盖 aicode**：隔离 Linux（PRoot+Rootfs）、交互式终端、`workspace_shell` 的 Agent Shell 循环、完整 MCP 客户端（官方 Kotlin SDK）、本地 MNN 推理均**已具备**；唯一实质缺口是**远程 SSH 后端**与"编辑器优先"的 coding UX。

对既有方案的借鉴（详见 [07](07-aicode-特性参考与借鉴.md)）：
- **M1**：`GraphNodeKind` 扩展 `EDIT`/`TERMINAL`/`SSH` 节点，使 DAG 表达"编辑→构建→测试"流程。
- **M3**：编排器复用 `GenerationHandler` 内核调度 coding 闭环。
- **M2/M5**：节点级 MCP 绑定，细化现有全局 MCP 配置。
- **M4**：画布增加执行日志面板（展示 `WorkspaceCommandResult`）。

> 说明：所有借鉴项均**复用 RikkaLLM 已有能力**，不新增 native、不引入重型库，与 06 的"轻量化"约束一致。

---

## 一、核心结论

1. **现状已是 DAG 的退化特例**：`Conversation.currentMessages` 当前按节点线性顺序、各取一个选中版本。ThoughtDAG 的"边即上下文"只需把这一线性映射替换为**拓扑遍历入边得到的祖先序列**。
2. **底座完备**：Compose、Koin、Room、Provider 抽象、Transformer 管线、`ChatMessageBranch` 分支 UI 已覆盖所需 80% 能力，改造集中在**数据模型 + 持久化 + UI 三个挂点**。
3. **轻量化可行**：画布用 Compose `Canvas` 自绘（非 WebView/mermaid），复用既有 Compose/Koin/Room，**不引入重型库**。包体积增量预计 **< 0.5MB**。

---

## 二、设计的 6 个模块（M1–M6）

| 模块 | 职责 | 必改 |
|---|---|---|
| M1 GraphModel | DAG 领域模型 + 拓扑遍历 + 无环校验 | 是 |
| M2 ContextAssembler | 遍历入边装配"将发送的序列" + token 预览/diff | 是 |
| M3 GraphOrchestrator | Branch/Prune/Merge/Inspect 状态机，复用 Tool 内核 | 是 |
| M4 GraphCanvas | 可拖拽/缩放/连线的 Compose 自绘画布 + Inspect 浮层 | 是 |
| M5 GraphStore/DAO | Room 图实体与 DAO（`Flow`/`suspend`/`RawQuery`） | 是 |
| M6 GraphCanvasVM+DI | UI 状态、`GraphModule` 注册、路由入口 | 是 |

ThoughtDAG 四大操作映射：
- **Branch** → 复用 `MessageNode.selectIndex` 切换/新增并行节点
- **Prune** → 移除一条边（不删数据）触发上下文重算（删边显示 `−N tokens`）
- **Merge** → 多节点产出按 last/first/concat 合并
- **Inspect** → 调 `ContextAssembler.preview` 预览，不真实请求

---

## 三、落地优先级与执行顺序

```
P0 闭环：M1 → M5 → M6 → M2（装配可运行）→ M3(Branch/Prune/Inspect) → M4(画布+手势)
P1 增强：Merge、Inspect diff、provenance、Tool 内核复用、画布菜单
P2 锦上添花：导出 Mermaid/PNG、自动多步编排、流式建图缓存
```

依赖关系：`M1 → M5 → M6 → M2 → M3 → M4`。

---

## 四、包体积影响（轻量化约束）

- 当前 `app-arm64-v8a-debug.apk` ≈ 93.92MB（未 R8 优化；release 约 45–55MB）。
- 体积大头：assets（23MB：jieba 字典/mermaid/banner）、web-ui（11.9MB）、native（~13.3MB：libMNN/libsimple）、DEX（~30–40MB）。
- **本功能增量 < 0.5MB**（仅新增 Kotlin/DEX，无 native、无大型资源、无新图形库）。
- 硬约束：画布不用 WebView/mermaid（交互不足）；不引入图形库；默认关闭图谱模式（设置开关），普通用户零负担。

---

## 五、关键文件索引（真实路径）

| 主题 | 路径 |
|---|---|
| 模块声明 | `settings.gradle.kts` |
| DI 模块 | `app/src/main/java/com/ninef/rikkallm/di/*.kt` |
| 对话模型 | `app/src/main/java/com/ninef/rikkallm/data/model/Conversation.kt` |
| 消息模型 | `ai/src/main/java/me/rerere/ai/ui/{Message,UIMessagePart}.kt` |
| Provider | `ai/src/main/java/me/rerere/ai/provider/Provider.kt` |
| Transformer | `app/src/main/java/com/ninef/rikkallm/data/ai/transformers/Transformer.kt` |
| 生成编排 | `app/src/main/java/com/ninef/rikkallm/data/ai/GenerationHandler.kt` |
| DAO | `app/src/main/java/com/ninef/rikkallm/data/db/dao/{ConversationDAO,MessageNodeDAO}.kt` |
| 分支 UI | `app/src/main/java/com/ninef/rikkallm/ui/components/message/ChatMessageBranch.kt` |
| 图渲染 | `app/src/main/java/com/ninef/rikkallm/ui/components/richtext/{Mermaid,DiagramRenderer}.kt` |
| 路由 | `app/src/main/java/com/ninef/rikkallm/Screen.kt` |

---

## 六、后续执行入口

按 [06-实现方案](06-实现方案.md) 第 7 节的"涉及文件/目录汇总"创建/改造对应文件，并遵循第 8 节测试策略（参考现有 `ai/src/test/.../GenerativeCardTest.kt` 测试风格）。建议从 M1 起步，逐层向上构建 P0 闭环。
