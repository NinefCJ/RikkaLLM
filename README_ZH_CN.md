<div align="center">
  <img src="docs/icon.png" alt="应用图标" width="100" />
  <h1>RikkaLLM</h1>

一个原生 Android LLM 聊天客户端，支持在云端供应商与**设备端本地引擎**之间切换，实现完全离线的对话 🤖💬

[English](README.md) | 简体中文 | [繁體中文](README_ZH_TW.md)
</div>

<div align="center">
  <img src="docs/img/chat.png" alt="聊天界面" width="150" />
  <img src="docs/img/desktop.png" alt="模型选择" width="450" />
</div>

> [!NOTE]
> **关于本仓库**
> 本仓库是 [RikkaHub](https://github.com/rikkahub/rikkahub) 的一个功能分支（feature fork）。在原客户端基础上新增：
> - **本地 MNN 引擎**：模型可完全在设备端运行（无需联网），并通过一个 OpenAI 兼容的本地服务对外暴露；
> - **类 ChatGPT 的长期记忆**层（自动抽取、定期整合与 RAG 检索）；
> - **M3 Expressive** 主题预设。

## 🚀 下载

🔗 [前往官网下载](https://rikka-ai.com/download)（推荐）

🔗 [前往 Google Play 下载](https://play.google.com/store/apps/details?id=me.rerere.rikkahub)

> [!WARNING]
> RikkaHub 存在许多 fork 版本。fork 版本出现的问题与 RikkaHub 无关，请谨慎使用 fork 版本，避免隐私泄露或过度索要权限。

## ✨ 功能特色

核心功能（来自上游 RikkaHub）：

- 🎨 Material You 设计语言与 🌙 暗色模式
- 📦 工作区：基于 proot 的 Linux 智能体环境
- 🔄 多供应商支持：自定义 API / URL / 模型（兼容 OpenAI、Google、Anthropic 的全部接口）
- 🖼️ 多模态输入（图片、文本文档、PDF、Docx）
- 🖥️ Web 多端访问支持
- 🛠️ MCP 支持
- 📝 Markdown 渲染（代码高亮、LaTeX 公式、表格、Mermaid）
- 🪾 消息分支
- 🔍 搜索能力（Exa、Tavily、Zhipu、LinkUp、Brave、Perplexity 等）
- 🧩 提示词变量（模型名、时间等）
- 🤳 供应商二维码导出 / 导入
- 🤖 智能体自定义
- 🌐 自定义 HTTP 请求头与请求体
- 💌 SillyTavern 角色卡导入

本分支新增：

- 📴 **本地 MNN 引擎** —— 通过 `:mnn` 模块在设备端完整运行 LLM。本地 OpenAI 兼容 HTTP 服务（默认端口 `8090`）让现有聊天界面在零云端依赖下与已下载模型对话。
- 🧠 **类 ChatGPT 记忆** —— 助手作用域的长期记忆，包含自动抽取、定期整合与 RAG 检索，可在助手设置页中开启。
- 🎭 **M3 Expressive 主题** —— 高饱和、富有情绪表现力的 Material 3 预设，可在主题选择器中选用（非默认）。

## 🛠️ 从源码构建

### 前置条件

| 工具 | 版本 / 说明 |
| --- | --- |
| JDK | 17 |
| Android SDK | 需包含 **CMake** 与 **NDK 25.x** |
| Gradle | 使用 wrapper（`./gradlew`），无需手动安装 |
| `pnpm` | 仅因为 `web` 模块在 `preBuild` 阶段会构建 `web-ui/` |

> 本分支已移除 Firebase，因此构建**无需 `google-services.json`**。

### 1. 准备 MNN 原生依赖

`:mnn` 模块硬依赖两个被 gitignore 的产物。全新克隆后**必须**先准备，否则 Gradle 配置阶段会直接报错（fail fast）：

- `vendor/MNN` —— alibaba/MNN 源码树，固定 commit `1d535d7`（头文件 + CMake 工程）
- `mnn-prebuilt/arm64-v8a/libMNN.so` —— 预编译运行时（链接并打包进 APK）

运行对应平台的幂等 setup 脚本（浅克隆固定 commit 并构建运行时）：

```powershell
# Windows —— 内部复用 scripts/build-mnn-android.ps1
powershell -File scripts/setup-mnn.ps1
```

```bash
# Linux / macOS（CI 的 daily-build 也使用）
./scripts/setup-mnn.sh
```

### 2. 构建 / 测试

```bash
./gradlew assembleDebug                 # 构建 Debug APK
./gradlew test                          # 运行全部 JVM 单元测试
./gradlew connectedDebugAndroidTest     # 设备 / 模拟器上的仪器化测试
./gradlew lint                          # 运行 Android Lint
```

在 Android Studio 中打开本项目并运行 `app` 模块，或安装生成的 APK。

## 📖 使用说明

### 云端供应商（上游行为）

1. 启动应用并打开 **设置 → 供应商**。
2. 输入 base URL、API Key 与模型列表添加一个供应商（任何兼容 OpenAI / Google / Anthropic 的端点均可）。
3. 开始对话并选择你配置的助手 / 模型。

### 本地引擎（本分支）

1. 打开 **设置 → 本地引擎**。
2. 下载兼容的 MNN 模型并设置其**模型目录**。
3. 启动本地服务（默认端口 `8090`）。应用通过 OpenAI 兼容 API 与其通信，因此聊天可**完全离线**进行。
4. 在助手设置中开启 **记忆**，启用基于 RAG 的长期记忆。

### 主题

在 **设置 → 主题** 中选择 **Expressive**（或任意其它预设）。

## 🧩 技术栈

- [Kotlin](https://kotlinlang.org/) —— 开发语言
- [Koin](https://insert-koin.io/) —— 依赖注入
- [Jetpack Compose](https://developer.android.com/jetpack/compose) —— UI 框架
- [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) —— 偏好存储
- [Room](https://developer.android.com/training/data-storage/room) —— 数据库（记忆实体、FTS）
- [Coil](https://coil-kt.github.io/coil/) —— 图片加载
- [Material You (M3)](https://m3.material.io/) —— UI 设计
- [Navigation 3](https://developer.android.com/guide/navigation/navigation-3) —— 导航
- [OkHttp](https://square.github.io/okhttp/) —— HTTP 客户端
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) —— JSON 序列化
- [MNN](https://github.com/alibaba/MNN) —— 设备端推理引擎（`:mnn` 模块）
- [Ktor](https://ktor.io/) —— 本地 OpenAI 兼容服务与 `web` 模块

## 📐 模块结构

- **app** —— 主应用（UI、ViewModels、核心逻辑、本地引擎设置、记忆 UI）
- **ai** —— 面向供应商的 AI SDK 抽象层（OpenAI、Google、Anthropic）
- **mnn** —— 本地 MNN 引擎：OpenAI 兼容路由、模型注册表、引擎适配器、统计
- **common** —— 共享工具与扩展
- **document** —— PDF / DOCX / PPTX / EPUB 解析
- **highlight** —— 代码语法高亮
- **material3** —— Material 颜色工具
- **search** —— 联网搜索 SDK（Exa、Tavily、Zhipu、Bing、Brave、SearXNG 等）
- **speech** —— TTS / ASR
- **web** —— 内嵌 Ktor 服务 + 托管的 `web-ui/` 静态构建产物
- **workspace** —— 以沙箱方式向 AI 暴露的每工作区文件系统与 shell 环境

## ✨ 贡献

本项目使用 [Android Studio](https://developer.android.com/studio) 开发，欢迎提交 PR！

> [!IMPORTANT]
> 以下 PR 将被拒绝：
> 1. 与翻译相关的改动，例如新增语言或更新已有翻译
> 2. 新增功能，本项目有明确取向，不接受新功能类 PR
> 3. 大规模重构以及由 AI 生成的改动

## 📄 许可证

本项目基于 [GNU Affero General Public License v3.0](LICENSE)（AGPL-3.0）开源。

`:mnn` 模块集成了源自 MNN 的代码与一个预编译的 `libMNN.so`；其许可证（Apache-2.0）与 NOTICE 位于 `mnn/` 目录下。
