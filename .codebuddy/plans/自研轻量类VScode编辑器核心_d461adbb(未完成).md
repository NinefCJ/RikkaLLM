---
name: 自研轻量类VScode编辑器核心
overview: 在 RikkaLLM App 内新建 Compose 编辑器模块，彻底移除 code-server/VSCodroid 重资产方案，用 tree-sitter 自研编辑器核心，实现高亮/提示/补全 + 文件树/多标签 + 命令面板/设置/主题，并清理 C 盘空间。
---

## 用户需求概述

在 RikkaLLM Android 应用中，彻底舍弃现有的"code-server + WebView + VSCodroid"重资产 IDE 方案，从零自研一套轻量、高保真还原 VScode 观感与交互的编辑器核心。新编辑器以原生 Compose 页面形式嵌入应用，天然兼容 arm64-v8a 与 x86_64。第一版不实现插件系统，但必须优先做好语法高亮、语法提示与代码补全等核心编辑能力，并尽量贴近 VScode 的界面与交互体验。

## 核心功能

- **空间清理**：删除约 360MB 的 `ide-runtime` 资产、`VSCodroid-main/` 子工程、`fetch-ide-runtime.ps1` 及旧 `IdePage/IdePane/components/IdeRuntimeInstaller` 等相关集成代码；清理 C 盘 Gradle/Temp 缓存，并将 `GRADLE_USER_HOME` 持久化指向 D 盘以避免再次撑满 C 盘。
- **自研编辑器核心（Compose）**：行号槽、光标、选区、滚动、大文件虚拟渲染、撤销/重做、多光标、括号匹配、自动缩进。
- **语言智能（tree-sitter）**：内置多语言 tree-sitter 语法库（.so），提供高精度语法高亮、语法错误/诊断着色、基于 AST 的悬浮提示与基础代码补全；以现有 `:highlight`（正则）作为无 grammar 语言的兜底；预留 LSP 接口供后续扩展。
- **VScode 风格 UI 外壳**：活动栏、侧边栏（文件资源管理器）、编辑器组（多标签页 + 分屏）、状态栏、命令面板（Ctrl+P 文件跳转、Ctrl+Shift+P 命令）、设置面板、明暗主题（贴近 VScode 配色，复用 `:material3` 与 App 主题）。
- **多文件/工作区**：基于 `:workspace` 文件系统的侧边文件树、多标签页、[User Cancelled]