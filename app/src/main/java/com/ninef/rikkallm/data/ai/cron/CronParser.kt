package com.ninef.rikkallm.data.ai.cron

import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 标准 5 段 cron 表达式解析器（分 时 日 月 周）。
 *
 * 支持语法：
 * - `*`：任意值
 * - `*&#47;n`：步长（如 `*&#47;15` 每 15 分钟）
 * - `1,2,3`：枚举
 * - `1-5`：范围
 * - 固定值：如 `0`、`8`
 * - 月/周支持英文缩写（`JAN`、`MON` 等，大小写不敏感）
 *
 * 计算下一次触发时间基于 [ZoneId.systemDefault] 本地时区，纯 JVM 可测。
 */
class CronParser(
    private val expression: String,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    private val minutes: IntSet
    private val hours: IntSet
    private val daysOfMonth: IntSet
    private val months: IntSet
    private val daysOfWeek: IntSet

    init {
        val fields = expression.split(WHITESPACE).filter { it.isNotBlank() }
        require(fields.size == 5) {
            "cron 表达式必须包含 5 段（分 时 日 月 周），当前为 ${fields.size} 段: '$expression'"
        }
        minutes = parseField(fields[0], 0, 59, MONTH_ABBR, WEEK_ABBR, "分钟")
        hours = parseField(fields[1], 0, 23, MONTH_ABBR, WEEK_ABBR, "小时")
        daysOfMonth = parseField(fields[2], 1, 31, MONTH_ABBR, WEEK_ABBR, "日")
        months = parseField(fields[3], 1, 12, MONTH_ABBR, WEEK_ABBR, "月")
        daysOfWeek = parseField(fields[4], 0, 7, MONTH_ABBR, WEEK_ABBR, "周")
    }

    /** 计算 [fromEpochMs]（含）之后的下一次触发时间；若无法触发返回 null */
    fun nextRunAfter(fromEpochMs: Long): Long? {
        var candidate = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(fromEpochMs),
            zoneId,
        ).truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
        val upperBound = candidate.plusYears(5)

        while (candidate.isBefore(upperBound)) {
            if (matches(candidate)) {
                return candidate.atZone(zoneId).toInstant().toEpochMilli()
            }
            candidate = candidate.plusMinutes(1)
        }
        return null
    }

    private fun matches(dt: LocalDateTime): Boolean {
        if (!minutes.contains(dt.minute)) return false
        if (!hours.contains(dt.hour)) return false
        if (!months.contains(dt.monthValue)) return false
        if (!matchesDayOfMonthOrWeek(dt)) return false
        return true
    }

    /**
     * 日字段语义：与标准 cron 一致——
     * 当"日"与"周"都为受限集（非全匹配）时，两者取并集；否则取交集。
     */
    private fun matchesDayOfMonthOrWeek(dt: LocalDateTime): Boolean {
        val domMatches = daysOfMonth.contains(dt.dayOfMonth)
        val dowMatches = daysOfWeek.contains(dt.dayOfWeek.value % 7)

        val domRestricted = !daysOfMonth.isFull
        val dowRestricted = !daysOfWeek.isFull

        return when {
            domRestricted && dowRestricted -> domMatches || dowMatches
            domRestricted -> domMatches
            dowRestricted -> dowMatches
            else -> true
        }
    }

    private class IntSet(
        val values: Set<Int>,
        val isFull: Boolean,
    ) {
        fun contains(value: Int): Boolean = values.contains(value)
    }

    companion object {
        private val WHITESPACE = Regex("\\s+")
        private val MONTH_ABBR = mapOf(
            "JAN" to 1, "FEB" to 2, "MAR" to 3, "APR" to 4,
            "MAY" to 5, "JUN" to 6, "JUL" to 7, "AUG" to 8,
            "SEP" to 9, "OCT" to 10, "NOV" to 11, "DEC" to 12,
        )
        private val WEEK_ABBR = mapOf(
            "SUN" to 0, "MON" to 1, "TUE" to 2, "WED" to 3,
            "THU" to 4, "FRI" to 5, "SAT" to 6,
        )

        fun isValidExpression(expression: String): Boolean = runCatching {
            val fields = expression.split(WHITESPACE).filter { it.isNotBlank() }
            check(fields.size == 5) { "必须 5 段" }
            parseField(fields[0], 0, 59, MONTH_ABBR, WEEK_ABBR, "分钟")
            parseField(fields[1], 0, 23, MONTH_ABBR, WEEK_ABBR, "小时")
            parseField(fields[2], 1, 31, MONTH_ABBR, WEEK_ABBR, "日")
            parseField(fields[3], 1, 12, MONTH_ABBR, WEEK_ABBR, "月")
            parseField(fields[4], 0, 7, MONTH_ABBR, WEEK_ABBR, "周")
        }.isSuccess

        private fun parseField(
            field: String,
            min: Int,
            max: Int,
            monthAbbr: Map<String, Int>,
            weekAbbr: Map<String, Int>,
            label: String,
        ): IntSet {
            if (field == "*") return IntSet((min..max).toSet(), true)

            val values = LinkedHashSet<Int>()
            field.split(',').forEach { segment ->
                if (segment.isBlank()) throw DateTimeException("$label 段含空项: '$field'")
                val (rangePart, stepPart) = segment.split('/', limit = 2).let {
                    it[0] to it.getOrNull(1)
                }
                val step = stepPart?.toIntOrNull()?.takeIf { it > 0 }
                    ?: if (stepPart != null) throw DateTimeException("$label 步长非法: '$stepPart'") else 1

                val (start, end) = when {
                    rangePart == "*" -> min to max
                    rangePart.contains('-') -> {
                        val parts = rangePart.split('-')
                        require(parts.size == 2) { "$label 范围非法: '$rangePart'" }
                        resolve(parts[0].trim(), min, max, monthAbbr, weekAbbr, label) to
                            resolve(parts[1].trim(), min, max, monthAbbr, weekAbbr, label)
                    }
                    else -> {
                        val v = resolve(rangePart, min, max, monthAbbr, weekAbbr, label)
                        v to v
                    }
                }

                var current = start
                while (current <= end) {
                    values.add(current)
                    current += step
                }
            }
            return IntSet(values, values.size == max - min + 1)
        }

        private fun resolve(
            token: String,
            min: Int,
            max: Int,
            monthAbbr: Map<String, Int>,
            weekAbbr: Map<String, Int>,
            label: String,
        ): Int {
            val upper = token.uppercase()
            val value = when {
                monthAbbr.containsKey(upper) -> monthAbbr.getValue(upper)
                weekAbbr.containsKey(upper) -> weekAbbr.getValue(upper)
                else -> token.toIntOrNull()
                    ?: throw DateTimeException("$label 值非法: '$token'")
            }
            if (value < min || value > max) {
                throw DateTimeException("$label 值 $value 超出范围 [$min, $max]")
            }
            return value
        }
    }
}
