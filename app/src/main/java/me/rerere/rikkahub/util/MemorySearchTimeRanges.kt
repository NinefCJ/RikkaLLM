package me.rerere.rikkahub.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 记忆搜索时间范围：将 ``"last week"`` / ``"本月"`` / ``"last 3 months"`` 等自然语言
 * 解析为 [startMillis, endMillis]（含时区）。同时提供关键词提取，便于把时间词从搜索串中剥离。
 *
 * 移植自 lastchat 分支，仅改包名与 import，并用 java.time 实现（无需引入 kotlinx-datetime 依赖）。
 */
data class MemorySearchTimeRange(
    val startMillis: Long,
    val endMillis: Long,
    val matched: String,
    val remaining: String
) {
    val isInstant: Boolean
        get() = endMillis - startMillis <= 36 * 60 * 60 * 1000L // <=36h 视为“时间点”
}

data class MemorySearchTimeLabel(
    val label: String,
    val startMillis: Long,
    val endMillis: Long,
    val needsRecalculate: Boolean = false
)

private val PAST_RE = Regex("""(?:last|past|previous|recent)\s+(\d+)\s+(second|minute|hour|day|week|month|year)s?""", RegexOption.IGNORE_CASE)
private val THIS_RE = Regex("""\b(this|current|本月|本周|今天|this\s+month|this\s+week|today)\b""", RegexOption.IGNORE_CASE)
private val LAST_UNIT_RE = Regex("""\b(last|previous|上个|上一)\s+(second|minute|hour|day|week|month|year)\b""", RegexOption.IGNORE_CASE)
private val AGO_RE = Regex("""(\d+)\s+(second|minute|hour|day|week|month|year)s?\s+ago""", RegexOption.IGNORE_CASE)

fun parseMemorySearchTimeRange(raw: String?, tz: ZoneId = ZoneId.systemDefault()): MemorySearchTimeRange? {
    if (raw.isNullOrBlank()) return null
    val text = raw.trim()

    PAST_RE.find(text)?.let { m ->
        val n = m.groupValues[1].toInt()
        val unit = m.groupValues[2].lowercase()
        val (start, end) = rangeFromNow(n, unit, tz)
        return MemorySearchTimeRange(start, end, m.value, stripMatched(text, m.value))
    }

    AGO_RE.find(text)?.let { m ->
        val n = m.groupValues[1].toInt()
        val unit = m.groupValues[2].lowercase()
        val (start, end) = rangeFromNow(n, unit, tz)
        return MemorySearchTimeRange(start, end, m.value, stripMatched(text, m.value))
    }

    THIS_RE.find(text)?.let { m ->
        val (start, end) = when {
            m.value.lowercase().contains("week") || m.value == "本周" -> weekRange(tz, 0)
            m.value.lowercase().contains("month") || m.value == "本月" -> monthRange(tz, 0)
            else -> dayRange(tz, 0)
        }
        return MemorySearchTimeRange(start, end, m.value, stripMatched(text, m.value))
    }

    LAST_UNIT_RE.find(text)?.let { m ->
        val unit = m.groupValues[2].lowercase()
        val (start, end) = when (unit) {
            "week" -> weekRange(tz, -1)
            "month" -> monthRange(tz, -1)
            "year" -> yearRange(tz, -1)
            else -> rangeFromNow(1, unit, tz)
        }
        return MemorySearchTimeRange(start, end, m.value, stripMatched(text, m.value))
    }

    return null
}

private fun stripMatched(text: String, matched: String): String =
    text.replace(matched, "", ignoreCase = true).replace(Regex("""\s{2,}"""), " ").trim()

private fun toEpochMs(ldt: LocalDateTime, tz: ZoneId): Long = ldt.atZone(tz).toInstant().toEpochMilli()

internal fun rangeFromNow(n: Int, unit: String, tz: ZoneId): Pair<Long, Long> {
    val now = LocalDateTime.now(tz)
    val end = toEpochMs(now, tz)
    val start = when (unit) {
        "second" -> end - n * 1000L
        "minute" -> end - n * 60_000L
        "hour" -> end - n * 3_600_000L
        "day" -> end - n * 86_400_000L
        "week" -> {
            val monday = now.toLocalDate().with(DayOfWeek.MONDAY).minusWeeks((n - 1).toLong()).atStartOfDay()
            toEpochMs(monday, tz)
        }
        "month" -> {
            val first = now.toLocalDate().withDayOfMonth(1).minusMonths((n - 1).toLong())
            toEpochMs(first.atStartOfDay(), tz)
        }
        "year" -> {
            val first = now.toLocalDate().withDayOfYear(1).minusYears((n - 1).toLong())
            toEpochMs(first.atStartOfDay(), tz)
        }
        else -> end - n * 86_400_000L
    }
    return start to end
}

internal fun dayRange(tz: ZoneId, offsetDays: Int): Pair<Long, Long> {
    val day = LocalDate.now(tz).plusDays(offsetDays.toLong())
    val start = toEpochMs(day.atStartOfDay(), tz)
    val end = toEpochMs(day.atTime(23, 59, 59), tz)
    return start to end
}

internal fun weekRange(tz: ZoneId, offsetWeeks: Int): Pair<Long, Long> {
    val monday = LocalDate.now(tz).with(DayOfWeek.MONDAY).plusWeeks(offsetWeeks.toLong())
    val start = toEpochMs(monday.atStartOfDay(), tz)
    val end = toEpochMs(monday.plusDays(6).atTime(23, 59, 59), tz)
    return start to end
}

internal fun monthRange(tz: ZoneId, offsetMonths: Int): Pair<Long, Long> {
    val first = LocalDate.now(tz).withDayOfMonth(1).plusMonths(offsetMonths.toLong())
    val start = toEpochMs(first.atStartOfDay(), tz)
    val next = first.plusMonths(1).minusDays(1)
    val end = toEpochMs(next.atTime(23, 59, 59), tz)
    return start to end
}

internal fun yearRange(tz: ZoneId, offsetYears: Int): Pair<Long, Long> {
    val first = LocalDate.now(tz).withDayOfYear(1).plusYears(offsetYears.toLong())
    val start = toEpochMs(first.atStartOfDay(), tz)
    val end = toEpochMs(first.plusYears(1).minusDays(1).atTime(23, 59, 59), tz)
    return start to end
}
