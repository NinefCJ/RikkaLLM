package com.ninef.rikkallm.di

import com.ninef.rikkallm.data.editor.EditorSessionManager
import com.ninef.rikkallm.data.plugin.IdePlugin
import com.ninef.rikkallm.data.plugin.PluginManager
import com.ninef.rikkallm.data.plugin.builtin.AiAssistantPlugin
import org.koin.dsl.module

val pluginModule = module {
    // 所有 IdePlugin 实现在启动期注入并统一初始化（编译期注册，零阻塞）
    single { PluginManager(get(), getAll<IdePlugin>()) }
    single<IdePlugin> { AiAssistantPlugin() }
}
