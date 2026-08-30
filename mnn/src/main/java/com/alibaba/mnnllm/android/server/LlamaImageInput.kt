// Normalises image references into something llama.cpp's Kotlin binding can open.
//
// Clients send images in several shapes: base64 `data:` URLs (the OpenAI convention), `file://`
// URIs, bare absolute paths or `content://` URIs. LlamaHelper.predict() runs the value through
// Uri.parse() + ContentResolver.openInputStream(), which only understands real URIs — so base64
// payloads must be decoded to disk first and bare paths need a `file://` scheme.
//
// Pure java.io/java.util (no android.*), so it is unit-testable on the JVM.

package com.alibaba.mnnllm.android.server

import java.io.File
import java.security.MessageDigest
import java.util.Base64

object LlamaImageInput {

    private val DATA_URI = Regex("""^data:image/([a-zA-Z0-9.+-]+);base64,(.+)$""", RegexOption.DOT_MATCHES_ALL)

    /**
     * Resolves [image] to a URI string readable by the engine, or null when the reference
     * cannot be handled locally (e.g. a remote http(s) URL, which we deliberately do not
     * download — the engine is offline by design).
     */
    fun resolve(image: String, cacheDir: File): String? {
        val value = image.trim()
        if (value.isBlank()) return null

        DATA_URI.matchEntire(value)?.let { match ->
            val subtype = match.groupValues[1]
            val payload = match.groupValues[2]
            val bytes = decodeBase64(payload) ?: return null
            val file = writeTempImage(bytes, subtype, cacheDir) ?: return null
            return file.toURI().toString()
        }

        // Already a URI the platform can open.
        if (value.startsWith("content://", ignoreCase = true) ||
            value.startsWith("file://", ignoreCase = true) ||
            value.startsWith("android.resource://", ignoreCase = true)
        ) {
            return value
        }

        // Anything else carrying a scheme is a remote/unsupported reference (e.g. https://).
        if (value.contains("://")) return null

        // No scheme: a bare filesystem path (Unix or Windows). Give it a file: scheme so
        // Uri.parse() yields something ContentResolver can actually open.
        val file = File(value)
        return if (file.isFile) file.toURI().toString() else null
    }

    /** Removes previously decoded images so repeated generations cannot grow the cache. */
    fun clearCache(cacheDir: File) {
        imageDir(cacheDir).deleteRecursively()
    }

    private fun decodeBase64(payload: String): ByteArray? = runCatching {
        Base64.getDecoder().decode(payload.replace(Regex("\\s"), ""))
    }.getOrNull()

    private fun writeTempImage(bytes: ByteArray, subtype: String, cacheDir: File): File? {
        if (bytes.isEmpty()) return null
        return runCatching {
            val dir = imageDir(cacheDir)
            if (!dir.isDirectory && !dir.mkdirs()) return null
            // Name by content hash: re-sending the same image reuses the same file.
            val hash = MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { "%02x".format(it) }
                .take(16)
            val ext = subtype.lowercase().takeIf { it.all { c -> c.isLetterOrDigit() } } ?: "png"
            val file = File(dir, "img_$hash.$ext")
            if (!file.exists()) file.writeBytes(bytes)
            file
        }.getOrNull()
    }

    private fun imageDir(cacheDir: File): File = File(cacheDir, "llm-images")
}
