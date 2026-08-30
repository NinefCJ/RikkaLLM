<div align="center">
  <img src="docs/icon.png" alt="應用程式圖示" width="100" />
  <h1>RikkaLLM</h1>

一個原生 Android LLM 聊天客戶端，支援在雲端供應商與**裝置端本地引擎**之間切換，實現完全離線的對話 🤖💬

[English](README.md) | [简体中文](README_ZH_CN.md) | 繁體中文
</div>

<div align="center">
  <img src="docs/img/chat.png" alt="聊天介面" width="150" />
  <img src="docs/img/desktop.png" alt="模型選擇" width="450" />
</div>

> [!NOTE]
> **關於本倉庫**
> 本倉庫是 [RikkaHub](https://github.com/rikkahub/rikkahub) 的一個功能分支（feature fork）。在原客戶端基礎上新增：
> - **本地 MNN 引擎**：模型可完全在裝置端執行（無需聯網），並透過一個 OpenAI 相容的本地服務對外暴露；
> - **類 ChatGPT 的長期記憶**層（自動抽取、定期整合與 RAG 檢索）；
> - **M3 Expressive** 主題預設。

## 🚀 下載

🔗 [前往官網下載](https://rikka-ai.com/download)（推薦）

🔗 [前往 Google Play 下載](https://play.google.com/store/apps/details?id=me.rerere.rikkahub)

> [!WARNING]
> RikkaHub 存在許多 fork 版本。fork 版本出現的問題與 RikkaHub 無關，請謹慎使用 fork 版本，避免隱私洩露或過度索取權限。

## ✨ 功能特色

核心功能（來自上游 RikkaHub）：

- 🎨 Material You 設計語言與 🌙 暗色模式
- 📦 工作區：基於 proot 的 Linux 智慧體環境
- 🔄 多供應商支援：自訂 API / URL / 模型（相容 OpenAI、Google、Anthropic 的全部介面）
- 🖼️ 多模態輸入（圖片、文字文件、PDF、Docx）
- 🖥️ Web 多端存取支援
- 🛠️ MCP 支援
- 📝 Markdown 渲染（程式碼高亮、LaTeX 公式、表格、Mermaid）
- 🪾 訊息分支
- 🔍 搜尋能力（Exa、Tavily、Zhipu、LinkUp、Brave、Perplexity 等）
- 🧩 提示詞變數（模型名稱、時間等）
- 🤳 供應商 QR Code 匯出 / 匯入
- 🤖 智慧體自訂
- 🌐 自訂 HTTP 請求標頭與請求主體
- 💌 SillyTavern 角色卡匯入

本分支新增：

- 📴 **本地 MNN 引擎** —— 透過 `:mnn` 模組在裝置端完整執行 LLM。本地 OpenAI 相容 HTTP 服務（預設連接埠 `8090`）讓現有聊天介面在零雲端依賴下與已下載模型對話。
- 🧠 **類 ChatGPT 記憶** —— 助手作用域的長期記憶，包含自動抽取、定期整合與 RAG 檢索，可在助手設定頁中開啟。
- 🎭 **M3 Expressive 主題** —— 高飽和、富有情緒表現力的 Material 3 預設，可在主題選擇器中選用（非預設）。

## 🛠️ 從原始碼建置

### 前置條件

| 工具 | 版本 / 說明 |
| --- | --- |
| JDK | 17 |
| Android SDK | 需包含 **CMake** 與 **NDK 25.x** |
| Gradle | 使用 wrapper（`./gradlew`），無需手動安裝 |
| `pnpm` | 僅因為 `web` 模組在 `preBuild` 階段會建置 `web-ui/` |

> 本分支已移除 Firebase，因此建置**無需 `google-services.json`**。

### 1. 準備 MNN 原生依賴

`:mnn` 模組硬依賴兩個被 gitignore 的產物。全新克隆後**必須**先準備，否則 Gradle 設定階段會直接報錯（fail fast）：

- `vendor/MNN` —— alibaba/MNN 原始碼樹，固定 commit `1d535d7`（標頭檔 + CMake 工程）
- `mnn-prebuilt/arm64-v8a/libMNN.so` —— 預先建置的執行期函式庫（連結並打包進 APK）

執行對應平台的冪等 setup 腳本（淺克隆固定 commit 並建置執行期函式庫）：

```powershell
# Windows —— 內部複用 scripts/build-mnn-android.ps1
powershell -File scripts/setup-mnn.ps1
```

```bash
# Linux / macOS（CI 的 daily-build 也使用）
./scripts/setup-mnn.sh
```

### 2. 建置 / 測試

```bash
./gradlew assembleDebug                 # 建置 Debug APK
./gradlew test                          # 執行全部 JVM 單元測試
./gradlew connectedDebugAndroidTest     # 裝置 / 模擬器上的儀器化測試
./gradlew lint                          # 執行 Android Lint
```

在 Android Studio 中開啟本專案並執行 `app` 模組，或安裝產生的 APK。

## 📖 使用說明

### 雲端供應商（上游行為）

1. 啟動應用程式並開啟 **設定 → 供應商**。
2. 輸入 base URL、API Key 與模型清單新增一個供應商（任何相容 OpenAI / Google / Anthropic 的端點皆可）。
3. 開始對話並選擇你設定的助手 / 模型。

### 本地引擎（本分支）

1. 開啟 **設定 → 本地引擎**。
2. 下載相容的 MNN 模型並設定其**模型目錄**。
3. 啟動本地服務（預設連接埠 `8090`）。應用程式透過 OpenAI 相容 API 與其通訊，因此聊天可**完全離線**進行。
4. 在助手設定中開啟 **記憶**，啟用基於 RAG 的長期記憶。

### 主題

在 **設定 → 主題** 中選擇 **Expressive**（或任意其它預設）。

## 🧩 技術堆疊

- [Kotlin](https://kotlinlang.org/) —— 開發語言
- [Koin](https://insert-koin.io/) —— 依賴注入
- [Jetpack Compose](https://developer.android.com/jetpack/compose) —— UI 框架
- [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) —— 偏好儲存
- [Room](https://developer.android.com/training/data-storage/room) —— 資料庫（記憶實體、FTS）
- [Coil](https://coil-kt.github.io/coil/) —— 圖片載入
- [Material You (M3)](https://m3.material.io/) —— UI 設計
- [Navigation 3](https://developer.android.com/guide/navigation/navigation-3) —— 導覽
- [OkHttp](https://square.github.io/okhttp/) —— HTTP 用戶端
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) —— JSON 序列化
- [MNN](https://github.com/alibaba/MNN) —— 裝置端推論引擎（`:mnn` 模組）
- [Ktor](https://ktor.io/) —— 本地 OpenAI 相容服務與 `web` 模組

## 📐 模組結構

- **app** —— 主應用程式（UI、ViewModels、核心邏輯、本地引擎設定、記憶 UI）
- **ai** —— 面向供應商的 AI SDK 抽象層（OpenAI、Google、Anthropic）
- **mnn** —— 本地 MNN 引擎：OpenAI 相容路由、模型註冊表、引擎介面卡、統計
- **common** —— 共享工具與擴充
- **document** —— PDF / DOCX / PPTX / EPUB 解析
- **highlight** —— 程式碼語法高亮
- **material3** —— Material 顏色工具
- **search** —— 聯網搜尋 SDK（Exa、Tavily、Zhipu、Bing、Brave、SearXNG 等）
- **speech** —— TTS / ASR
- **web** —— 內嵌 Ktor 服務 + 託管的 `web-ui/` 靜態建置產物
- **workspace** —— 以沙箱方式向 AI 暴露的每工作區檔案系統與 shell 環境

## ✨ 貢獻

本專案使用 [Android Studio](https://developer.android.com/studio) 開發，歡迎提交 PR！

> [!IMPORTANT]
> 以下 PR 將被拒絕：
> 1. 大規模重構
> 2. 新增功能，本專案有明確取向，不接受新功能類 PR

## 📄 授權

本專案基於 [GNU Affero General Public License v3.0](LICENSE)（AGPL-3.0）開源。

`:mnn` 模組整合了源自 MNN 的程式碼與一個預先建置的 `libMNN.so`；其授權（Apache-2.0）與 NOTICE 位於 `mnn/` 目錄下。
