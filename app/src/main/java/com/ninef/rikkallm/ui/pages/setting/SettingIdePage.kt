package com.ninef.rikkallm.ui.pages.setting

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.ninef.rikkallm.ui.components.nav.BackButton
import com.ninef.rikkallm.ui.components.ui.CardGroup
import com.ninef.rikkallm.ui.hooks.rememberUserSettingsState
import com.ninef.rikkallm.ui.theme.CustomColors
import com.ninef.rikkallm.ui.theme.Spacing
import kotlin.math.roundToInt
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Folder01
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingIdePage(vm: SettingVM = koinViewModel()) {
    val settings by rememberUserSettingsState()
    val context = LocalContext.current

    val workspaceName = remember(settings.ideWorkspaceUri) {
        if (settings.ideWorkspaceUri.isBlank()) {
            null
        } else {
            runCatching {
                DocumentFile.fromTreeUri(context, Uri.parse(settings.ideWorkspaceUri))?.name
            }.getOrNull()
        }
    }

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        vm.updateSettings(settings.copy(ideWorkspaceUri = uri.toString()))
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("IDE 设置") },
                navigationIcon = { BackButton() },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            CardGroup(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.lg),
                title = { Text("编辑器外观") },
            ) {
                item(
                    headlineContent = { Text("编辑器字体大小") },
                    supportingContent = {
                        Column {
                            Text("${settings.ideFontSize} sp")
                            Slider(
                                value = settings.ideFontSize.toFloat(),
                                onValueChange = { value ->
                                    vm.updateSettings(
                                        settings.copy(ideFontSize = value.roundToInt().coerceIn(10, 28)),
                                    )
                                },
                                valueRange = 10f..28f,
                                steps = 18,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                )
                item(
                    headlineContent = { Text("显示行号") },
                    supportingContent = { Text("在编辑器左侧显示行号") },
                    trailingContent = {
                        Switch(
                            checked = settings.ideShowLineNumbers,
                            onCheckedChange = { checked ->
                                vm.updateSettings(settings.copy(ideShowLineNumbers = checked))
                            },
                        )
                    },
                )
                item(
                    headlineContent = { Text("自动换行") },
                    supportingContent = { Text("超出宽度时折行，不出现横向滚动条") },
                    trailingContent = {
                        Switch(
                            checked = settings.ideWordWrap,
                            onCheckedChange = { checked ->
                                vm.updateSettings(settings.copy(ideWordWrap = checked))
                            },
                        )
                    },
                )
            }

            CardGroup(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.lg),
                title = { Text("编辑行为") },
            ) {
                item(
                    headlineContent = { Text("自动保存") },
                    supportingContent = { Text("编辑停顿 0.8 秒后自动保存到磁盘") },
                    trailingContent = {
                        Switch(
                            checked = settings.ideAutoSave,
                            onCheckedChange = { checked ->
                                vm.updateSettings(settings.copy(ideAutoSave = checked))
                            },
                        )
                    },
                )
            }

            CardGroup(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.lg),
                title = { Text("工作区") },
            ) {
                item(
                    headlineContent = { Text("默认工作区目录") },
                    supportingContent = {
                        Text(workspaceName ?: "未设置（使用应用内置工作区）")
                    },
                    trailingContent = {
                        IconButton(onClick = { pickFolder.launch(null) }) {
                            Icon(HugeIcons.Folder01, contentDescription = "选择文件夹")
                        }
                    },
                    onClick = { pickFolder.launch(null) },
                )
                if (settings.ideWorkspaceUri.isNotBlank()) {
                    item(
                        headlineContent = { Text("清除默认工作区") },
                        supportingContent = { Text("恢复为应用内置工作区") },
                        onClick = { vm.updateSettings(settings.copy(ideWorkspaceUri = "")) },
                    )
                }
            }
        }
    }
}
