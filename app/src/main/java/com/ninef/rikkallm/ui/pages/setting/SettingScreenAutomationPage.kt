package com.ninef.rikkallm.ui.pages.setting

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.SmartPhone01
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.ninef.rikkallm.service.ScreenAutomationService
import com.ninef.rikkallm.ui.context.LocalNavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreenAutomationPage() {
    val navController = LocalNavController.current
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(ScreenAutomationService.isEnabled()) }

    DisposableEffect(Unit) {
        var active = true
        val job = CoroutineScope(Dispatchers.Main).launch {
            while (active) {
                enabled = ScreenAutomationService.isEnabled()
                delay(1000)
            }
        }
        onDispose {
            active = false
            job.cancel()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("屏幕自动化") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(HugeIcons.ArrowLeft01, null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "状态：${if (enabled) "已开启" else "未开启"}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "屏幕自动化允许 RikkaLLM 读取当前界面的控件与文本，并代为点击、输入、滚动，供 AI Agent 完成屏幕操作类任务。\n\n" +
                            "该能力依赖 Android 无障碍服务，需要你在系统设置中手动授予。开启后，Agent 每次执行屏幕动作都会向你确认。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(HugeIcons.SmartPhone01, null)
                        Text("前往系统无障碍设置开启")
                    }
                }
            }
        }
    }
}
