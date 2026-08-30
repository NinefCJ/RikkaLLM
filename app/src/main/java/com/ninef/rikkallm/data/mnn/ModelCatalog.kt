package com.ninef.rikkallm.data.mnn

import java.net.URLEncoder
import kotlin.text.Charsets.UTF_8

/**
 * Phase 3 model catalog: the list of models the app can download and manage in-app,
 * surfaced as a "model market" in the local-engine settings page.
 *
 * The catalog is intentionally data-driven and small. Each entry references a remote
 * source (HuggingFace Hub by default) plus the exact set of files that make up a
 * usable MNN model (config + weights + tokenizer). To add or fix a model, append a
 * [CatalogModel] to [ModelCatalog.default] — the downloader and UI pick it up with no
 * other code changes.
 *
 * NOTE: MNN-format weights are produced by converting a HuggingFace checkpoint with the
 * MNN conversion tools, so the [ModelSource.HuggingFace.repo] must point at a repo that
 * actually publishes `.mnn` files. The URLs below follow the HuggingFace `resolve` API
 * and are editable to track upstream releases.
 */
object ModelCatalog {
    val default: List<CatalogModel> = listOf(
        CatalogModel(
            id = "qwen2.5-0.5b-instruct-mnn",
            name = "Qwen2.5-0.5B-Instruct (MNN)",
            description = "最小的 Qwen2.5 指令模型，约 0.5B 参数，适合低端设备试跑。",
            version = "1.0",
            source = ModelSource.HuggingFace("MNN-Community/Qwen2.5-0.5B-Instruct-MNN", "main"),
            files = listOf(
                "config.json",
                "qwen2.5-0.5b-instruct-q4_k.mnn",
                "tokenizer.json",
            ),
            fileSizes = mapOf(
                "qwen2.5-0.5b-instruct-q4_k.mnn" to 350L * 1024 * 1024,
            ),
            minRamMb = 1500,
        ),
        CatalogModel(
            id = "qwen2.5-1.5b-instruct-mnn",
            name = "Qwen2.5-1.5B-Instruct (MNN)",
            description = "1.5B 参数，质量与速度的平衡点，推荐的中端设备选择。",
            version = "1.0",
            source = ModelSource.HuggingFace("MNN-Community/Qwen2.5-1.5B-Instruct-MNN", "main"),
            files = listOf(
                "config.json",
                "qwen2.5-1.5b-instruct-q4_k.mnn",
                "tokenizer.json",
            ),
            fileSizes = mapOf(
                "qwen2.5-1.5b-instruct-q4_k.mnn" to 1000L * 1024 * 1024,
            ),
            minRamMb = 2500,
        ),
        CatalogModel(
            id = "llama-3.2-1b-instruct-mnn",
            name = "Llama-3.2-1B-Instruct (MNN)",
            description = "Meta 的 1B 指令模型，英文能力强，适合轻量英文对话。",
            version = "1.0",
            source = ModelSource.HuggingFace("MNN-Community/Llama-3.2-1B-Instruct-MNN", "main"),
            files = listOf(
                "config.json",
                "llama-3.2-1b-instruct-q4_k.mnn",
                "tokenizer.json",
            ),
            fileSizes = mapOf(
                "llama-3.2-1b-instruct-q4_k.mnn" to 700L * 1024 * 1024,
            ),
            minRamMb = 2000,
        ),
    )
}

/**
 * A model the app knows how to download and manage.
 *
 * @param id Stable folder name under the engine's models root. Also used as the
 *   matching key between the catalog and installed models.
 * @param version Catalog-side version string, compared against the marker written at
 *   install time to decide whether an "update" is available.
 * @param files Relative paths (as they appear in the remote source) to download in order.
 * @param fileSizes Best-effort per-file sizes (bytes) used to render an overall progress
 *   total before the server reports Content-Length. Unknown files default to 0.
 */
data class CatalogModel(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val source: ModelSource,
    val files: List<String>,
    val fileSizes: Map<String, Long> = emptyMap(),
    val minRamMb: Int? = null,
)

/**
 * Where a model's files live. The downloader turns each [CatalogModel.files] entry into a
 * concrete URL via [urlFor].
 */
sealed interface ModelSource {
    fun urlFor(file: String): String

    /** HuggingFace Hub `resolve` API: https://huggingface.co/<repo>/resolve/<ref>/<file>. */
    data class HuggingFace(val repo: String, val ref: String) : ModelSource {
        override fun urlFor(file: String): String {
            val encoded = file.split("/").joinToString("/") { segment ->
                URLEncoder.encode(segment, UTF_8.name()).replace("+", "%20")
            }
            return "https://huggingface.co/$repo/resolve/$ref/$encoded"
        }
    }

    /** Arbitrary base URL (e.g. a ModelScope/CDN mirror) with files appended verbatim. */
    data class Remote(val baseUrl: String) : ModelSource {
        override fun urlFor(file: String): String =
            "${baseUrl.removeSuffix("/")}/${file.split("/").joinToString("/") { it }}"
    }

    /** ModelScope (魔搭社区) `resolve` API: https://modelscope.cn/models/<repo>/resolve/<ref>/<file>. */
    data class ModelScope(val repo: String, val ref: String = "master") : ModelSource {
        override fun urlFor(file: String): String {
            val encoded = file.split("/").joinToString("/") { segment ->
                URLEncoder.encode(segment, UTF_8.name()).replace("+", "%20")
            }
            return "https://modelscope.cn/models/$repo/resolve/$ref/$encoded"
        }
    }
}
