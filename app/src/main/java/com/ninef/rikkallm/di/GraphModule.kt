package com.ninef.rikkallm.di

import com.ninef.rikkallm.data.ai.graph.ContextAssembler
import com.ninef.rikkallm.data.graph.GraphAutoSync
import com.ninef.rikkallm.data.graph.GraphOrchestrator
import com.ninef.rikkallm.data.graph.GraphStore
import com.ninef.rikkallm.data.graph.GraphStoreImpl
import com.ninef.rikkallm.ui.pages.graph.GraphCanvasVM
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * DAG 上下文图相关依赖。
 * - [GraphStore]：图的持久化仓库（M5）。
 * - [ContextAssembler]：上下文装配器（M2，挂点②）。
 * - [GraphCanvasVM]：可视化画布 ViewModel（M6 / M4）。
 *
 * 后续 M3 [com.ninef.rikkallm.data.graph.GraphOrchestrator] 将在此模块继续注册。
 */
val graphModule = module {
    single<GraphStore> { GraphStoreImpl(get()) }
    single { ContextAssembler(get()) }
    single { GraphOrchestrator(get(), get()) }
    single { GraphAutoSync(get(), get(), get()) }

    viewModel<GraphCanvasVM> { params ->
        GraphCanvasVM(
            conversationId = params.get(),
            graphStore = get(),
            conversationRepo = get(),
            orchestrator = get(),
        )
    }
}
