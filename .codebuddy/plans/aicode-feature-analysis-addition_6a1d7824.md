---
name: aicode-feature-analysis-addition
overview: 在既有的 RikkaLLM×ThoughtDAG 开发方案（docs/thoughtdag/）基础上，新增一篇分析文档，总结开源项目 jieapi/aicode 的独有功能（Android 端 AI 代码编辑器、内置 Alpine Linux 容器、终端模拟器、SSH 远程执行、AI Agent、MCP 协议），并给出这些能力如何反哺/借鉴到 RikkaLLM 的 DAG 上下文图改造方案中（如新增 EditNode/TerminalNode、加深 workspace + MCP 复用）。纯文档补充，不写代码。
todos:
  - id: verify-rikkallm
    content: 使用 [subagent:code-explorer] 核实 workspace/MCP/Tool/web 现有能力
    status: completed
  - id: write-aicode-doc
    content: 撰写《07-aicode-特性参考与借鉴.md》补充文档
    status: completed
    dependencies:
      - verify-rikkallm
  - id: update-readme
    content: 在 README 总索引新增 aicode 导航与借鉴小结
    status: completed
    dependencies:
      - write-aicode-doc
---

## 需求概述

分析开源项目 `jieapi/aicode`（运行在 Android 上的 AI 代码编辑器，内置 Alpine Linux 容器、终端模拟器、远程 SSH 执行、AI Agent 执行闭环与 MCP 协议），总结其特有功能，并将其作为参考补充进已有的"RikkaLLM×ThoughtDAG 开发方案"（位于 `e:/Code/RikkaLLM/docs/thoughtdag/`，共 7 篇：README + 01~06）。

## 用户已确认的范围

- 本次为**纯分析文档**，不写代码、不改源码。
- 不写成独立的 aicode 写作手册，而是作为对既有 DAG 方案的**补充/参考**。
- 交付物：

1. 新增一篇文档 `e:/Code/RikkaLLM/docs/thoughtdag/07-aicode-特性参考与借鉴.md`；
2. 在 `e:/Code/RikkaLLM/docs/thoughtdag/README.md` 总索引中增加该篇导航与"aicode 借鉴"小结。

## 文档拟覆盖内容

1. aicode 核心定位与目标用户（移动端 agentic coding）。
2. 主要功能模块与特色：Android 端代码编辑器、内置 Alpine Linux 容器、终端模拟器、远程 SSH 执行后端、AI Agent 工具执行闭环、MCP 协议。
3. 与 RikkaLLM 的能力对照（已有 workspace/MCP/Tool/web/mnn / 缺失编辑器与容器与终端 / 可复用执行内核）。
4. 对 RikkaLLM×ThoughtDAG 方案的借鉴点：M1 的 `GraphNodeKind` 扩展 EDIT/TERMINAL/SSH 节点、M3 编排器驱动 agentic coding 闭环、MCP 在图节点细粒度绑定、M4 画布增加执行日志/终端输出面板。
5. 落地建议：与 M1~M6 的衔接与优先级。

## Agent Extensions

### SubAgent

- **code-explorer**
- Purpose: 在撰写对照表前，核实 RikkaLLM 现有 `workspace` 模块（沙箱/Shell 执行）、Assistant 的 `mcpServers` 与本地工具、AI `Tool` 执行机制、`web`（Ktor）模块的真实实现路径与能力边界，确保与 aicode 的对照准确、不臆造。
- Expected outcome: 产出 RikkaLLM 现有 agentic/执行相关能力的精确事实清单（文件路径 + 关键类/接口），供 07 篇文档的对照表与借鉴点引用。