package com.ninef.rikkallm.data.ai.cron

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class CronParserTest {

    private val utc = ZoneId.of("UTC")

    private fun LocalDateTime.epoch(): Long = atZone(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun fromEpoch(epochMs: Long): LocalDateTime =
        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMs), ZoneOffset.UTC)

    @Test
    fun `every minute matches the next minute`() {
        val parser = CronParser("* * * * *", utc)
        val from = LocalDateTime.of(2026, 8, 13, 10, 30, 15).epoch()
        val next = parser.nextRunAfter(from)!!
        assertEquals(LocalDateTime.of(2026, 8, 13, 10, 31, 0), fromEpoch(next))
    }

    @Test
    fun `fixed minute and hour fires at next occurrence`() {
        val parser = CronParser("0 10 * * *", utc)
        val from = LocalDateTime.of(2026, 8, 13, 9, 59, 0).epoch()
        val next = parser.nextRunAfter(from)!!
        assertEquals(LocalDateTime.of(2026, 8, 13, 10, 0, 0), fromEpoch(next))
    }

    @Test
    fun `step expression every 15 minutes`() {
        val parser = CronParser("*/15 * * * *", utc)
        val from = LocalDateTime.of(2026, 8, 13, 10, 7, 0).epoch()
        val next = parser.nextRunAfter(from)!!
        assertEquals(LocalDateTime.of(2026, 8, 13, 10, 15, 0), fromEpoch(next))
    }

    @Test
    fun `list expression matches any of the listed minutes`() {
        val parser = CronParser("0,5,10,15 * * * *", utc)
        val from = LocalDateTime.of(2026, 8, 13, 10, 4, 0).epoch()
        val next = parser.nextRunAfter(from)!!
        assertEquals(LocalDateTime.of(2026, 8, 13, 10, 5, 0), fromEpoch(next))
    }

    @Test
    fun `daily 08 00 fires tomorrow when today already passed`() {
        val parser = CronParser("0 8 * * *", utc)
        val from = LocalDateTime.of(2026, 8, 13, 12, 0, 0).epoch()
        val next = parser.nextRunAfter(from)!!
        assertEquals(LocalDateTime.of(2026, 8, 14, 8, 0, 0), fromEpoch(next))
    }

    @Test
    fun `weekly monday 09 00 fires on next monday`() {
        // 2026-08-13 is Thursday
        val parser = CronParser("0 9 * * 1", utc)
        val from = LocalDateTime.of(2026, 8, 13, 10, 0, 0).epoch()
        val next = parser.nextRunAfter(from)!!
        assertEquals(LocalDateTime.of(2026, 8, 17, 9, 0, 0), fromEpoch(next))
    }

    @Test
    fun `month abbreviation is resolved`() {
        // JAN 限制为 1 月：从 2026-01-01 00:00 之后的下一次触发是次年 1 月 1 日
        val parser = CronParser("0 0 1 JAN *", utc)
        val from = LocalDateTime.of(2026, 1, 1, 0, 0, 0).epoch()
        val next = parser.nextRunAfter(from)!!
        assertEquals(LocalDateTime.of(2027, 1, 1, 0, 0, 0), fromEpoch(next))
    }

    @Test
    fun `day of week numeric 0 or 7 both mean sunday`() {
        // 2026-08-16 is Sunday
        val parser = CronParser("0 0 * * 0", utc)
        val from = LocalDateTime.of(2026, 8, 13, 0, 0, 0).epoch()
        val next = parser.nextRunAfter(from)!!
        assertEquals(LocalDateTime.of(2026, 8, 16, 0, 0, 0), fromEpoch(next))
    }

    @Test
    fun `invalid field count is rejected`() {
        assertFalse(CronParser.isValidExpression("0 8 * *"))
        assertFalse(CronParser.isValidExpression("0 8 * * * *"))
    }

    @Test
    fun `out of range values are rejected`() {
        assertFalse(CronParser.isValidExpression("60 8 * * *"))
        assertFalse(CronParser.isValidExpression("0 24 * * *"))
        assertFalse(CronParser.isValidExpression("0 8 32 * *"))
        assertFalse(CronParser.isValidExpression("0 8 * 13 *"))
    }

    @Test
    fun `invalid step is rejected`() {
        assertFalse(CronParser.isValidExpression("*/0 * * * *"))
        assertFalse(CronParser.isValidExpression("*/a * * * *"))
    }

    @Test
    fun `valid expression passes validation`() {
        assertTrue(CronParser.isValidExpression("* * * * *"))
        assertTrue(CronParser.isValidExpression("0 8 * * *"))
        assertTrue(CronParser.isValidExpression("*/15 9-17 * * 1-5"))
        assertTrue(CronParser.isValidExpression("0 0 1 JAN *"))
    }

    @Test
    fun `range expression matches boundaries`() {
        val parser = CronParser("0 9-11 * * *", utc)
        val from = LocalDateTime.of(2026, 8, 13, 9, 30, 0).epoch()
        val next = parser.nextRunAfter(from)!!
        assertEquals(LocalDateTime.of(2026, 8, 13, 10, 0, 0), fromEpoch(next))
    }

    @Test
    fun `day of month restricted without day of week fires on that day`() {
        val parser = CronParser("0 0 13 * *", utc)
        val from = LocalDateTime.of(2026, 8, 1, 0, 0, 0).epoch()
        val next = parser.nextRunAfter(from)!!
        assertEquals(LocalDateTime.of(2026, 8, 13, 0, 0, 0), fromEpoch(next))
    }

    @Test
    fun `never-firing expression returns null`() {
        // Feb 30 never exists
        val parser = CronParser("0 0 30 2 *", utc)
        val from = LocalDateTime.of(2026, 1, 1, 0, 0, 0).epoch()
        assertNull(parser.nextRunAfter(from))
    }
}
