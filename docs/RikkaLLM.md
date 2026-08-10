# RikkaLLM

RikkaHub × MNN 融合计划：在 [RikkaHub](https://github.com/rikkahub/rikkahub)（Kotlin / Jetpack Compose 多模型 AI 客户端）的基础上接入 [MNN](https://github.com/alibaba/MNN) 本地推理，目标是让同一个 App 既能使用云端模型的完整工具生态，也能完全离线地在设备上运行本地大模型。

## 项目定位

- **本地模型**：通过 MNN 在 Android 设备上离线运行 Qwen 等开源模型，数据不出设备。
- **完整工具生态**：继承 RikkaHub 的助手、MCP、内置工具、工作区、文档解析、联网搜索等全部能力，本地与云端模型共用同一套上层功能。
- **渐进式融合**：
  - Phase 1（当前）：以 OpenAI 兼容 HTTP API 对接独立运行的 MNN Chat，不改动上游核心代码。
  - Phase 2：在单 App 内嵌 MNN 推理引擎（JNI），去掉对独立 MNN Chat 应用的依赖。
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
- AGPL-3.0 与 Apache-2.0 兼容，可在同一发行版中共存。

## 路线图

| 阶段 | 内容 |
| --- | --- |
| Phase 1 | 预置「MNN 本地模型」供应商，对接 MNN Chat 的 OpenAI 兼容 API（已完成） |
| Phase 2 | 单 App 内嵌 MNN 推理引擎（JNI），本地推理不再依赖 MNN Chat |
| Phase 3 | App 内模型下载/版本管理、推理速度与内存遥测 |
