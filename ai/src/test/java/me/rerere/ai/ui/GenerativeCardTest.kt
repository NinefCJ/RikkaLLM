package me.rerere.ai.ui

import kotlinx.serialization.json.buildJsonObject
import me.rerere.ai.core.MessageRole
import me.rerere.ai.util.json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerativeCardTest {

    private fun sampleCard() = GenerativeCardData(
        kind = "search_result",
        title = "Search results",
        subtitle = "Top 3",
        items = listOf(
            GenerativeCardItem(
                label = "1",
                value = "Kotlin docs",
                detail = "official",
                link = "https://kotlinlang.org",
            ),
            GenerativeCardItem(
                label = "2",
                value = "code sample",
                code = "println(\"hi\")",
            ),
        ),
        footer = "3 results",
    )

    @Test
    fun `GenerativeCardData round-trip preserves all fields`() {
        val card = sampleCard()
        val encoded = json.encodeToString(GenerativeCardData.serializer(), card)
        val decoded = json.decodeFromString(GenerativeCardData.serializer(), encoded)
        assertEquals(card, decoded)
    }

    @Test
    fun `GenerativeCard part round-trip via UIMessage`() {
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Text("text"), UIMessagePart.GenerativeCard(sampleCard())),
        )
        val encoded = json.encodeToString(UIMessage.serializer(), message)
        val decoded = json.decodeFromString(UIMessage.serializer(), encoded)

        val card = decoded.parts.filterIsInstance<UIMessagePart.GenerativeCard>().single()
        assertEquals("search_result", card.card.kind)
        assertEquals("Search results", card.card.title)
        assertEquals(2, card.card.items.size)
        assertEquals("https://kotlinlang.org", card.card.items[0].link)
        assertEquals("println(\"hi\")", card.card.items[1].code)
        assertEquals("3 results", card.card.footer)
    }

    @Test
    fun `unknown part types are ignored during decode`() {
        val encoded = """{"role":"assistant","parts":[{"type":"generative_card","card":{}}]}"""
        val decoded = json.decodeFromString(UIMessage.serializer(), encoded)
        val card = decoded.parts.filterIsInstance<UIMessagePart.GenerativeCard>().single()
        assertEquals("card", card.card.kind)
        assertTrue(card.card.items.isEmpty())
    }

    @Test
    fun `metadata is preserved on card part`() {
        val metadata = buildJsonObject { }
        val part = UIMessagePart.GenerativeCard(
            card = sampleCard(),
            metadata = metadata,
        )
        assertEquals(metadata, part.metadata)
    }
}
