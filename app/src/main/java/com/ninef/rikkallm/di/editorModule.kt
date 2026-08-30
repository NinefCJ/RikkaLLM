package com.ninef.rikkallm.di

import com.ninef.rikkallm.data.editor.EditorSessionManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val editorModule = module {
    single { EditorSessionManager(androidContext()) }
}
