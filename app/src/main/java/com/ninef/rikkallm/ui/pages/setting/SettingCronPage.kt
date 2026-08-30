package com.ninef.rikkallm.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Play
import me.rerere.hugeicons.stroke.Sun01
import com.ninef.rikkallm.R
import com.ninef.rikkallm.data.ai.cron.CronJobStatus
import com.ninef.rikkallm.data.ai.cron.CronJobType
import com.ninef.rikkallm.data.db.entity.CronJobEntity
import com.ninef.rikkallm.ui.components.nav.BackButton
import com.ninef.rikkallm.ui.components.ui.Switch
import com.ninef.rikkallm.ui.components.ui.Tag
import com.ninef.rikkallm.ui.components.ui.TagType
import com.ninef.rikkallm.ui.theme.Spacing
import com.ninef.rikkallm.data.ai.cron.CronParser
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingCronPage(
    vm: CronJobVM = koinViewModel(),
) {
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val busyIds by vm.busyJobIds.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var editingJob by remember { mutableStateOf<CronJobEntity?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<CronJobEntity?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setting_page_cron)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FilledTonalIconButton(onClick = { showCreate = true }) {
                Icon(HugeIcons.Add01, contentDescription = null)
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .imePadding(),
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
                end = innerPadding.calculateEndPadding(LocalLayoutDirection.current),
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item("board") {
                BoardBanner(
                    boardJob = jobs.firstOrNull { it.type == CronJobType.BOARD.name },
                    onCreate = { vm.ensureBoardJob() },
                    onOpen = { job -> editingJob = job },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.sm),
                )
            }

            item("header") {
                Text(
                    text = stringResource(R.string.setting_page_cron_jobs),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                )
            }

            if (jobs.isEmpty()) {
                item("empty") {
                    Text(
                        text = stringResource(R.string.setting_page_cron_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
                    )
                }
            } else {
                items(jobs, key = { it.jobId }) { job ->
                    CronJobCard(
                        job = job,
                        busy = busyIds.contains(job.jobId),
                        onToggle = { vm.setEnabled(job.jobId, it) },
                        onEdit = { editingJob = job },
                        onRun = { vm.runNow(job) },
                        onDelete = { deleteTarget = job },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.sm),
                    )
                }
            }
        }
    }

    if (showCreate) {
        CronJobEditSheet(
            initial = null,
            onDismiss = { showCreate = false },
            onSave = { name, cron, prompt, type ->
                vm.createJob(
                    name = name,
                    cronExpr = cron,
                    prompt = prompt,
                    assistantId = "",
                    type = type,
                )
                showCreate = false
            },
        )
    }

    editingJob?.let { job ->
        CronJobEditSheet(
            initial = job,
            onDismiss = { editingJob = null },
            onSave = { name, cron, prompt, type ->
                vm.updateJob(
                    job.copy(
                        name = name,
                        cronExpr = cron,
                        prompt = prompt,
                        type = type.name,
                    ),
                )
                editingJob = null
            },
        )
    }

    deleteTarget?.let { job ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.setting_page_cron_delete_title)) },
            text = { Text(stringResource(R.string.setting_page_cron_delete_desc, job.name)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteJob(job.jobId)
                    deleteTarget = null
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun BoardBanner(
    boardJob: CronJobEntity?,
    onCreate: () -> Unit,
    onOpen: (CronJobEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(
                    HugeIcons.Sun01,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = stringResource(R.string.setting_page_cron_board),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                text = stringResource(R.string.setting_page_cron_board_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (boardJob == null) {
                    Button(onClick = onCreate) {
                        Text(stringResource(R.string.setting_page_cron_board_create))
                    }
                } else {
                    Text(
                        text = stringResource(
                            R.string.setting_page_cron_board_schedule,
                            boardJob.cronExpr,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(1.dp))
                    TextButton(onClick = { onOpen(boardJob) }) {
                        Text(stringResource(R.string.setting_page_cron_edit))
                    }
                }
            }
        }
    }
}

@Composable
private fun CronJobCard(
    job: CronJobEntity,
    busy: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onRun: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onEdit,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = job.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StatusTag(job.lastStatus)
            }
            Text(
                text = job.prompt.take(80),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = job.cronExpr,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRun, enabled = !busy) {
                    Icon(HugeIcons.Play, contentDescription = stringResource(R.string.setting_page_cron_run))
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        HugeIcons.Delete01,
                        contentDescription = stringResource(R.string.setting_page_cron_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                Switch(checked = job.enabled, onCheckedChange = onToggle)
            }
        }
    }
}

@Composable
private fun StatusTag(status: String) {
    val (text, type) = when (status) {
        CronJobStatus.SUCCESS.name -> stringResource(R.string.setting_page_cron_status_success) to TagType.SUCCESS
        CronJobStatus.FAILED.name -> stringResource(R.string.setting_page_cron_status_failed) to TagType.ERROR
        CronJobStatus.RUNNING.name -> stringResource(R.string.setting_page_cron_status_running) to TagType.INFO
        else -> stringResource(R.string.setting_page_cron_status_pending) to TagType.DEFAULT
    }
    Tag(type = type) { Text(text = text) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CronJobEditSheet(
    initial: CronJobEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, cronExpr: String, prompt: String, type: CronJobType) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var cronExpr by remember { mutableStateOf(initial?.cronExpr ?: "0 8 * * *") }
    var prompt by remember { mutableStateOf(initial?.prompt ?: "") }
    var type by remember {
        mutableStateOf(
            initial?.let { runCatching { CronJobType.valueOf(it.type) }.getOrDefault(CronJobType.CRON) }
                ?: CronJobType.CRON,
        )
    }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = stringResource(
                    if (initial == null) R.string.setting_page_cron_create else R.string.setting_page_cron_edit,
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.setting_page_cron_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = cronExpr,
                onValueChange = { cronExpr = it },
                label = { Text(stringResource(R.string.setting_page_cron_expr)) },
                supportingText = { Text(stringResource(R.string.setting_page_cron_expr_hint)) },
                singleLine = true,
                isError = error != null,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text(stringResource(R.string.setting_page_cron_prompt)) },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(
                    text = stringResource(R.string.setting_page_cron_type),
                    style = MaterialTheme.typography.bodyMedium,
                )
                val options = listOf(CronJobType.CRON, CronJobType.BOARD)
                options.forEach { option ->
                    FilterChip(
                        selected = type == option,
                        onClick = { type = option },
                        label = {
                            Text(
                                stringResource(
                                    when (option) {
                                        CronJobType.CRON -> R.string.setting_page_cron_type_cron
                                        CronJobType.BOARD -> R.string.setting_page_cron_type_board
                                    },
                                ),
                            )
                        },
                    )
                }
            }
            if (error != null) {
                Text(
                    text = error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = {
                        if (name.isBlank()) {
                            error = "请输入任务名称"
                            return@Button
                        }
                        if (!CronParser.isValidExpression(cronExpr.trim())) {
                            error = "cron 表达式无效"
                            return@Button
                        }
                        onSave(name.trim(), cronExpr.trim(), prompt.trim(), type)
                    },
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        }
    }
}
