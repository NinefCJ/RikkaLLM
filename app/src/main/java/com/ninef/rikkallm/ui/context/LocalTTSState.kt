package com.ninef.rikkallm.ui.context

import androidx.compose.runtime.compositionLocalOf
import com.ninef.rikkallm.ui.hooks.CustomTtsState

val LocalTTSState = compositionLocalOf<CustomTtsState> { error("Not provided yet") }
