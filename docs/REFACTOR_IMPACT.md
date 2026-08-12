# 重构影响分析报告：并入上游 `refactor/ai-stream`

> 目标：将上游分支 `upstream/refactor/ai-stream`（AI 流式管道重构，85 文件 / +7892 −1016）合并/rebase 进本 fork（`master`，相对 upstream/master 改动 118 文件）。
> 基线：`upstream/master`。当前 `master` 已推送到 `NinefCJ/RikkaLLM`（安全锚点）。

## 1. 冲突面量化

| 维度 | 结果 |
|------|------|
| 本 fork 改动文件数 | 118 |
| 上游重构改动文件数 | 85 |
| **路径级交集（潜在文本冲突）** | **1 个：`service/ChatService.kt`** |
| 本 fork 使用将被移除的 `ai` API（`handleMessageChunk` / `ai.core.merge`） | 仅 `GenerationHandler.kt` 自身（由重构整体替换）；fork 其他文件**零引用** |

## 2. ChatService.kt 冲突性质（唯一交集）

两侧改动位于**不同区段**，git 三路合并大概率自动通过，即便冲突也极小、局部化：

- **本 fork（master）侧**：集中在 `sendMessage` / `generateChatStream` 附近，新增"Phase 3 异常重复输出检测"（导入 `AssistantMemory`、companion 常量、`hasDuplicateRun`、流式检测 + 自动重新生成）。
- **上游重构侧**：集中在后台辅助方法 `generateTitle` / `generateSuggestion` / `compressSummary`（包裹 `withContext(Dispatchers.IO)`，并将 `result.choices[0].message?.toText()` → `result.message.toText()`），以及顶部新增 `import kotlinx.coroutines.withContext`。

> 重构侧的 `result.message` 变更源自 `ai` 模块 `generateText` 返回结构变化，由 `GenerationHandler` 内部统一承接；fork 未新增任何 `choices[0].message` 调用，不会被牵连。

## 3. 编译级风险：低

- 重构从 `ai` 模块移除 `ai.core.merge` 与 `ai.ui.handleMessageChunk`，但搜索确认 fork 中仅 `GenerationHandler.kt` 使用二者，而该文件由重构整体替换。
- `GenerationHandler` 的**构造函数签名**与公开方法 `generateText(...)` / `translateText(...)` **未变**；fork 的 DI（`DataSourceModule`/`AppModule`）与 `ChatService` 调用保持有效。
- 结论：合并后只需 `./gradlew :app:compileDebugKotlin :ai:compileDebugKotlin` 验证。

## 4. 语义 / 行为风险：中–高（需真机验证）

重构把流式分块处理从 `handleMessageChunk` 重写为 `StreamChunkHandler` + `handleTextGenerationResult`（新增类位于 `ai` 模块）。这改变了 UIMessage 的合并语义（usage 累计、工具调用分块组装、partial-text 拼接）。

- 本 fork 的 **MNN 本地引擎**产出 OpenAI 兼容 SSE 流，依赖上述合并语义。重写后必须回归验证：**本地流式输出、工具调用（tool-call 流式解析）、usage 累计**。
- `ChatMessage` / `ChatMessageCot` / `ChatMessageTools` 被重构（新增 `ChatMessageServerToolStep`、修改 `groupMessageParts`）。fork 的 `ChatList.kt` / `ChatVM.kt` 调用这些组件，需确认公开签名未变（构建即可暴露）。

## 5. 其他变更（低风险 / 无影响）

- `ai/src/test/resources/stream-traces/*` 与 `trace-cli/`（Node/bun 工具链）：纯新增、隔离，**不影响 Android 构建**，但会扩大仓库体积。
- `Export.kt` / `LogPage.kt` / `ProviderConnectionTester.kt` / `OcrTransformer.kt`：仅重构侧改动，文本合并干净。

## 6. 回滚方案

- `master` 已推送 GitHub 作为安全锚点。
- 合并在独立分支 `refactor/ai-stream-merge` 上进行：
  - 合并中出错：`git merge --abort`
  - 合并后构建/验证失败：`git checkout master && git branch -D refactor/ai-stream-merge`
- 全程不触碰 `master` 直到验证通过。

## 7. 建议执行步骤（待你确认后再动手）

1. `git checkout -b refactor/ai-stream-merge`（基于当前 `master`）
2. `git merge upstream/refactor/ai-stream`（优先 merge 而非 rebase，便于 abort；如遇 ChatService.kt 冲突，按第 2 节局部解决）
3. `./gradlew :app:compileDebugKotlin :ai:compileDebugKotlin` 捕获编译错误
4. `./gradlew test` 跑 JVM 单测（重构新增大量 stream-trace 测试）
5. `assembleDebug` 装模拟器，验证：MNN 本地流式 / 工具调用 / 记忆注入(RAG) / 翻译 / 标题与建议生成
6. 全部绿灯后：`git checkout master && git merge refactor/ai-stream-merge && git push`，并清理临时分支

## 8. 决策点

- ✅ 风险可控，建议**按第 7 节执行**（保留随时回滚能力）。
- ⚠️ 若你想更保守：可先只 `cherry-pick` 重构中与 MNN/记忆无关的部分，或改为 `rebase` 以获得线性历史（但 rebase 冲突解决更繁琐）。
- 請确认是否开始执行第 7 节；或指定调整（如改用 rebase、暂不引入 trace-cli）。
