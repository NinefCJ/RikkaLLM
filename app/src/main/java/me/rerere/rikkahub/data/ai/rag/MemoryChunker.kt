package me.rerere.rikkahub.data.ai.rag

import me.rerere.rikkahub.util.MemoryTimeLabels

/**
 * 把一段文本切分为适合作为“记忆片段”的语义块。
 * 默认策略：按自然段落/换行切分；超长段落按句子上限再切。
 * 移植自 lastchat 分支，仅改包名与 import。
 */
object MemoryChunker {
    /** 单个记忆片段的最大字符数（超过则按句切分） */
    const val MAX_CHUNK_CHARS = 500

    fun chunkText(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val paragraphs = text
            .split(Regex("""\n{2,}"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val out = mutableListOf<String>()
        for (p in paragraphs) {
            if (p.length <= MAX_CHUNK_CHARS) {
                out += p
            } else {
                out += splitLong(p)
            }
        }
        return out.ifEmpty { listOf(text.trim().take(MAX_CHUNK_CHARS)) }
    }

    private fun splitLong(p: String): List<String> {
        val sentences = p.split(Regex("""(?<=[。.!?！？；;\n])"""))
        val chunks = mutableListOf<String>()
        val sb = StringBuilder()
        for (s in sentences) {
            if (sb.length + s.length > MAX_CHUNK_CHARS && sb.isNotEmpty()) {
                chunks += sb.toString().trim()
                sb.clear()
            }
            sb.append(s)
        }
        if (sb.isNotEmpty()) chunks += sb.toString().trim()
        return chunks
    }

    /** 便捷：对一组文本做分块（用于批量导入） */
    fun chunkAll(texts: List<String>): List<String> = texts.flatMap { chunkText(it) }
}
