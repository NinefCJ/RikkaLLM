package com.ninef.rikkallm.data.ai.generativeui

import me.rerere.ai.ui.GenerativeCardData
import me.rerere.ai.ui.GenerativeCardItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GenerativeUiSanitizerTest {

    private fun validCard() = GenerativeCardData(
        kind = "search_result",
        title = "Results",
        items = listOf(
            GenerativeCardItem(label = "a", value = "b"),
        ),
    )

    @Test
    fun `valid card passes through unchanged`() {
        val result = GenerativeUiSanitizer.sanitize(validCard())
        assertNotNull(result)
        assertEquals("search_result", result!!.kind)
        assertEquals("Results", result.title)
    }

    @Test
    fun `https and http links are kept`() {
        val card = GenerativeCardData(
            kind = "card",
            title = "t",
            items = listOf(
                GenerativeCardItem(label = "a", value = "b", link = "https://example.com"),
                GenerativeCardItem(label = "c", value = "d", link = "http://example.org/x"),
            ),
        )
        val result = GenerativeUiSanitizer.sanitize(card)
        assertNotNull(result)
        assertEquals("https://example.com", result!!.items[0].link)
        assertEquals("http://example.org/x", result.items[1].link)
    }

    @Test
    fun `javascript link is stripped`() {
        val card = GenerativeCardData(
            kind = "card",
            title = "t",
            items = listOf(
                GenerativeCardItem(label = "a", value = "b", link = "javascript:alert(1)"),
                GenerativeCardItem(label = "c", value = "d"),
            ),
        )
        val result = GenerativeUiSanitizer.sanitize(card)
        assertNotNull(result)
        assertNull(result!!.items[0].link)
        assertEquals("c", result.items[1].label)
    }

    @Test
    fun `data scheme link is stripped`() {
        val card = GenerativeCardData(
            kind = "card",
            title = "t",
            items = listOf(
                GenerativeCardItem(label = "a", value = "b", link = "data:text/html;base64,PHNjcmlwdD4="),
            ),
        )
        val result = GenerativeUiSanitizer.sanitize(card)
        assertNotNull(result)
        assertNull(result!!.items[0].link)
    }

    @Test
    fun `overlong title is rejected`() {
        val card = GenerativeCardData(
            kind = "card",
            title = "x".repeat(GenerativeUiSanitizer.MAX_TITLE_LENGTH + 1),
        )
        assertNull(GenerativeUiSanitizer.sanitize(card))
    }

    @Test
    fun `overlong value is rejected for the whole item`() {
        val card = GenerativeCardData(
            kind = "card",
            title = "t",
            items = listOf(
                GenerativeCardItem(label = "a", value = "v".repeat(GenerativeUiSanitizer.MAX_ITEM_VALUE_LENGTH + 1)),
            ),
        )
        // 唯一条目非法 -> 整卡非法
        assertNull(GenerativeUiSanitizer.sanitize(card))
    }

    @Test
    fun `too many items are rejected`() {
        val card = GenerativeCardData(
            kind = "card",
            title = "t",
            items = (0 until GenerativeUiSanitizer.MAX_ITEMS + 1).map {
                GenerativeCardItem(label = "l$it", value = "v")
            },
        )
        assertNull(GenerativeUiSanitizer.sanitize(card))
    }

    @Test
    fun `empty card without title or items is rejected`() {
        assertNull(GenerativeUiSanitizer.sanitize(GenerativeCardData(kind = "card")))
    }

    @Test
    fun `all-invalid items reject the whole card`() {
        val card = GenerativeCardData(
            kind = "card",
            title = "t",
            items = listOf(
                GenerativeCardItem(label = "", value = ""),
                GenerativeCardItem(label = "x".repeat(GenerativeUiSanitizer.MAX_ITEM_LABEL_LENGTH + 1), value = "v"),
            ),
        )
        assertNull(GenerativeUiSanitizer.sanitize(card))
    }

    @Test
    fun `blank strings are trimmed and empty subtitle dropped`() {
        val card = GenerativeCardData(
            kind = "status",
            title = "  Title  ",
            subtitle = "   ",
            footer = "  done  ",
            items = listOf(GenerativeCardItem(label = " a ", value = " b ")),
        )
        val result = GenerativeUiSanitizer.sanitize(card)
        assertNotNull(result)
        assertEquals("Title", result!!.title)
        assertEquals(null, result.subtitle)
        assertEquals("done", result.footer)
        assertEquals("a", result.items[0].label)
    }

    @Test
    fun `unknown kind longer than limit is rejected`() {
        val card = GenerativeCardData(
            kind = "x".repeat(GenerativeUiSanitizer.MAX_KIND_LENGTH + 1),
            title = "t",
        )
        assertNull(GenerativeUiSanitizer.sanitize(card))
    }
}
