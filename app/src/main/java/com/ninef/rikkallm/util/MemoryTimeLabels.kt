package com.ninef.rikkallm.util

import java.time.ZoneId

/**
 * 把记忆按“时间标签”归类，供 UI 展示与检索。
 * 移植自 lastchat 分支，仅改包名与 import，并用 java.time 实现。
 */
object MemoryTimeLabels {
    fun now(): String = "now"

    fun getKnownRange(raw: String?, tz: ZoneId = ZoneId.systemDefault()): MemorySearchTimeRange? =
        parseMemorySearchTimeRange(raw, tz)

    /** 当前可展示的时间标签（相对当前时刻） */
    fun currentLabels(tz: ZoneId = ZoneId.systemDefault()): List<MemorySearchTimeLabel> {
        fun lbl(label: String, range: Pair<Long, Long>) =
            MemorySearchTimeLabel(label, range.first, range.second)
        return listOf(
            lbl("今天", dayRange(tz, 0)),
            lbl("昨天", dayRange(tz, -1)),
            lbl("本周", weekRange(tz, 0)),
            lbl("上周", weekRange(tz, -1)),
            lbl("本月", monthRange(tz, 0)),
            lbl("上月", monthRange(tz, -1)),
            lbl("今年", yearRange(tz, 0)),
            lbl("去年", yearRange(tz, -1)),
        )
    }

    /** 把时间戳映射到若干标签名（用于展示“这条记忆属于哪段时间”） */
    fun labelsForTs(tsMillis: Long, tz: ZoneId = ZoneId.systemDefault()): List<String> {
        val labels = currentLabels(tz)
        return labels.filter { tsMillis in it.startMillis..it.endMillis }.map { it.label }
    }
}
