---
name: rikkallm-thoughtdag-analysis
overview: 分析 RikkaLLM 项目（Android/Kotlin AI 聊天应用）的技术栈、数据流与扩展点，并参照 ThoughtDAG（本地优先的可编辑 DAG 上下文图）的设计，产出分篇设计文档，规划在现有架构中新增任务编排、节点依赖管理、可视化流程图画布等模块的实现方案（含文件规划），不写实际代码。
---

I now have a comprehensive understanding of the project's data layer, DAO patterns, and UI structure. I have sufficient context to produce the final plan.

This is a documentation/analysis task producing multiple Markdown files. I'll output the plan in the required XML format. Since the deliverable is analysis documentation (not app UI implementation), I'll omit the `<design>` tag per the rules. I'll include the `<extensions>` tag for the code-explorer subagent which is relevant for exploration during documentation writing, and the response_language requires Chinese.

<plan_result>
<req>

## 需求概述

本任务要求对当前 Android 项目（RikkaLLM）进行架构分析，并参考开源项目 ThoughtDAG（本地优先的 AI 上下文管理工具，核心理念"图本身就是上下文"）的设计思路，产出一套"分析报告 + 实现方案设计"的文档交付物。

## 用户已确认的澄清项

- **交付物**：分析报告 + 实现方案设计（不落任何代码、不改动任何文件）
- **落地深度**：完整 DAG 上下文图模型 + 可拖拽的可视化画布 UI，且强调"轻量化部署"
- **报告形式**：按章节拆分为多篇独立 Markdown 分篇文档，并配一份总索引

## 核心功能范围（分析文档需覆盖的 6 个章节）

1. **技术栈分析**：前端（Compose）、后端（Ktor）、数据库（Room+DataStore）、状态管理（ViewModel+Koin+Flow）、构建工具（Gradle+build-logic）等
2. **数据流说明**：从 UI 输入到 AI 响应输出的完整链路，含关键数据结构（Conversation/MessageNode/UIMessage）与接口（Provider、Transformer 管线）
3. **可扩展点识别**：定位适合插入新功能的模块或层次
4. **新增/改造模块清单**：任务编排、节点依赖管理、可视化流程展示等核心能力的职责与交互方式
5. **功能列表**：按模块列出功能点，标注优先级与依赖关系
6. **实现方案**：每个模块的实现思路、关键步骤与涉及的文件/目录

## 参照设计（ThoughtDAG 核心机制）

- DAG 管理对话/上下文，节点=消息/来源片段/推理路径，边即上下文
- 遍历节点入边、排序祖先、构建发送给模型的消息序列
- 四大操作：Branch（分支）、Prune（修剪）、Merge（合并）、Inspect（检查/预览 token）
- 来源溯源（provenance）、上下文差异对比（Context diff，如删除边显示 "-47 tokens"）
</req>

<tech>

## 交付形态

本任务为**纯分析 + 设计文档**交付（不写代码），产出 7 篇 Markdown 文档，存放在 `e:/Code/RikkaLLM/docs/thoughtdag/` 目录下，并在 README 中提供索引。

## 技术方案（基于代码勘探结果）

### 勘探结论（已确认的代码事实）

- **模块结构**：`app`（`com.ninef.rikkallm`，主应用）+ `ai`（`me.rerere.ai`，UIMessage/Provider/Tool 抽象）+ `common`/`document`/`highlight`/`material3`/`search`/`speech`/`web`/`workspace`/`mnn`。依赖方向 `common → ai/speech/search → app`。
- **核心数据模型**：`Conversation` 持有 `messageNodes: List<MessageNode>`（分叉树），`MessageNode` 含 `messages: List<UIMessage>` + `selectIndex` 支持分支；`UIMessage`（ai 模块）为平台无关消息抽象，支持流式 chunk 合并。
- **数据层**：Room `AppDatabase`（version=27）含 `ConversationDAO`（PagingSource + Flow + suspend）、`MessageNodeDAO`、`MemoryDAO` 等；DAO 模式为 `Flow<T>` / `PagingSource` / `suspend` 混合。
- **Transformer 管线**：`app/.../data/ai/transformers/Transformer.kt` 定义 Input/Output transformer（Template/ThinkTag/Regex/Document/Ocr 等），是插入"上下文装配"的最佳挂点。
- **状态管理**：Koin DI + ViewModel + Room Flow + Paging3 + Navigation3（Compose）。
- **UI**：Compose，消息组件位于 `app/.../ui/components/message/`，含 `ChatMessageBranch.kt`（已有分支 UI）、`ChainOfThought.kt`、`DiagramRenderer.kt`、`Mermaid.kt`（已有图渲染基础）。

### 实现方案设计要点（文档中体现）

1. **DAG 上下文图数据模型**：设计 `GraphNode`（映射 UIMessage/来源片段）+ `GraphEdge`（依赖/上下文关系），通过 DAG 遍历（拓扑排序）构建发送给 Provider 的消息序列，替代现有线性 `currentMessages` 逻辑。
2. **上下文装配器**：新增 `ContextAssembler`，在 InputMessageTransformer 管线之前/之后执行，遍历入边、排序祖先、计算 token 预算、输出"将发送给模型的序列"，支持 Inspect 预览与 Context diff（前后 token 差）。
3. **编排执行器**：基于 DAG 的轻量任务编排器（拓扑排序 + 状态机：pending/ready/running/done/pruned），可驱动 Branch/Prune/Merge 操作，不引入重型工作流引擎。
4. **可视化画布**：复用现有 Compose `DiagramRenderer.kt`/`Mermaid.kt` 渲染思路，采用**轻量自绘 Canvas + 自定义手势**（平移/缩放/拖拽/连线）实现节点与边展示，避免引入大型图形库；新增 `GraphCanvas.kt` 组件与画布 ViewModel。
5. **持久化**：新增 Room entity（`GraphNodeEntity`/`GraphEdgeEntity`）+ DAO，遵循现有 `ConversationDAO` 的 `Flow/suspend/PagingSource` 模式，并规划 schema 迁移。
6. **轻量化部署**：复用现有 Koin 模块注册、Room 基础设施与 Compose 主题，不新增重型前端框架；画布以单文件 Compose 组件形式集成进现有聊天气泡侧栏/独立页面。

### 性能与设计原则

- DAG 遍历采用拓扑排序 O(V+E)，节点缓存避免重复遍历；token 计算按需懒计算。
- 遵循 SOLID/DRY：图模型与 UIMessage 之间通过映射器解耦，复用现有 transformer 管线。
- 文档须给出每个模块的具体文件路径、接口契约与交互时序，保证后续实现可直接落地。
</tech>

<extensions>

## 说明

本任务为文档撰写，可选的勘探类子代理用于辅助验证代码事实，确保分析文档内容精确可信。

### SubAgent

- **code-explorer**
- Purpose: 在撰写各分篇文档时，按需深度勘探 RikkaLLM 代码库，核实模块依赖、数据模型字段、DAO 模式、Transformer 管线与 UI 组件路径，避免文档中出现臆造的文件路径或 API
- Expected outcome: 每篇文档中的代码引用与文件路径均与真实仓库一致，保证方案可落地
</extensions>

<todolist>
<item id="01-analysis-project" deps="">撰写《01-技术栈分析.md》：梳理 RikkaLLM 各模块技术栈、依赖方向、构建工具、DI/ORM/导航方案，并与 ThoughtDAG 技术特征做差异对比</item>
<item id="02-analysis-dataflow" deps="01-analysis-project">撰写《02-数据流说明.md》：从 UI 输入到 AI 响应输出的完整链路，覆盖 Conversation/MessageNode/UIMessage、Transformer 管线、Provider 接口与 Room 持久化</item>
<item id="03-analysis-extensions" deps="01-analysis-project,02-analysis-dataflow">撰写《03-可扩展点识别.md》：定位适合插入 DAG 上下文图、任务编排与可视化画布的模块/层次挂点</item>
<item id="04-modules-list" deps="03-analysis-extensions">撰写《04-新增改造模块清单.md》：定义 DAG 核心模型、上下文装配器、编排执行器、可视化画布、持久化模块的职责与交互方式</item>
<item id="05-feature-list" deps="04-modules-list">撰写《05-功能列表.md》：按模块列出功能点，标注优先级 P0/P1/P2 与依赖关系，覆盖 Branch/Prune/Merge/Inspect、来源溯源、上下文预览与 token 差异对比</item>
<item id="06-implementation-plan" deps="05-feature-list">撰写《06-实现方案.md》：给出每模块实现思路、关键步骤、涉及文件/目录与轻量化部署策略（复用 Compose/Koin/Room，画布轻量自绘 Canvas）</item>
<item id="07-readme-index" deps="06-implementation-plan">撰写《README.md》总索引：汇总七篇文档结论、落地优先级、后续执行入口，并附完整目录结构</item>
</todolist>
</plan_result>