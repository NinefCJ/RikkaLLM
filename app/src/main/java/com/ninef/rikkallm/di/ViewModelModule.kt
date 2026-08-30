package com.ninef.rikkallm.di

import com.ninef.rikkallm.ui.pages.assistant.AssistantVM
import com.ninef.rikkallm.ui.pages.assistant.detail.AssistantDetailVM
import com.ninef.rikkallm.ui.pages.backup.BackupVM
import com.ninef.rikkallm.ui.pages.chat.ChatDrawerVM
import com.ninef.rikkallm.ui.pages.chat.ChatVM
import com.ninef.rikkallm.ui.pages.debug.DebugVM
import com.ninef.rikkallm.ui.pages.favorite.FavoriteVM
import com.ninef.rikkallm.ui.pages.search.SearchVM
import com.ninef.rikkallm.ui.pages.history.HistoryVM
import com.ninef.rikkallm.ui.pages.stats.StatsVM
import com.ninef.rikkallm.ui.pages.imggen.ImgGenVM
import com.ninef.rikkallm.ui.pages.extensions.PromptVM
import com.ninef.rikkallm.ui.pages.extensions.QuickMessagesVM
import com.ninef.rikkallm.ui.pages.extensions.skills.SkillDetailVM
import com.ninef.rikkallm.ui.pages.extensions.skills.SkillsVM
import com.ninef.rikkallm.ui.pages.extensions.workspace.WorkspaceDetailVM
import com.ninef.rikkallm.ui.pages.extensions.workspace.WorkspaceVM
import com.ninef.rikkallm.ui.pages.setting.SettingVM
import com.ninef.rikkallm.ui.pages.setting.CronJobVM
import com.ninef.rikkallm.ui.pages.setting.WebMountVM
import com.ninef.rikkallm.ui.pages.setting.CliSeatVM
import com.ninef.rikkallm.ui.pages.deepread.DeepReadVM
import com.ninef.rikkallm.ui.pages.share.handler.ShareHandlerVM
import com.ninef.rikkallm.ui.pages.translator.TranslatorVM
import com.ninef.rikkallm.ui.pages.setting.ModelMarketVM
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModel<ChatVM> { params ->
        ChatVM(
            id = params.get(),
            context = get(),
            settingsStore = get(),
            conversationRepo = get(),
            chatService = get(),
            updateChecker = get(),
            filesManager = get(),
            favoriteRepository = get(),
        )
    }
    viewModelOf(::ChatDrawerVM)
    viewModelOf(::SettingVM)
    viewModelOf(::DebugVM)
    viewModelOf(::HistoryVM)
    viewModelOf(::AssistantVM)
    viewModel<AssistantDetailVM> {
        AssistantDetailVM(
            id = it.get(),
            settingsStore = get(),
            memoryRepository = get(),
            filesManager = get(),
            skillManager = get(),
            workspaceRepository = get(),
        )
    }
    viewModelOf(::TranslatorVM)
    viewModel<ShareHandlerVM> {
        ShareHandlerVM(
            text = it.get(),
            settingsStore = get(),
        )
    }
    viewModelOf(::BackupVM)
    viewModelOf(::ImgGenVM)
    viewModelOf(::PromptVM)
    viewModelOf(::QuickMessagesVM)
    viewModelOf(::SkillsVM)
    viewModelOf(::SkillDetailVM)
    viewModelOf(::WorkspaceVM)
    viewModel<WorkspaceDetailVM> {
        WorkspaceDetailVM(
            id = it.get(),
            repository = get(),
        )
    }
    viewModelOf(::FavoriteVM)
    viewModelOf(::SearchVM)
    viewModelOf(::StatsVM)
    viewModelOf(::ModelMarketVM)

    viewModelOf(::CronJobVM)
    viewModelOf(::WebMountVM)
    viewModelOf(::CliSeatVM)
    viewModelOf(::DeepReadVM)
}
