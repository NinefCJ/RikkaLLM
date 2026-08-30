package com.ninef.rikkallm.data.webmount.adapters

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import com.ninef.rikkallm.data.webmount.WebMountAdapter
import com.ninef.rikkallm.data.webmount.WebMountAuthType
import com.ninef.rikkallm.data.webmount.WebMountConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64

/**
 * GitHub 适配器（PAT 鉴权）。把账号暴露为可读写工具：
 * - 只读：列出仓库、列出 Issues、读取文件
 * - 写操作：创建 Issue（默认需要用户确认）
 */
class GitHubAdapter : WebMountAdapter {
    override val siteId = "github"
    override val displayName = "GitHub"
    override val supportedAuth = listOf(WebMountAuthType.PAT)

    private val json = Json { ignoreUnknownKeys = true }

    override fun buildTools(mount: WebMountConfig, client: OkHttpClient): List<Tool> {
        val suffix = mount.id.take(8)
        val prefix = (if (mount.name.isNotBlank()) slug(mount.name) else "github") + "_" + suffix
        val label = mount.name.ifBlank { mount.username.ifBlank { "默认" } }
        val apiBase = mount.baseUrl.ifBlank { "https://api.github.com" }
        val auth = "Bearer ${mount.token}"

        fun schema(
            vararg required: Pair<String, String>,
            optional: List<String> = emptyList(),
        ): InputSchema.Obj {
            val props = buildJsonObject {
                (required.toList() + optional.map { it to "" }).forEach { (name, desc) ->
                    put(
                        name,
                        buildJsonObject {
                            put("type", "string")
                            if (desc.isNotBlank()) put("description", desc)
                        },
                    )
                }
            }
            val req = required.map { it.first }
            return InputSchema.Obj(properties = props, required = req)
        }

        return listOf(
            Tool(
                name = "${prefix}_list_repos",
                description = "列出 GitHub 账号「$label」下的仓库（含名称、描述、语言、Stars、链接）。可选 type=owner/all/public。",
                parameters = { schema("type" to "仓库范围：owner/all/public，默认 owner") },
                needsApproval = { false },
                execute = { args ->
                    val type = args.jsonObject["type"]?.jsonPrimitive?.contentOrNull ?: "owner"
                    val raw = call(client, auth, apiBase, "/user/repos?type=$type&per_page=50&sort=updated")
                    listOf(UIMessagePart.Text(formatRepos(raw)))
                },
            ),
            Tool(
                name = "${prefix}_list_issues",
                description = "列出 GitHub 仓库「$label」的 Issues（编号、标题、状态、作者、链接）。需要 owner 与 repo。",
                parameters = {
                    schema(
                        "owner" to "仓库所属用户名或组织",
                        "repo" to "仓库名",
                        optional = listOf("state"),
                    )
                },
                needsApproval = { false },
                execute = { args ->
                    val owner = args.jsonObject["owner"]?.jsonPrimitive?.contentOrNull ?: ""
                    val repo = args.jsonObject["repo"]?.jsonPrimitive?.contentOrNull ?: ""
                    val state = args.jsonObject["state"]?.jsonPrimitive?.contentOrNull ?: "open"
                    val raw = call(client, auth, apiBase, "/repos/$owner/$repo/issues?state=$state&per_page=50")
                    listOf(UIMessagePart.Text(formatIssues(raw)))
                },
            ),
            Tool(
                name = "${prefix}_read_file",
                description = "读取 GitHub 仓库「$label」中的文件内容（按路径返回文本）。需要 owner、repo、path。",
                parameters = {
                    schema(
                        "owner" to "仓库所属用户名或组织",
                        "repo" to "仓库名",
                        "path" to "文件路径，如 src/main.kt",
                        optional = listOf("ref"),
                    )
                },
                needsApproval = { false },
                execute = { args ->
                    val owner = args.jsonObject["owner"]?.jsonPrimitive?.contentOrNull ?: ""
                    val repo = args.jsonObject["repo"]?.jsonPrimitive?.contentOrNull ?: ""
                    val path = args.jsonObject["path"]?.jsonPrimitive?.contentOrNull ?: ""
                    val ref = args.jsonObject["ref"]?.jsonPrimitive?.contentOrNull ?: ""
                    val q = if (ref.isBlank()) "" else "?ref=$ref"
                    val raw = call(client, auth, apiBase, "/repos/$owner/$repo/contents/$path$q")
                    listOf(UIMessagePart.Text(formatFile(raw)))
                },
            ),
            Tool(
                name = "${prefix}_create_issue",
                description = "在 GitHub 仓库「$label」中创建 Issue（写操作）。需要 owner、repo、title。",
                parameters = {
                    schema(
                        "owner" to "仓库所属用户名或组织",
                        "repo" to "仓库名",
                        "title" to "Issue 标题",
                        optional = listOf("body"),
                    )
                },
                needsApproval = { true },
                execute = { args ->
                    val owner = args.jsonObject["owner"]?.jsonPrimitive?.contentOrNull ?: ""
                    val repo = args.jsonObject["repo"]?.jsonPrimitive?.contentOrNull ?: ""
                    val title = args.jsonObject["title"]?.jsonPrimitive?.contentOrNull ?: ""
                    val body = args.jsonObject["body"]?.jsonPrimitive?.contentOrNull ?: ""
                    val payload = buildJsonObject {
                        put("title", title)
                        if (body.isNotBlank()) put("body", body)
                    }.toString()
                    val raw = call(client, auth, apiBase, "/repos/$owner/$repo/issues", method = "POST", body = payload)
                    listOf(UIMessagePart.Text(formatCreatedIssue(raw)))
                },
            ),
        )
    }

    private suspend fun call(
        client: OkHttpClient,
        auth: String,
        apiBase: String,
        path: String,
        method: String = "GET",
        body: String? = null,
    ): String = withContext(Dispatchers.IO) {
        runCatching {
            val builder = Request.Builder()
                .url(apiBase + path)
                .header("Authorization", auth)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
            when (method) {
                "POST" -> builder.post((body ?: "{}").toRequestBody("application/json".toMediaType()))
                else -> builder.get()
            }
            client.newCall(builder.build()).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) "⚠️ GitHub API 错误 ${resp.code}：${text.take(500)}" else text
            }
        }.getOrElse { "⚠️ 网络请求失败：${it.message}" }
    }

    private fun formatRepos(raw: String): String {
        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull()
        if (root !is JsonArray) return raw.take(2000)
        if (root.isEmpty()) return "（该账号下没有仓库，或令牌无权访问）"
        return root.joinToString("\n----\n") { el ->
            val o = el.jsonObject
            val name = o["full_name"]?.jsonPrimitive?.contentOrNull
                ?: o["name"]?.jsonPrimitive?.contentOrNull ?: "?"
            val desc = o["description"]?.jsonPrimitive?.contentOrNull ?: ""
            val stars = o["stargazers_count"]?.jsonPrimitive?.contentOrNull ?: "0"
            val url = o["html_url"]?.jsonPrimitive?.contentOrNull ?: ""
            val lang = o["language"]?.jsonPrimitive?.contentOrNull ?: ""
            "📦 $name${if (lang.isNotBlank()) " ($lang)" else ""}\n   $desc\n   ⭐$stars  $url"
        }.take(5000)
    }

    private fun formatIssues(raw: String): String {
        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull()
        if (root !is JsonArray) return raw.take(2000)
        if (root.isEmpty()) return "（没有匹配的问题）"
        return root.joinToString("\n----\n") { el ->
            val o = el.jsonObject
            val num = o["number"]?.jsonPrimitive?.contentOrNull ?: "?"
            val state = o["state"]?.jsonPrimitive?.contentOrNull ?: "?"
            val title = o["title"]?.jsonPrimitive?.contentOrNull ?: ""
            val url = o["html_url"]?.jsonPrimitive?.contentOrNull ?: ""
            val user = o["user"]?.jsonObject?.get("login")?.jsonPrimitive?.contentOrNull ?: ""
            "#$num [$state] $title\n   作者:$user  $url"
        }.take(5000)
    }

    private fun formatFile(raw: String): String {
        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull()
        if (root !is JsonObject) return raw.take(2000)
        val o = root
        val encoding = o["encoding"]?.jsonPrimitive?.contentOrNull
        val content = o["content"]?.jsonPrimitive?.contentOrNull ?: return raw.take(2000)
        val decoded = if (encoding == "base64") {
            runCatching { String(Base64.getDecoder().decode(content.replace("\n", ""))) }.getOrElse { content }
        } else {
            content
        }
        val path = o["path"]?.jsonPrimitive?.contentOrNull ?: ""
        val size = o["size"]?.jsonPrimitive?.contentOrNull ?: ""
        return "📄 $path (${size} bytes)\n```\n${decoded.take(8000)}\n```"
    }

    private fun formatCreatedIssue(raw: String): String {
        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull()
        if (root !is JsonObject) return raw.take(2000)
        val num = root["number"]?.jsonPrimitive?.contentOrNull ?: "?"
        val url = root["html_url"]?.jsonPrimitive?.contentOrNull ?: ""
        val title = root["title"]?.jsonPrimitive?.contentOrNull ?: ""
        return "✅ 已创建 Issue #$num：$title\n   $url"
    }

    private fun slug(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(24).ifBlank { "github" }
}
