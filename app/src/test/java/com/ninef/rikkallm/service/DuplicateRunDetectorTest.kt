package com.ninef.rikkallm.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证 Phase 3 的异常重复输出检测（移植自 Bubble-RikkaHub 的 hasDuplicateRun）。
 */
class DuplicateRunDetectorTest {

    @Test
    fun shortTextNeverTriggers() {
        assertFalse(hasDuplicateRun("hello"))
    }

    @Test
    fun normalTextWithoutLongRunDoesNotTrigger() {
        assertFalse(hasDuplicateRun("the quick brown fox jumps over the lazy dog ".repeat(3)))
    }

    @Test
    fun exactlyThresholdIdenticalCharsTriggers() {
        assertTrue(hasDuplicateRun("a".repeat(DUPLICATE_RUN_THRESHOLD)))
    }

    @Test
    fun belowThresholdIdenticalCharsDoesNotTrigger() {
        assertFalse(hasDuplicateRun("a".repeat(DUPLICATE_RUN_THRESHOLD - 1)))
    }

    @Test
    fun runInTheMiddleTriggers() {
        assertTrue(hasDuplicateRun("prefix" + "x".repeat(DUPLICATE_RUN_THRESHOLD) + "suffix"))
    }

    @Test
    fun alternatingCharsDoNotTrigger() {
        assertFalse(hasDuplicateRun("ab".repeat(DUPLICATE_RUN_THRESHOLD)))
    }

    @Test
    fun customThresholdIsRespected() {
        assertTrue(hasDuplicateRun("z".repeat(10), threshold = 10))
        assertFalse(hasDuplicateRun("z".repeat(9), threshold = 10))
    }
}
