package com.ninef.rikkallm.ui.context

import androidx.compose.runtime.staticCompositionLocalOf
import com.ninef.rikkallm.data.datastore.Settings

val LocalSettings = staticCompositionLocalOf<Settings> {
    error("No SettingsStore provided")
}
