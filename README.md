<div align="center">
  <img src="docs/icon.png" alt="App Icon" width="100" />
  <h1>RikkaLLM</h1>

A native Android LLM chat client that supports switching between cloud providers **and an on-device local engine** for fully offline conversations 🤖💬

[简体中文](README_ZH_CN.md) | [繁體中文](README_ZH_TW.md) | English
</div>

<div align="center">
  <img src="docs/img/chat.png" alt="Chat Interface" width="150" />
  <img src="docs/img/desktop.png" alt="Models Picker" width="450" />
</div>

> [!NOTE]
> **About this repository**
> This is a feature fork of [RikkaHub](https://github.com/rikkahub/rikkahub). On top of the upstream client it adds:
> - a **local MNN engine** so models can run entirely on-device (no network), exposed through an OpenAI-compatible local server;
> - a **ChatGPT-like long-term memory** layer (extraction, consolidation, and RAG retrieval);
> - an **M3 Expressive** theme preset.
>
> The upstream README (downloads, sponsors, donation, star-history) is intentionally kept below for reference.

## 🚀 Download

🔗 [Download from Website](https://rikka-ai.com/download) (Recommended)

🔗 [Download from Google Play](https://play.google.com/store/apps/details?id=me.rerere.rikkahub)

> [!WARNING]
> There are many forked versions of RikkaHub. Issues with forks are unrelated to RikkaHub, so please use forks with caution to avoid privacy leaks or excessive permission requests.

## ✨ Features

Core (from upstream RikkaHub):

- 🎨 Material You Design and 🌙 Dark mode
- 📦 Workspace: a proot-based Linux agent environment
- 🔄 Multiple AI Provider Support: custom API / URL / models (all OpenAI, Google, Anthropic compatible api)
- 🖼️ Multimodal input support (Image, Text Documentation, PDF, Docx)
- 🖥️ Web access for multi-platform use
- 🛠️ MCP support
- 📝 Markdown Rendering (with code highlighting, Latex formulas, tables, Mermaid)
- 🪾 Message Branching
- 🔍 Search capabilities (Exa, Tavily, Zhipu, LinkUp, Brave, Perplexity, etc.)
- 🧩 Prompt variables (model name, time, etc.)
- 🤳 QR code export and import for providers
- 🤖 Agent customization
- 🌐 Custom HTTP request headers and request bodies
- 💌 Silly Tavern character card import

Added in this fork:

- 📴 **Local MNN engine** — run LLMs fully on-device via the `:mnn` module. A local OpenAI-compatible HTTP server (default port `8090`) lets the existing chat UI talk to a downloaded model with zero cloud dependency.
- 🧠 **ChatGPT-like memory** — assistant-scoped long-term memory with automatic extraction, periodic consolidation, and RAG retrieval, surfaced in the assistant settings page.
- 🎭 **M3 Expressive theme** — a high-saturation, emotionally expressive Material 3 preset, selectable from the theme picker (non-default).

## 🛠️ Build from source

### Prerequisites

| Tool | Version / Notes |
| --- | --- |
| JDK | 17 |
| Android SDK | with **CMake** and **NDK 25.x** |
| Gradle | use the wrapper (`./gradlew`) — no manual install |
| `pnpm` | only needed because the `web` module builds `web-ui/` during `preBuild` |

> Firebase has been removed from this fork, so **no `google-services.json` is required** to build.

### 1. Prepare the MNN native dependencies

The `:mnn` module hard-depends on two git-ignored artifacts. After a fresh clone you **must** prepare them, otherwise the Gradle configuration phase fails fast:

- `vendor/MNN` — alibaba/MNN source tree pinned to commit `1d535d7` (headers + CMake project)
- `mnn-prebuilt/arm64-v8a/libMNN.so` — prebuilt runtime (linked into and packaged with the APK)

Run the idempotent setup script for your platform (it shallow-clones the pinned commit and builds the runtime):

```powershell
# Windows — internally reuses scripts/build-mnn-android.ps1
powershell -File scripts/setup-mnn.ps1
```

```bash
# Linux / macOS (also used by CI daily-build)
./scripts/setup-mnn.sh
```

### 2. Build / test

```bash
./gradlew assembleDebug                 # build the Debug APK
./gradlew test                          # run all JVM unit tests
./gradlew connectedDebugAndroidTest     # on-device / emulator instrumentation tests
./gradlew lint                          # run Android Lint
```

Open the project in Android Studio and run the `app` module, or install the produced APK.

## 📖 Usage

### Cloud providers (upstream behavior)

1. Launch the app and open **Settings → Providers**.
2. Add a provider by entering its base URL, API key, and model list (any OpenAI / Google / Anthropic compatible endpoint works).
3. Start a conversation and pick the assistant / model you configured.

### Local engine (this fork)

1. Open **Settings → Local Engine**.
2. Download a compatible MNN model and set its **model directory**.
3. Start the local server (defaults to port `8090`). The app talks to it through an OpenAI-compatible API, so chat works **fully offline**.
4. Toggle **Memory** in an assistant's settings to enable long-term, RAG-backed memory.

### Theme

Pick **Expressive** (or any other preset) from **Settings → Theme**.

## 🧩 Tech stack

- [Kotlin](https://kotlinlang.org/) — development language
- [Koin](https://insert-koin.io/) — dependency injection
- [Jetpack Compose](https://developer.android.com/jetpack/compose) — UI framework
- [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) — preference storage
- [Room](https://developer.android.com/training/data-storage/room) — database (memory entities, FTS)
- [Coil](https://coil-kt.github.io/coil/) — image loading
- [Material You (M3)](https://m3.material.io/) — UI design
- [Navigation 3](https://developer.android.com/guide/navigation/navigation-3) — navigation
- [OkHttp](https://square.github.io/okhttp/) — HTTP client
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) — JSON serialization
- [MNN](https://github.com/alibaba/MNN) — on-device inference engine (`:mnn` module)
- [Ktor](https://ktor.io/) — local OpenAI-compatible server & `web` module

## 📐 Module structure

- **app** — main application (UI, ViewModels, core logic, local engine settings, memory UI)
- **ai** — AI SDK abstraction for providers (OpenAI, Google, Anthropic)
- **mnn** — local MNN engine: OpenAI-compatible routes, model registry, engine adapter, stats
- **common** — shared utilities and extensions
- **document** — PDF / DOCX / PPTX / EPUB parsing
- **highlight** — code syntax highlighting
- **material3** — Material color utilities
- **search** — web search SDK (Exa, Tavily, Zhipu, Bing, Brave, SearXNG, …)
- **speech** — TTS / ASR
- **web** — embedded Ktor server + hosted `web-ui/` static build
- **workspace** — sandboxed per-workspace filesystem and shell exposed to the AI as tools

## ✨ Contributing

This project is developed using [Android Studio](https://developer.android.com/studio). PRs are welcome!

> [!IMPORTANT]
> The following PRs will be rejected:
> 1. Translation related changes, such as adding new languages or updating existing translations
> 2. Adding new features, this project is opinionated and will not accept pull requests for new features
> 3. Large-scale refactoring and changes generated by AI

## 💰 Donate

* [Patreon](https://patreon.com/rikkahub)
* [爱发电](https://afdian.com/a/reovo)

## ⭐ Star History

If you like this project, please give it a star ⭐

<a href="https://www.star-history.com/?type=date&repos=rikkahub/rikkahub">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=rikkahub/rikkahub&type=date&theme=dark&legend=top-left&sealed_token=qSytWeq7LkzQQViTjK0MYlvvA_qkfuwjOxOqgbRpLUZZwok5rO6LXhpVL7Mq-q3o89BfKpzE7g66BCy18H6eiqTsD8czD0J-HejLqmHy-npcvCTHu11wZw" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=rikkahub/rikkahub&type=date&legend=top-left&sealed_token=qSytWeq7LkzQQViTjK0MYlvvA_qkfuwjOxOqgbRpLUZZwok5rO6LXhpVL7Mq-q3o89BfKpzE7g66BCy18H6eiqTsD8czD0J-HejLqmHy-npcvCTHu11wZw" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=rikkahub/rikkahub&type=date&legend=top-left&sealed_token=qSytWeq7LkzQQViTjK0MYlvvA_qkfuwjOxOqgbRpLUZZwok5rO6LXhpVL7Mq-q3o89BfKpzE7g66BCy18H6eiqTsD8czD0J-HejLqmHy-npcvCTHu11wZw" />
 </picture>
</a>

## 📄 License

This project is licensed under the [GNU Affero General Public License v3.0](LICENSE) (AGPL-3.0).

The `:mnn` module bundles MNN-derived code and a prebuilt `libMNN.so`; their licenses (Apache-2.0) and NOTICE are included under `mnn/`.
