package com.ninef.rikkallm.di

import kotlinx.serialization.json.Json
import com.ninef.rikkallm.AppScope
import com.ninef.rikkallm.data.ai.tools.local.LocalTools
import kotlinx.coroutines.CoroutineScope
import com.ninef.rikkallm.data.event.AppEventBus
import com.ninef.rikkallm.service.ChatNotificationManager
import com.ninef.rikkallm.service.ChatService
import com.ninef.rikkallm.utils.EmojiData
import com.ninef.rikkallm.utils.EmojiUtils
import com.ninef.rikkallm.utils.JsonInstant
import com.ninef.rikkallm.utils.SoundEffectPlayer
import com.ninef.rikkallm.utils.UpdateChecker
import com.ninef.rikkallm.web.WebServerManager
import me.rerere.tts.provider.TTSManager
import org.koin.dsl.module

val appModule = module {
    single<Json> { JsonInstant }

    single {
        AppEventBus()
    }

    single {
        LocalTools(get(), get(), get(), get())
    }

    single {
        UpdateChecker(get(), get(), get())
    }

    single {
        AppScope()
    }

    // AppScope 实现了 CoroutineScope，但 Koin 按精确类型解析。
    // 部分组件（如 SubAgentManager）按 CoroutineScope 类型注入，
    // 这里复用同一个 AppScope 实例，避免 NoDefinitionFoundException。
    single<CoroutineScope> {
        get<AppScope>()
    }

    single<EmojiData> {
        EmojiUtils.loadEmoji(get())
    }

    single {
        TTSManager(get())
    }

    single {
        SoundEffectPlayer(get())
    }

    // 生成通知与业务解耦：ChatService 只发事件，通知由这里消费；
    // createdAtStart 保证进程启动即订阅，否则后台生成的事件会因无订阅者而丢失
    single(createdAtStart = true) {
        ChatNotificationManager(
            context = get(),
            appScope = get(),
            eventBus = get(),
            settingsStore = get(),
        )
    }

    single {
        ChatService(
            context = get(),
            appScope = get(),
            appEventBus = get(),
            settingsStore = get(),
            conversationRepo = get(),
            memoryRepository = get(),
            generationHandler = get(),
            templateTransformer = get(),
            providerManager = get(),
            localTools = get(),
            mcpManager = get(),
            filesManager = get(),
            skillManager = get(),
            workspaceRepository = get(),
            folderRepository = get(),
            webMountManager = get(),
            cliSeatManager = get(),
            deepReadRunner = get()
        )
    }

    single {
        WebServerManager(
            context = get(),
            appScope = get(),
            chatService = get(),
            conversationRepo = get(),
            folderRepo = get(),
            settingsStore = get(),
            filesManager = get()
        )
    }
}
