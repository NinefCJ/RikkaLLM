package com.ninef.rikkallm.ui.pages.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import me.rerere.ai.core.MessageRole
import com.ninef.rikkallm.data.datastore.Settings
import com.ninef.rikkallm.data.model.Conversation
import com.ninef.rikkallm.ui.context.LocalTTSState
import com.ninef.rikkallm.utils.extractQuotedContentAsText
import com.ninef.rikkallm.utils.removeBracketedContent

@Composable
fun TTSAutoPlay(vm: ChatVM, setting: Settings, conversation: Conversation) {
    // Auto-play TTS after generation completes
    val tts = LocalTTSState.current
    val currentConversation by rememberUpdatedState(conversation)
    val updatedSetting by rememberUpdatedState(setting)
    LaunchedEffect(Unit) {
        vm.generationDoneFlow.collect { conversationId ->
            if (updatedSetting.displaySetting.autoPlayTTSAfterGeneration) {
                val lastMessage = currentConversation.currentMessages.lastOrNull()
                if (lastMessage != null && lastMessage.role == MessageRole.ASSISTANT) {
                    val text = lastMessage.toText()
                    var textToSpeak = text
                    if (updatedSetting.displaySetting.ttsOnlyReadQuoted) {
                        textToSpeak = textToSpeak.extractQuotedContentAsText() ?: textToSpeak
                    }
                    if (updatedSetting.displaySetting.ttsOnlyReadOutsideBrackets) {
                        textToSpeak = textToSpeak.removeBracketedContent() ?: textToSpeak
                    }
                    if (textToSpeak.isNotBlank()) {
                        tts.speak(textToSpeak)
                    }
                }
            }
        }
    }
}
