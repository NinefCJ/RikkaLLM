package com.ninef.rikkallm.ui.context

import androidx.compose.runtime.compositionLocalOf
import com.ninef.rikkallm.ui.hooks.CustomAsrState

val LocalASRState = compositionLocalOf<CustomAsrState> { error("Not provided yet") }

