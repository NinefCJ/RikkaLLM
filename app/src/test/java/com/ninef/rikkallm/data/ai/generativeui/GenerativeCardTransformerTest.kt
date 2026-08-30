package com.ninef.rikkallm.data.ai.generativeui

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerativeCardTransformerTest {

    private fun assistantMessage(text: String) = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = listOf(UIMessagePart.Text(text)),
    )

    private fun textOf(msg: UIMessage): String =
        msg.parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }

    // ---- extractGenerativeCards ----

    @Test
    fun `fenced card is extracted into GenerativeCard part`() {
        val text = """
            Here are the results:
            :::generative-ui
            {"kind":"search_result","title":"Results","items":[{"label":"a","value":"b"}]}
            :::
        """.trimIndent()

        val parts = extractGenerativeCards(text)
        assertTrue(parts.any { it is UIMessagePart.GenerativeCard })
        val card = parts.filterIsInstance<UIMessagePart.GenerativeCard>().single()
        assertEquals("search_result", card.card.kind)
        assertEquals("Results", card.card.title)
        assertEquals("b", card.card.items[0].value)
    }

    @Test
    fun `text around fences is preserved`() {
        val text = "prefix\n:::generative-ui\n{\"kind\":\"card\",\"title\":\"t\"}\n:::\nsuffix"
        val parts = extractGenerativeCards(text)
        assertEquals(3, parts.size)
        assertEquals("prefix\n", (parts[0] as UIMessagePart.Text).text)
        assertTrue(parts[1] is UIMessagePart.GenerativeCard)
        assertEquals("suffix", (parts[2] as UIMessagePart.Text).text)
    }

    @Test
    fun `multiple cards in one text are all extracted`() {
        val text = """
            :::generative-ui
            {"kind":"card","title":"one"}
            :::
            middle
            :::generative-ui
            {"kind":"status","title":"two"}
            :::
        """.trimIndent()
        val parts = extractGenerativeCards(text)
        assertEquals(3, parts.size)
        val cards = parts.filterIsInstance<UIMessagePart.GenerativeCard>()
        assertEquals(2, cards.size)
        assertEquals("one", cards[0].card.title)
        assertEquals("two", cards[1].card.title)
    }

    @Test
    fun `invalid json keeps raw fence text`() {
        val text = ":::generative-ui\n{not valid json\n:::"
        val parts = extractGenerativeCards(text)
        assertEquals(1, parts.size)
        assertTrue(parts[0] is UIMessagePart.Text)
        assertTrue(textOf(UIMessage(role = MessageRole.ASSISTANT, parts = parts)).contains(":::generative-ui"))
    }

    @Test
    fun `card failing sanitize keeps raw fence text`() {
        val text = ":::generative-ui\n{\"kind\":\"card\",\"title\":\"t\",\"items\":[{\"label\":\"x\",\"value\":\"y\",\"link\":\"javascript:alert(1)\"}]}\n:::"
        val parts = extractGenerativeCards(text)
        // items 唯一条目 link 被剥离后 label/value 仍合法 -> 卡片保留但 link 为 null
        val card = parts.filterIsInstance<UIMessagePart.GenerativeCard>().single()
        assertEquals(null, card.card.items[0].link)

        // 整卡非法的场景（超长 title）
        val bad = ":::generative-ui\n{\"kind\":\"card\",\"title\":\"${"x".repeat(200)}\"}\n:::"
        val badParts = extractGenerativeCards(bad)
        assertTrue(badParts.single() is UIMessagePart.Text)
    }

    @Test
    fun `single-line fence is supported`() {
        val text = ":::generative-ui {\"kind\":\"card\",\"title\":\"inline\"} :::"
        val parts = extractGenerativeCards(text)
        val card = parts.filterIsInstance<UIMessagePart.GenerativeCard>().single()
        assertEquals("inline", card.card.title)
    }

    @Test
    fun `text without fence is untouched`() {
        val text = "just plain text"
        val parts = extractGenerativeCards(text)
        assertEquals(1, parts.size)
        assertEquals(text, (parts[0] as UIMessagePart.Text).text)
    }

    // ---- parseGenerativeCard ----

    @Test
    fun `parse with trailing content tolerance`() {
        val card = parseGenerativeCard("{\"kind\":\"card\",\"title\":\"t\"}")
        assertTrue(card != null)
        assertEquals("t", card!!.title)
    }

    @Test
    fun `parse blank returns null`() {
        assertEquals(null, parseGenerativeCard("   "))
    }

    // ---- onGenerationFinish via GenerativeCardTransformer ----

    @Test
    fun `onGenerationFinish replaces fence with card part`() {
        val messages = listOf(
            assistantMessage(
                "summary\n:::generative-ui\n{\"kind\":\"card\",\"title\":\"t\",\"items\":[{\"label\":\"l\",\"value\":\"v\"}]}\n:::"
            )
        )
        val result = finishGenerativeCards(messages)
        val message = result.single()
        assertTrue(message.parts.any { it is UIMessagePart.GenerativeCard })
        assertFalse(textOf(message).contains(":::generative-ui"))
    }

    @Test
    fun `non-assistant message is not touched`() {
        val user = UIMessage(
            role = MessageRole.USER,
            parts = listOf(UIMessagePart.Text(":::generative-ui\n{\"kind\":\"card\",\"title\":\"t\"}\n:::")),
        )
        val result = finishGenerativeCards(listOf(user))
        assertEquals(user, result.single())
    }

    // ---- injectGenerativeUiPrompt ----

    @Test
    fun `prompt is appended to first system message`() {
        val system = UIMessage(role = MessageRole.SYSTEM, parts = listOf(UIMessagePart.Text("be nice")))
        val user = UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text("hi")))
        val result = injectGenerativeUiPrompt(listOf(system, user), modelName = "test-model")

        assertEquals(2, result.size)
        val systemText = textOf(result[0])
        assertTrue(systemText.contains("be nice"))
        assertTrue(systemText.contains(GenerativeUiPrompt.FENCE_START))
        assertTrue(systemText.contains("test-model"))
    }

    @Test
    fun `prompt inserts new system message when none exists`() {
        val user = UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text("hi")))
        val result = injectGenerativeUiPrompt(listOf(user), modelName = null)

        assertEquals(2, result.size)
        assertEquals(MessageRole.SYSTEM, result[0].role)
        assertTrue(textOf(result[0]).contains(GenerativeUiPrompt.FENCE_START))
        assertEquals(user, result[1])
    }
}
