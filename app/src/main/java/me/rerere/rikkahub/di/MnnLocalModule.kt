package me.rerere.rikkahub.di

import com.alibaba.mnnllm.android.server.LocalMnnManager
import me.rerere.rikkahub.data.ai.MnnLocalProviderSync
import org.koin.dsl.module

// Koin wiring for the local MNN engine stack (Phase 2). LocalMnnManager owns the
// engine + foreground service lifecycle; MnnLocalProviderSync mirrors the running
// server's port/token into the built-in "MNN 本地模型" provider entry.
val mnnLocalModule = module {
    single { LocalMnnManager(get()) }

    single(createdAtStart = true) {
        MnnLocalProviderSync(
            localMnnManager = get(),
            settingsStore = get(),
            appScope = get(),
        )
    }
}
