// Minimal Jinja2-subset renderer for GGUF `tokenizer.chat_template` metadata.
//
// llama.cpp stores each model's official chat template as a Jinja string in the GGUF header
// (ChatML, Llama-3, Mistral, Qwen, Gemma, Phi-3, ...). Instead of hardcoding a generic
// instruction template, we apply the model's own template so instruction-tuned models get
// the exact formatting they were trained with. The renderer implements the subset of Jinja2
// that covers ~all shipping GGUF templates: `{{ expr }}`, `{% for %}`/`{% if %}`/`{% set %}`,
// whitespace control (`{%-`/`-%}`), member access, function calls and a practical set of
// filters (trim, lower, replace, join, tojson, trim_messages, ...).
//
// It is intentionally conservative: any syntax it cannot handle makes render() return null so
// the caller can fall back to its generic template. Pure JVM — unit-testable without a model.

package com.alibaba.mnnllm.android.server

object ChatTemplateRenderer {

    data class Message(
        val role: String,
        val content: String?,
        val name: String? = null,
        val toolCalls: List<Map<String, Any?>> = emptyList(),
    )

    /**
     * Renders [template] against [messages]. Returns null when the template is unsupported
     * or evaluation fails, so callers can fall back to a generic prompt format.
     *
     * @param addGenerationPrompt when true, an empty assistant turn is appended (llama.cpp
     * semantics) so `{% if add_generation_prompt %}` branches emit their generation prefix.
     */
    fun render(
        template: String,
        messages: List<Message>,
        addGenerationPrompt: Boolean = true,
        bosToken: String = "",
        eosToken: String = "",
    ): String? {
        val tokens = try {
            tokenize(template)
        } catch (_: Exception) {
            return null
        }
        val ctx = Ctx()
        ctx.global["add_generation_prompt"] = addGenerationPrompt
        ctx.global["bos_token"] = bosToken
        ctx.global["eos_token"] = eosToken
        val effective = if (addGenerationPrompt) messages + Message("assistant", null) else messages
        ctx.global["messages"] = effective
        return try {
            val sb = StringBuilder()
            val next = renderNodes(tokens, 0, tokens.size, ctx, sb)
            if (next >= 0) sb.toString() else null
        } catch (_: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------
    // tokens
    // ------------------------------------------------------------------

    private sealed class Token {
        class Text(val value: String) : Token()
        class Expr(val code: String, val lstrip: Boolean) : Token()
        class Stmt(val code: String, val lstrip: Boolean) : Token()
    }

    private fun tokenize(t: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val sb = StringBuilder()
        fun flush() {
            if (sb.isNotEmpty()) {
                tokens.add(Token.Text(sb.toString()))
                sb.setLength(0)
            }
        }
        var i = 0
        while (i < t.length) {
            val c = t[i]
            if (c == '{' && i + 1 < t.length) {
                val n = t[i + 1]
                val close = when (n) {
                    '{' -> "}}"
                    '%' -> "%}"
                    '#' -> "#}"
                    else -> null
                }
                if (close != null) {
                    val end = t.indexOf(close, i + 2)
                    if (end < 0) {
                        // Unclosed tag: treat the whole template as malformed (matches Jinja).
                        throw IllegalArgumentException("Unclosed template tag at $i")
                    }
                    var body = t.substring(i + 2, end)
                    val lstrip = body.startsWith("-")
                    if (lstrip) body = body.substring(1)
                    val rstrip = body.endsWith("-")
                    if (rstrip) body = body.substring(0, body.length - 1)
                    flush()
                    when (n) {
                        '{' -> tokens.add(Token.Expr(body.trim(), lstrip))
                        '%' -> tokens.add(Token.Stmt(body.trim(), lstrip))
                        // '#' — comment: drop entirely.
                    }
                    var j = end + close.length
                    if (rstrip) {
                        while (j < t.length && t[j].isWhitespace()) j++
                    }
                    i = j
                    continue
                }
            }
            sb.append(c)
            i++
        }
        flush()
        return tokens
    }

    // ------------------------------------------------------------------
    // rendering
    // ------------------------------------------------------------------

    private class Ctx {
        val global = mutableMapOf<String, Any?>()
        val local = mutableMapOf<String, Any?>()

        fun lookup(name: String): Any? =
            local[name] ?: global[name]
    }

    /**
     * Renders tokens[from, to). Returns the index right after the consumed range
     * (for `for`/`if` blocks), or -1 when the template is unsupported.
     */
    private fun renderNodes(tokens: List<Token>, from: Int, to: Int, ctx: Ctx, sb: StringBuilder): Int {
        var i = from
        while (i < to) {
            when (val tk = tokens[i]) {
                is Token.Text -> sb.append(tk.value)
                is Token.Expr -> {
                    if (tk.lstrip) stripTrailingWs(sb)
                    sb.append(stringify(eval(tk.code, ctx)))
                }
                is Token.Stmt -> {
                    if (tk.lstrip) stripTrailingWs(sb)
                    val code = tk.code
                    when {
                        code == "endfor" || code == "endif" || code == "else" || code.startsWith("elif ") ->
                            return i // let the enclosing for/if block consume it

                        code.startsWith("for ") -> {
                            val r = renderFor(tokens, i, to, ctx, sb)
                            if (r < 0) return -1
                            i = r
                            continue
                        }
                        code.startsWith("if ") -> {
                            val r = renderIf(tokens, i, to, ctx, sb)
                            if (r < 0) return -1
                            i = r
                            continue
                        }
                        code.startsWith("set ") -> {
                            applySet(code.removePrefix("set "), ctx)
                        }
                        else -> return -1 // unsupported statement
                    }
                }
            }
            i++
        }
        return i
    }

    private fun stripTrailingWs(sb: StringBuilder) {
        while (sb.isNotEmpty() && sb.last().isWhitespace()) sb.setLength(sb.length - 1)
    }

    private fun renderFor(tokens: List<Token>, forIdx: Int, to: Int, ctx: Ctx, sb: StringBuilder): Int {
        val code = tokens[forIdx].let { (it as Token.Stmt).code }
        val m = FOR_PATTERN.matchEntire(code) ?: return -1
        val varName = m.groupValues[1].trim()
        val list = eval(m.groupValues[2].trim(), ctx) as? List<*> ?: return -1
        val endIdx = findBlockEnd(tokens, forIdx, to, "endfor") ?: return -1
        val endLstrip = (tokens[endIdx] as Token.Stmt).lstrip
        val saved = ctx.local[varName]
        var idx = 0
        for (item in list) {
            ctx.local[varName] = item
            ctx.local["loop"] = mapOf(
                "index0" to idx.toLong(),
                "index" to (idx + 1).toLong(),
                "first" to (idx == 0),
                "last" to (idx == list.size - 1),
                "length" to list.size.toLong(),
            )
            val r = renderNodes(tokens, forIdx + 1, endIdx, ctx, sb)
            if (r < 0) return -1
            if (endLstrip) stripTrailingWs(sb)
            idx++
        }
        if (saved === undefinedMarker) ctx.local.remove(varName) else ctx.local[varName] = saved
        ctx.local.remove("loop")
        return endIdx + 1
    }

    private fun renderIf(tokens: List<Token>, ifIdx: Int, to: Int, ctx: Ctx, sb: StringBuilder): Int {
        // Split the if..endif region into (condition, bodyStart, bodyEnd) branches.
        val branches = mutableListOf<Triple<String?, Int, Int>>()
        var cond: String? = (tokens[ifIdx] as Token.Stmt).code.removePrefix("if ").trim()
        var bodyStart = ifIdx + 1
        var depth = 1
        var endIdx = -1
        var j = ifIdx + 1
        while (j < to) {
            val tk = tokens[j]
            if (tk is Token.Stmt) {
                when {
                    tk.code.startsWith("for ") || tk.code.startsWith("if ") -> depth++
                    tk.code == "endfor" -> depth--
                    tk.code == "endif" -> {
                        depth--
                        if (depth == 0) {
                            branches.add(Triple(cond, bodyStart, j))
                            endIdx = j
                            break
                        }
                    }
                    (tk.code == "else" || tk.code.startsWith("elif ")) && depth == 1 -> {
                        branches.add(Triple(cond, bodyStart, j))
                        cond = if (tk.code == "else") null else tk.code.removePrefix("elif ").trim()
                        bodyStart = j + 1
                    }
                }
            }
            j++
        }
        if (endIdx < 0) return -1
        val endLstrip = (tokens[endIdx] as Token.Stmt).lstrip
        for ((c, s, e) in branches) {
            if (c == null || truthy(eval(c, ctx))) {
                val r = renderNodes(tokens, s, e, ctx, sb)
                if (r < 0) return -1
                if (endLstrip) stripTrailingWs(sb)
                return endIdx + 1
            }
        }
        if (endLstrip) stripTrailingWs(sb)
        return endIdx + 1
    }

    private fun applySet(body: String, ctx: Ctx) {
        val eq = body.indexOf('=')
        if (eq < 0) return
        val name = body.substring(0, eq).trim()
        val value = eval(body.substring(eq + 1).trim(), ctx)
        ctx.local[name] = value
    }

    /** Finds the matching endfor/endif for the block starting at blockStart (which is a for/if). */
    private fun findBlockEnd(tokens: List<Token>, blockStart: Int, to: Int, endCode: String): Int? {
        var depth = 1
        var j = blockStart + 1
        while (j < to) {
            val tk = tokens[j]
            if (tk is Token.Stmt) {
                when {
                    tk.code.startsWith("for ") || tk.code.startsWith("if ") -> depth++
                    tk.code == "endfor" || tk.code == "endif" -> {
                        depth--
                        if (depth == 0) {
                            if (tk.code == endCode) return j else return null
                        }
                    }
                }
            }
            j++
        }
        return null
    }

    // ------------------------------------------------------------------
    // expression evaluation (recursive descent)
    // ------------------------------------------------------------------

    private val FOR_PATTERN = Regex("""^for\s+(.+?)\s+in\s+(.+)$""")

    private val undefinedMarker = Any()

    private fun eval(expr: String, ctx: Ctx): Any? {
        val p = Parser(expr, ctx)
        val v = p.parseOr()
        p.skipWs()
        return v
    }

    private fun truthy(v: Any?): Boolean = when (v) {
        null -> false
        is Boolean -> v
        is String -> v.isNotEmpty()
        is Number -> v.toDouble() != 0.0
        is List<*> -> v.isNotEmpty()
        is Map<*, *> -> v.isNotEmpty()
        else -> true
    }

    private fun stringify(v: Any?): String = when (v) {
        null -> ""
        is String -> v
        is Boolean -> if (v) "true" else "false"
        is Double -> if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
        is Number -> v.toString()
        is List<*> -> v.joinToString(", ") { stringify(it) }
        else -> v.toString()
    }

    private fun getAttr(v: Any?, name: String): Any? = when (v) {
        is Message -> when (name) {
            "role" -> v.role
            "content" -> v.content
            "name" -> v.name
            "tool_calls" -> v.toolCalls
            else -> null
        }
        is Map<*, *> -> v[name]
        is Ctx -> v.lookup(name)
        else -> null
    }

    private fun getIndex(v: Any?, idx: Any?): Any? = when (v) {
        is List<*> -> (idx as? Number)?.toInt()?.let { if (it in v.indices) v[it] else null }
        is Map<*, *> -> v[idx]
        is String -> (idx as? Number)?.toInt()?.let { if (it in v.indices) v[it].toString() else null }
        else -> null
    }

    private fun callFunction(name: String, args: List<Any?>): Any? = when (name) {
        "range" -> (args.firstOrNull() as? Number)?.toInt()?.let { (0 until it).map { n -> n.toLong() } }
        "namespace" -> mapOf<String, Any?>()
        else -> null
    }

    private fun splitTopLevel(s: String): List<String> {
        val parts = mutableListOf<String>()
        val sb = StringBuilder()
        var depth = 0
        var quote: Char? = null
        for (c in s) {
            when {
                quote != null -> {
                    sb.append(c)
                    if (c == quote && sb.length > 1 && sb[sb.length - 2] != '\\') quote = null
                }
                c == '\'' || c == '"' -> {
                    quote = c
                    sb.append(c)
                }
                c == '(' || c == '[' -> {
                    depth++
                    sb.append(c)
                }
                c == ')' || c == ']' -> {
                    depth--
                    sb.append(c)
                }
                c == ',' && depth == 0 -> {
                    parts.add(sb.toString().trim())
                    sb.setLength(0)
                }
                else -> sb.append(c)
            }
        }
        if (sb.isNotEmpty()) parts.add(sb.toString().trim())
        return parts
    }

    private fun applyFilter(value: Any?, name: String, args: List<Any?>): Any? = when (name) {
        "trim", "strip" -> value?.toString()?.trim()
        "lstrip" -> value?.toString()?.trimStart()
        "rstrip" -> value?.toString()?.trimEnd()
        "lower" -> value?.toString()?.lowercase()
        "upper" -> value?.toString()?.uppercase()
        "capitalize" -> value?.toString()?.replaceFirstChar { it.uppercase() }
        "title" -> value?.toString()?.split(' ')?.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        "length", "count" -> when (value) {
            is List<*> -> value.size.toLong()
            is Map<*, *> -> value.size.toLong()
            is String -> value.length.toLong()
            else -> null
        }
        "string" -> stringify(value)
        "int" -> (value as? Number)?.toLong() ?: value?.toString()?.trim()?.toDoubleOrNull()?.toLong()
        "float" -> (value as? Number)?.toDouble() ?: value?.toString()?.trim()?.toDoubleOrNull()
        "default" -> {
            val useBoolean = args.size > 1 && truthy(args[1])
            when {
                value === undefinedMarker || value == null -> args.firstOrNull()
                useBoolean && !truthy(value) -> args.firstOrNull()
                else -> value
            }
        }
        "first" -> (value as? List<*>)?.firstOrNull()
        "last" -> (value as? List<*>)?.lastOrNull()
        "join" -> (value as? List<*>)
            ?.joinToString(args.firstOrNull()?.toString() ?: "") { stringify(it) }
        "replace" -> {
            if (args.size >= 2) value?.toString()?.replace(args[0].toString(), args[1].toString()) else null
        }
        "escape", "e" -> value?.toString()?.let { v ->
            v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;")
        }
        "safe" -> value
        "tojson", "to_json" -> toJson(value)
        "trim_messages" -> trimMessages(value)
        else -> null
    }

    /** Approximates llama.cpp's trim_messages: drop trailing empty turns, keep the first. */
    private fun trimMessages(v: Any?): Any? {
        val list = v as? List<*> ?: return v
        if (list.isEmpty()) return list
        val lastIdx = list.indexOfLast {
            val msg = it as? Message ?: return@indexOfLast true
            msg.content?.isNotBlank() == true
        }
        return if (lastIdx <= 0) list.take(1) else list.subList(0, lastIdx + 1)
    }

    private fun toJson(v: Any?): String = when (v) {
        null -> "null"
        is String -> buildString {
            append('"')
            for (c in v) when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
            append('"')
        }
        is Boolean, is Number -> v.toString()
        is Map<*, *> -> v.entries.joinToString(",", "{", "}") { (k, vv) -> "${toJson(k.toString())}:${toJson(vv)}" }
        is List<*> -> v.joinToString(",", "[", "]") { toJson(it) }
        is Message -> toJson(mapOf("role" to v.role, "content" to v.content))
        else -> "\"$v\""
    }

    private class Parser(val s: String, val ctx: Ctx) {
        var pos = 0

        fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        fun peek(): Char = if (pos < s.length) s[pos] else '\u0000'

        fun match(ch: Char): Boolean {
            skipWs()
            if (pos < s.length && s[pos] == ch) {
                pos++
                return true
            }
            return false
        }

        fun matchWord(w: String): Boolean {
            skipWs()
            if (s.startsWith(w, pos) &&
                (pos + w.length >= s.length || !s[pos + w.length].isLetterOrDigit())
            ) {
                pos += w.length
                return true
            }
            return false
        }

        fun parseOr(): Any? {
            var v = parseAnd()
            while (true) {
                if (matchWord("or")) {
                    val r = parseAnd()
                    v = truthy(v) || truthy(r)
                } else break
            }
            return v
        }

        fun parseAnd(): Any? {
            var v = parseComparison()
            while (true) {
                if (matchWord("and")) {
                    val r = parseComparison()
                    v = truthy(v) && truthy(r)
                } else break
            }
            return v
        }

        fun parseComparison(): Any? {
            var v = parseAdditive()
            while (true) {
                skipWs()
                val op = when {
                    s.startsWith("==", pos) -> { pos += 2; "==" }
                    s.startsWith("!=", pos) || s.startsWith("<>", pos) -> { pos += 2; "!=" }
                    s.startsWith(">=", pos) -> { pos += 2; ">=" }
                    s.startsWith("<=", pos) -> { pos += 2; "<=" }
                    s.startsWith(">", pos) -> { pos += 1; ">" }
                    s.startsWith("<", pos) -> { pos += 1; "<" }
                    else -> null
                }
                if (op == null) break
                val r = parseAdditive()
                v = when (op) {
                    "==" -> eq(v, r)
                    "!=" -> !eq(v, r)
                    ">=" -> cmp(v, r)?.let { it >= 0 }
                    "<=" -> cmp(v, r)?.let { it <= 0 }
                    ">" -> cmp(v, r)?.let { it > 0 }
                    "<" -> cmp(v, r)?.let { it < 0 }
                    else -> null
                }
            }
            return v
        }

        fun parseAdditive(): Any? {
            var v = parseUnary()
            while (true) {
                skipWs()
                when {
                    s.startsWith("+", pos) -> {
                        pos++
                        val r = parseUnary()
                        v = if (v is String || r is String) stringify(v) + stringify(r) else (num(v) ?: 0.0) + (num(r) ?: 0.0)
                    }
                    s.startsWith("-", pos) -> {
                        pos++
                        v = (num(v) ?: 0.0) - (num(parseUnary()) ?: 0.0)
                    }
                    else -> break
                }
            }
            return v
        }

        fun parseUnary(): Any? {
            skipWs()
            if (matchWord("not")) return !truthy(parseUnary())
            if (peek() == '-') {
                pos++
                val v = parseUnary()
                return (num(v) ?: 0.0) * -1.0
            }
            // Filter chain: primary | filter(args) | ...
            var v = parsePostfix()
            while (true) {
                skipWs()
                if (peek() == '|') {
                    pos++
                    skipWs()
                    val fname = parseIdent()
                    var args = emptyList<Any?>()
                    if (peek() == '(') {
                        pos++
                        val a = mutableListOf<Any?>()
                        skipWs()
                        if (peek() != ')') {
                            a.add(parseOr())
                            while (match(',')) a.add(parseOr())
                        }
                        match(')')
                        args = a
                    }
                    v = applyFilter(v, fname, args)
                    if (v === null && fname !in NULLABLE_FILTERS) return null
                } else break
            }
            return v
        }

        fun parsePostfix(): Any? {
            val base = parsePrimary()
            var v = base
            while (true) {
                skipWs()
                when {
                    peek() == '.' -> {
                        pos++
                        v = getAttr(v, parseIdent())
                    }
                    peek() == '[' -> {
                        pos++
                        val idx = parseOr()
                        match(']')
                        v = getIndex(v, idx)
                    }
                    else -> break
                }
            }
            return v
        }

        fun parsePrimary(): Any? {
            skipWs()
            return when (peek()) {
                '(' -> {
                    pos++
                    val v = parseOr()
                    match(')')
                    v
                }
                '[' -> {
                    pos++
                    val items = mutableListOf<Any?>()
                    skipWs()
                    if (peek() != ']') {
                        items.add(parseOr())
                        while (match(',')) items.add(parseOr())
                    }
                    match(']')
                    items
                }
                '\'', '"' -> parseString()
                else -> {
                    if (peek() == '-' && pos + 1 < s.length && s[pos + 1].isDigit()) return parseNumber()
                    if (peek().isDigit()) return parseNumber()
                    if (peek().isLetter() || peek() == '_') {
                        val name = parseIdent()
                        return when (name) {
                            "true" -> true
                            "false" -> false
                            "null", "none", "None", "True", "False" -> if (name.equals("true", true)) true else if (name.equals("false", true)) false else null
                            else -> {
                                skipWs()
                                if (peek() == '(') {
                                    pos++
                                    val args = mutableListOf<Any?>()
                                    skipWs()
                                    if (peek() != ')') {
                                        args.add(parseOr())
                                        while (match(',')) args.add(parseOr())
                                    }
                                    match(')')
                                    callFunction(name, args)
                                } else {
                                    ctx.lookup(name) ?: undefinedMarker
                                }
                            }
                        }
                    }
                    null
                }
            }
        }

        fun parseIdent(): String {
            skipWs()
            val start = pos
            while (pos < s.length && (s[pos].isLetterOrDigit() || s[pos] == '_')) pos++
            return s.substring(start, pos)
        }

        fun parseString(): String {
            val quote = s[pos++]
            val sb = StringBuilder()
            while (pos < s.length) {
                val c = s[pos++]
                when {
                    c == quote -> break
                    c == '\\' && pos < s.length -> {
                        val e = s[pos++]
                        sb.append(
                            when (e) {
                                'n' -> '\n'
                                't' -> '\t'
                                'r' -> '\r'
                                '\\' -> '\\'
                                '\'' -> '\''
                                '"' -> '"'
                                else -> e
                            }
                        )
                    }
                    else -> sb.append(c)
                }
            }
            return sb.toString()
        }

        fun parseNumber(): Any? {
            skipWs()
            val start = pos
            if (peek() == '-') pos++
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.' || s[pos] == 'e' || s[pos] == 'E' || s[pos] == '+' || s[pos] == '-')) {
                // A trailing sign only makes sense right after an exponent marker.
                if ((s[pos] == '+' || s[pos] == '-') && pos > start && s[pos - 1] !in "eE") break
                pos++
            }
            val text = s.substring(start, pos)
            return text.toLongOrNull() ?: text.toDoubleOrNull()
        }
    }

    private fun eq(a: Any?, b: Any?): Boolean {
        if (a == null || b == null) return a == b
        if (a is Number && b is Number) return a.toDouble() == b.toDouble()
        if (a is Number || b is Number) return num(a) != null && num(b) != null && num(a) == num(b)
        return a.toString() == b.toString()
    }

    private fun cmp(a: Any?, b: Any?): Int? {
        if (a is Number && b is Number) return a.toDouble().compareTo(b.toDouble())
        if (a is String && b is String) return a.compareTo(b)
        return null
    }

    private fun num(v: Any?): Double? = when (v) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull()
        else -> null
    }

    private val NULLABLE_FILTERS = setOf(
        "first", "last", "length", "count", "int", "float", "replace",
        "tojson", "to_json", "default",
    )
}
