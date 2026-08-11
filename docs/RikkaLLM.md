# RikkaLLM

RikkaHub × MNN 融合计划：在 [RikkaHub](https://github.com/rikkahub/rikkahub)（Kotlin / Jetpack Compose 多模型 AI 客户端）的基础上接入 [MNN](https://github.com/alibaba/MNN) 本地推理，目标是让同一个 App 既能使用云端模型的完整工具生态，也能完全离线地在设备上运行本地大模型。

## 项目定位

- **本地模型**：通过 MNN 在 Android 设备上离线运行 Qwen 等开源模型，数据不出设备。
- **完整工具生态**：继承 RikkaHub 的助手、MCP、内置工具、工作区、文档解析、联网搜索等全部能力，本地与云端模型共用同一套上层功能。
- **渐进式融合**：
  - Phase 1：以 OpenAI 兼容 HTTP API 对接独立运行的 MNN Chat，不改动上游核心代码。
  - Phase 2（当前）：在单 App 内嵌 MNN 推理引擎（JNI），去掉对独立 MNN Chat 应用的依赖。
  - Phase 3：App 内模型下载/管理、推理性能遥测等。

## Phase 1 用户流程

Phase 1 依赖 MNN Chat 官方 App 内置的 OpenAI 兼容 HTTP API（默认监听 `127.0.0.1:8080`，模型名 `mnn-local`）。

1. **安装 MNN Chat**：从 [MNN 官方渠道](https://github.com/alibaba/MNN) 获取并安装 MNN Chat Android 版。
2. **下载模型**：在 MNN Chat 中下载 Qwen 系列 1.5B–3B 的 4bit 量化模型（兼顾移动端内存与响应速度）。
3. **开启 API 服务**：在 MNN Chat 设置中开启本地 OpenAI 兼容 API 服务（默认端口 8080）。
4. **启用供应商**：打开 RikkaLLM，在供应商（Provider）列表中启用「MNN 本地模型」（预置，默认关闭，地址 `http://127.0.0.1:8080/v1`）。
5. **开始对话**：选择 `mnn-local` 模型即可离线对话。

> 注意：Phase 1 的本地模型暂未实测 tools 透传，预置模型未声明工具能力（`ModelAbility.TOOL`）；需要工具调用时请切换云端模型。

## 许可证

- 本仓库继承 RikkaHub 的 **AGPL-3.0** 许可证（见根目录 `LICENSE`）；对本项目的任何修改在分发/提供网络服务时需按 AGPL-3.0 开源。
- MNN 引擎采用 **Apache-2.0** 许可证；Phase 2 内嵌 MNN 时需遵守其条款（保留版权声明与 NOTICE）。
- **prebuilt SO 与移植代码归属**：`mnn-prebuilt/arm64-v8a/libMNN.so` 由 [alibaba/MNN](https://github.com/alibaba/MNN)（固定 commit `1d535d7`）源码编译而来，`:mnn` 中 `com.alibaba.mnnllm.android.*` 移植代码源自其 `apps/Android/MnnLlmChat`；两者均属 Apache-2.0 上游作品，完整许可证与归属/修改说明见 `mnn/LICENSE-APACHE-2.0` 与 `mnn/NOTICE`，分发 APK 时需随附。
- AGPL-3.0 与 Apache-2.0 兼容，可在同一发行版中共存。

## 路线图

| 阶段 | 内容 |
| --- | --- |
| Phase 1 | 预置「MNN 本地模型」供应商，对接 MNN Chat 的 OpenAI 兼容 API（已完成） |
| Phase 2 | 单 App 内嵌 MNN 推理引擎（JNI），本地推理不再依赖 MNN Chat（代码已完成，待真机验证） |
| Phase 3 | App 内模型下载/版本管理、推理速度与内存遥测 |

## Phase 2 现状（本地 OpenAI 兼容服务）

Phase 2 已在单 App 内嵌 MNN 推理引擎，并在其上实现了一个本地 OpenAI 兼容服务：启动后 App 自身监听 `127.0.0.1` 上的 `/v1/chat/completions`，预置的「MNN 本地模型」供应商会自动指向该服务，本地推理不再依赖独立的 MNN Chat 应用。服务默认监听 **8090** 端口（可在「本地模型引擎」设置页修改，取值 1–65535），避开 App 内置网页服务默认占用的 8080。

### 构建前置

`:mnn` 模块硬依赖两个被 gitignore 的产物，fresh clone 后需先运行 setup 脚本（幂等：已存在时校验固定 commit `1d535d7`）：

- Windows：`powershell -File scripts/setup-mnn.ps1`（内部复用 `scripts/build-mnn-android.ps1`）
- Linux/macOS（含 CI daily-build）：`./scripts/setup-mnn.sh`

脚本会浅克隆 alibaba/MNN 到 `vendor/MNN`（头文件与 CMake 工程）并构建 arm64-v8a 的 `libMNN.so` 发布到 `mnn-prebuilt/`；两者缺失时 Gradle 配置阶段会以明确指引报错（fail fast）。

### 四层架构

| 层 | 位置 | 职责 |
| --- | --- | --- |
| 引擎层 | `:mnn`（`llm/`、`model/` 等移植自 MNN Chat 的 JNI 代码） | `LlmSession` 加载模型、执行生成；`MnnEngineAdapter` 将其封装为 `MnnEngine` 接口 |
| 协议适配层 | `:mnn` 的 `server/tools/` | `ToolsPromptBuilder` 把 OpenAI tools schema 渲染进系统提示并把历史 tool_call/tool 消息转成模型可读文本；`ToolCallStreamParser` 从 token 流中解析 tool call 标记为结构化事件（含分块边界缓冲与安全降级为纯文本） |
| 服务层 | `:mnn` 的 `server/` | `RequestTranslator` 归一化 OpenAI 请求体；`ChatOrchestrator` 驱动引擎并经适配层产出事件；`OpenAiResponses` 生成 SSE/非流式响应；`MnnOpenAiRoutes` 在 Ktor CIO 上挂载路由与鉴权；`MnnServerService` 前台服务宿主；`LocalMnnManager` 统筹引擎与服务生命周期 |
| 桥接层 | `app` 模块 | Manifest 服务声明、Koin 注册（`MnnLocalModule`）、「本地模型引擎」设置页（`SettingLocalEnginePage`）、`MnnLocalProviderSync`（服务启动后把实际端口与 token 写回「MNN 本地模型」供应商条目，复用既有 DataStore 更新路径；服务停止后自动停用该条目） |

### 安全模型

- 服务固定绑定 `127.0.0.1`（loopback），局域网内其他设备不可达。
- 每次启动生成新的 32 字节随机 bearer token，所有请求必须携带 `Authorization: Bearer <token>`（常量时间比较）；App 通过 DataStore 自动把 token 写入供应商条目。
- 服务为 `specialUse` 前台服务（`MnnServerService`），通知提供停止按钮。

### 已知限制

- tools 能力依赖模型自身的文本工具调用能力（提示词约定为 Qwen 风格 tool_call 代码块），推荐 Qwen3 / Qwen2.5-Instruct 系列；其他模型可能无法稳定产出可解析的 tool call。
- 单引擎串行：同一时刻仅支持一路生成，并发请求返回 429；生成期间不允许换载模型。
- 模型需为包含 MNN `config.json` 的本地目录：可在「本地模型引擎」设置页的「模型管理」中填写目录并加载/卸载（App 内下载/版本管理属于 Phase 3）。
- JVM 单测已覆盖协议适配与服务管线（tools 渲染/解析、请求翻译、编排、SSE 输出），但**真机运行时验证（服务启停、前台通知、真实模型加载与流式对话）尚未完成**。

