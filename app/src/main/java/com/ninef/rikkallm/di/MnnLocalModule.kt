package com.ninef.rikkallm.di

import com.alibaba.mnnllm.android.server.LocalMnnManager
import com.ninef.rikkallm.data.ai.MnnLocalProviderSync
import com.ninef.rikkallm.data.mnn.LocalModelManager
import com.ninef.rikkallm.data.mnn.ModelDownloader
import org.koin.dsl.module

// Koin wiring for the local MNN engine stack (Phase 2 + Phase 3). LocalMnnManager owns the
// engine + foreground service lifecycle; MnnLocalProviderSync mirrors the running server's
// port/token into the built-in "MNN 本地模型" provider entry; LocalModelManager (Phase 3)
// drives in-app model download / version management on top of the on-disk registry.
val mnnLocalModule = module {
    single { LocalMnnManager(get()) }

    single(createdAtStart = true) {
        MnnLocalProviderSync(
            localMnnManager = get(),
            settingsStore = get(),
            appScope = get(),
        )
    }

    single { ModelDownloader(get()) }
    single { LocalModelManager(get(), get()) }
}
