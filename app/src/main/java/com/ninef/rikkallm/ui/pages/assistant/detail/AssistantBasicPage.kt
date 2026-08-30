package com.ninef.rikkallm.ui.pages.assistant.detail

import com.ninef.rikkallm.ui.theme.Spacing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.ninef.rikkallm.Screen
import com.ninef.rikkallm.ui.components.ai.WorkspaceSelectSheet
import com.ninef.rikkallm.ui.context.LocalNavController
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ModelType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import com.ninef.rikkallm.R
import com.ninef.rikkallm.data.db.entity.WorkspaceEntity
import com.ninef.rikkallm.data.model.Assistant
import com.ninef.rikkallm.ui.components.ai.ModelSelector
import com.ninef.rikkallm.ui.components.ai.ReasoningButton
import com.ninef.rikkallm.ui.components.nav.BackButton
import com.ninef.rikkallm.ui.components.ui.FormItem
import com.ninef.rikkallm.ui.components.ui.TagsInput
import com.ninef.rikkallm.ui.components.ui.UIAvatar
import com.ninef.rikkallm.ui.hooks.heroAnimation
import com.ninef.rikkallm.ui.theme.CustomColors
import com.ninef.rikkallm.utils.toFixed
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt
import kotlin.uuid.Uuid
import com.ninef.rikkallm.data.model.Tag as DataTag

@Composable
fun AssistantBasicPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val providers by vm.providers.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val workspaces by vm.workspaces.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.assistant_page_tab_basic))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        AssistantBasicContent(
            innerPadding = innerPadding,
            assistant = assistant,
            providers = providers,
            tags = tags,
            workspaces = workspaces,
            onUpdate = { vm.update(it) },
            vm = vm
        )
    }
}

@Composable
internal fun AssistantBasicContent(
    innerPadding: PaddingValues,
    assistant: Assistant,
    providers: List<me.rerere.ai.provider.ProviderSetting>,
    tags: List<DataTag>,
    workspaces: List<WorkspaceEntity>,
    onUpdate: (Assistant) -> Unit,
    vm: AssistantDetailVM
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(innerPadding)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            UIAvatar(
                value = assistant.avatar,
                name = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
                onUpdate = { avatar ->
                    onUpdate(
                        assistant.copy(
                            avatar = avatar
                        )
                    )
                },
                modifier = Modifier
                    .size(80.dp)
                    .heroAnimation("assistant_${assistant.id}")
            )
        }

        Card(
            colors = CustomColors.cardColorsOnSurfaceContainer
        ) {
            FormItem(
                label = {
                    Text(stringResource(R.string.assistant_page_name))
                },
                modifier = Modifier.padding(Spacing.sm),

                ) {
                OutlinedTextField(
                    value = assistant.name,
                    onValueChange = {
                        onUpdate(
                            assistant.copy(
                                name = it
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider()

            FormItem(
                label = {
                    Text(stringResource(R.string.assistant_page_tags))
                },
                modifier = Modifier.padding(Spacing.sm),
            ) {
                TagsInput(
                    value = assistant.tags,
                    tags = tags,
                    onValueChange = { tagIds, tagList ->
                        vm.updateTags(tagIds, tagList)
                    },
                )
            }

            HorizontalDivider()

            val context = LocalContext.current
            val navController = LocalNavController.current
            val scope = rememberCoroutineScope()
            var showWorkspaceSheet by remember { mutableStateOf(false) }
            var showCreateDialog by remember { mutableStateOf(false) }
            var newWorkspaceName by remember { mutableStateOf("") }
            var importing by remember { mutableStateOf(false) }

            val importLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocumentTree()
            ) { uri ->
                if (uri != null) {
                    scope.launch {
                        try {
                            importing = true
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION,
                            )
                            val displayName = DocumentFile.fromTreeUri(context, uri)?.name
                                ?: context.getString(R.string.workspace_import_default_name)
                            val ws = vm.importWorkspaceFromFolder(displayName, uri)
                            onUpdate(assistant.copy(workspaceId = Uuid.parse(ws.id)))
                            Toast.makeText(
                                context,
                                context.getString(R.string.workspace_import_success, ws.name),
                                Toast.LENGTH_SHORT,
                            ).show()
                        } catch (e: Exception) {
                            Log.e("AssistantBasicPage", "导入文件夹失败", e)
                            Toast.makeText(
                                context,
                                context.getString(R.string.workspace_import_failed),
                                Toast.LENGTH_LONG,
                            ).show()
                        } finally {
                            importing = false
                            showWorkspaceSheet = false
                        }
                    }
                }
            }

            // 工作区
            FormItem(
                label = {
                    Text(stringResource(R.string.assistant_page_workspace))
                },
                description = {
                    Text(stringResource(R.string.assistant_page_workspace_desc))
                },
                modifier = Modifier
                    .padding(Spacing.sm)
                    .clip(MaterialTheme.shapes.small)
                    .clickable { showWorkspaceSheet = true },
                tail = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        if (importing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        val selectedWorkspace = workspaces.find { it.id == assistant.workspaceId?.toString() }
                        Text(
                            selectedWorkspace?.name ?: stringResource(R.string.workspace_no_binding),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            HugeIcons.ArrowRight01,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            )

            if (showWorkspaceSheet) {
                WorkspaceSelectSheet(
                    assistant = assistant,
                    workspaces = workspaces,
                    onSelect = { wsId ->
                        onUpdate(assistant.copy(workspaceId = wsId?.let { Uuid.parse(it) }))
                        showWorkspaceSheet = false
                    },
                    onManage = { navController.navigate(Screen.Workspaces) },
                    onCreate = { showCreateDialog = true },
                    onImport = { importLauncher.launch(null) },
                    onDismiss = { showWorkspaceSheet = false },
                )
            }

            if (showCreateDialog) {
                AlertDialog(
                    onDismissRequest = { showCreateDialog = false },
                    confirmButton = {
                        TextButton(
                            enabled = newWorkspaceName.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    try {
                                        val name = newWorkspaceName.ifBlank {
                                            context.getString(R.string.workspace_create_default_name)
                                        }
                                        val ws = vm.createWorkspace(name)
                                        onUpdate(assistant.copy(workspaceId = Uuid.parse(ws.id)))
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.workspace_create_success, ws.name),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            e.message ?: context.getString(R.string.workspace_create_failed),
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                }
                                showCreateDialog = false
                            },
                        ) {
                            Text(stringResource(android.R.string.ok))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCreateDialog = false }) {
                            Text(stringResource(android.R.string.cancel))
                        }
                    },
                    title = { Text(stringResource(R.string.workspace_create_new)) },
                    text = {
                        OutlinedTextField(
                            value = newWorkspaceName,
                            onValueChange = { newWorkspaceName = it },
                            label = { Text(stringResource(R.string.workspace_name_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                )
            }

            HorizontalDivider()

            FormItem(
                modifier = Modifier.padding(Spacing.sm),
                label = {
                    Text(stringResource(R.string.assistant_page_use_assistant_avatar))
                },
                description = {
                    Text(stringResource(R.string.assistant_page_use_assistant_avatar_desc))
                },
                tail = {
                    Switch(
                        checked = assistant.useAssistantAvatar,
                        onCheckedChange = {
                            onUpdate(
                                assistant.copy(
                                    useAssistantAvatar = it
                                )
                            )
                        }
                    )
                }
            )
        }

        Card(
            colors = CustomColors.cardColorsOnSurfaceContainer
        ) {
            FormItem(
                modifier = Modifier.padding(Spacing.sm),
                label = {
                    Text(stringResource(R.string.assistant_page_chat_model))
                },
                description = {
                    Text(stringResource(R.string.assistant_page_chat_model_desc))
                },
                content = {
                    ModelSelector(
                        modelId = assistant.chatModelId,
                        providers = providers,
                        type = ModelType.CHAT,
                        onSelect = {
                            onUpdate(
                                assistant.copy(
                                    chatModelId = it.id
                                )
                            )
                        },
                    )
                }
            )
            HorizontalDivider()
            FormItem(
                modifier = Modifier.padding(Spacing.sm),
                label = {
                    Text(stringResource(R.string.assistant_page_temperature))
                },
                description = {
                    Text(
                        text = buildAnnotatedString {
                            append(stringResource(R.string.assistant_page_temperature_warning))
                        }
                    )
                },
                tail = {
                    Switch(
                        checked = assistant.temperature != null,
                        onCheckedChange = { enabled ->
                            onUpdate(
                                assistant.copy(
                                    temperature = if (enabled) 1.0f else null
                                )
                            )
                        }
                    )
                }
            ) {
                if (assistant.temperature != null) {
                    var temperatureInput by remember(assistant.id) {
                        mutableStateOf(assistant.temperature.toString())
                    }
                    val temperatureValue = temperatureInput.toFloatOrNull()
                    OutlinedTextField(
                        value = temperatureInput,
                        onValueChange = { value ->
                            temperatureInput = value
                            value.toFloatOrNull()?.takeIf { it in 0f..2f }?.let { temperature ->
                                onUpdate(
                                    assistant.copy(
                                        temperature = temperature
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = temperatureValue == null || temperatureValue !in 0f..2f,
                        supportingText = {
                            Text("0 - 2")
                        }
                    )
                }
            }
            HorizontalDivider()
            FormItem(
                modifier = Modifier.padding(Spacing.sm),
                label = {
                    Text(stringResource(R.string.assistant_page_top_p))
                },
                description = {
                    Text(
                        text = buildAnnotatedString {
                            append(stringResource(R.string.assistant_page_top_p_warning))
                        }
                    )
                },
                tail = {
                    Switch(
                        checked = assistant.topP != null,
                        onCheckedChange = { enabled ->
                            onUpdate(
                                assistant.copy(
                                    topP = if (enabled) 1.0f else null
                                )
                            )
                        }
                    )
                }
            ) {
                assistant.topP?.let { topP ->
                    var topPInput by remember(assistant.id) {
                        mutableStateOf(topP.toString())
                    }
                    val topPValue = topPInput.toFloatOrNull()
                    OutlinedTextField(
                        value = topPInput,
                        onValueChange = { value ->
                            topPInput = value
                            value.toFloatOrNull()?.takeIf { it in 0f..1f }?.let { nextTopP ->
                                onUpdate(
                                    assistant.copy(
                                        topP = nextTopP
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = topPValue == null || topPValue !in 0f..1f,
                        supportingText = {
                            Text("0 - 1")
                        }
                    )
                }
            }
            HorizontalDivider()
            FormItem(
                modifier = Modifier.padding(Spacing.sm),
                label = {
                    Text(stringResource(R.string.assistant_page_context_message_limit))
                },
                description = {
                    Text(
                        text = stringResource(R.string.assistant_page_context_message_limit_desc),
                    )
                }
            ) {
                Slider(
                    value = assistant.contextMessageLimit.toFloat(),
                    onValueChange = { value ->
                        onUpdate(
                            assistant.copy(
                                contextMessageLimit = snapContextMessageLimit(value)
                            )
                        )
                    },
                    valueRange = 0f..512f,
                    steps = 0,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = if (assistant.contextMessageLimit > 0) stringResource(
                        R.string.assistant_page_context_message_limit_count,
                        assistant.contextMessageLimit
                    ) else stringResource(R.string.assistant_page_context_message_limit_unlimited),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f),
                )

                if (assistant.contextMessageLimit > 0) {
                    Text(
                        text = stringResource(R.string.assistant_page_context_message_limit_warning),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            HorizontalDivider()
            FormItem(
                modifier = Modifier.padding(Spacing.sm),
                label = {
                    Text(stringResource(R.string.assistant_page_stream_output))
                },
                description = {
                    Text(stringResource(R.string.assistant_page_stream_output_desc))
                },
                tail = {
                    Switch(
                        checked = assistant.streamOutput,
                        onCheckedChange = {
                            onUpdate(
                                assistant.copy(
                                    streamOutput = it
                                )
                            )
                        }
                    )
                }
            )
            HorizontalDivider()
            FormItem(
                modifier = Modifier.padding(Spacing.sm),
                label = {
                    Text(stringResource(R.string.assistant_page_thinking_budget))
                },
            ) {
                ReasoningButton(
                    reasoningLevel = assistant.reasoningLevel,
                    onUpdateReasoningLevel = { level ->
                        onUpdate(assistant.copy(reasoningLevel = level))
                    }
                )
            }
            HorizontalDivider()
            FormItem(
                modifier = Modifier.padding(Spacing.sm),
                label = {
                    Text(stringResource(R.string.assistant_page_max_tokens))
                },
                description = {
                    Text(stringResource(R.string.assistant_page_max_tokens_desc))
                }
            ) {
                OutlinedTextField(
                    value = assistant.maxTokens?.toString() ?: "",
                    onValueChange = { text ->
                        val tokens = if (text.isBlank()) {
                            null
                        } else {
                            text.toIntOrNull()?.takeIf { it > 0 }
                        }
                        onUpdate(
                            assistant.copy(
                                maxTokens = tokens
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(stringResource(R.string.assistant_page_max_tokens_no_limit))
                    },
                    supportingText = {
                        if (assistant.maxTokens != null) {
                            Text(stringResource(R.string.assistant_page_max_tokens_limit, assistant.maxTokens))
                        } else {
                            Text(stringResource(R.string.assistant_page_max_tokens_no_token_limit))
                        }
                    }
                )
            }
        }

        Card(
            colors = CustomColors.cardColorsOnSurfaceContainer
        ) {
            FormItem(
                modifier = Modifier.padding(Spacing.sm),
                label = {
                    Text(stringResource(R.string.assistant_page_gradient_background))
                },
                description = {
                    Text(stringResource(R.string.assistant_page_gradient_background_desc))
                },
                tail = {
                    Switch(
                        checked = assistant.useGradientBackground,
                        onCheckedChange = {
                            onUpdate(
                                assistant.copy(
                                    useGradientBackground = it
                                )
                            )
                        }
                    )
                }
            )

            if (!assistant.useGradientBackground) {
                HorizontalDivider()

                BackgroundPicker(
                    modifier = Modifier.padding(Spacing.sm),
                    background = assistant.background,
                    backgroundOpacity = assistant.backgroundOpacity,
                    onUpdate = { background ->
                        onUpdate(
                            assistant.copy(
                                background = background
                            )
                        )
                    }
                )
            }

            if (!assistant.useGradientBackground && assistant.background != null) {
                val backgroundOpacity = assistant.backgroundOpacity.coerceIn(0f, 1f)
                HorizontalDivider()
                FormItem(
                    modifier = Modifier.padding(Spacing.sm),
                    label = {
                        Text(stringResource(R.string.assistant_page_background_opacity))
                    },
                    description = {
                        Text(stringResource(R.string.assistant_page_background_opacity_desc))
                    }
                ) {
                    Slider(
                        value = backgroundOpacity,
                        onValueChange = {
                            onUpdate(
                                assistant.copy(
                                    backgroundOpacity = it.toFixed(2).toFloatOrNull()?.coerceIn(0f, 1f) ?: 1.0f
                                )
                            )
                        },
                        valueRange = 0f..1f,
                        steps = 19,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(
                            R.string.assistant_page_background_opacity_value,
                            (backgroundOpacity * 100).roundToInt()
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}

/**
 * 上下文限制的最小有效值
 *
 * 低于此值时截断点几乎每轮都在移动, 提示词缓存命中率跌破 90%,
 * 且保留的上下文通常达不到可缓存的最小长度, 限制本身失去意义
 */
private const val MIN_CONTEXT_MESSAGE_LIMIT = 20

/**
 * 把滑块取值吸附到 0(不限制) 或不低于 [MIN_CONTEXT_MESSAGE_LIMIT] 的有效档位
 */
private fun snapContextMessageLimit(value: Float): Int {
    val raw = value.roundToInt()
    return when {
        raw < MIN_CONTEXT_MESSAGE_LIMIT / 2 -> 0
        raw < MIN_CONTEXT_MESSAGE_LIMIT -> MIN_CONTEXT_MESSAGE_LIMIT
        else -> raw
    }
}
