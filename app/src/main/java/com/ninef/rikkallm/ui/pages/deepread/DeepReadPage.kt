package com.ninef.rikkallm.ui.pages.deepread

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.BookOpen01
import me.rerere.hugeicons.stroke.Clock02
import me.rerere.hugeicons.stroke.Delete01
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ninef.rikkallm.data.deepread.DeepReadRequest
import com.ninef.rikkallm.ui.components.richtext.MarkdownBlock
import com.ninef.rikkallm.ui.context.LocalNavController
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeepReadPage(vm: DeepReadVM = koinViewModel()) {
    val navController = LocalNavController.current
    val reports by vm.reports.collectAsStateWithLifecycle()
    val isRunning by vm.isRunning.collectAsStateWithLifecycle()
    val stage by vm.stage.collectAsStateWithLifecycle()
    val report by vm.currentReport.collectAsStateWithLifecycle()
    var showHistory by remember { mutableStateOf(false) }

    var url by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("zh-CN") }
    var title by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("深度阅读") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(HugeIcons.ArrowLeft01, null)
                    }
                },
                actions = {
                    IconButton(onClick = { showHistory = true }) {
                        Icon(HugeIcons.Clock02, null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("材料 URL（可选，优先级高于下方文本）") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text("或直接粘贴材料文本") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                        )
                        OutlinedTextField(
                            value = language,
                            onValueChange = { language = it },
                            label = { Text("输出语言（如 zh-CN / en）") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("标题（可选，留空由模型推断）") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Button(
                            onClick = {
                                vm.generate(
                                    DeepReadRequest(
                                        materialUrl = url.trim(),
                                        materialText = text.trim(),
                                        language = language.trim().ifBlank { "zh-CN" },
                                        title = title.trim(),
                                    ),
                                )
                            },
                            enabled = !isRunning && (url.isNotBlank() || text.isNotBlank()),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (isRunning) {
                                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                                Text(stage.ifBlank { "生成中…" })
                            } else {
                                Text("生成深度阅读报告")
                            }
                        }
                        if (isRunning && stage.isNotBlank()) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(stage, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            report?.let { r ->
                item {
                    Text(r.title, style = MaterialTheme.typography.headlineSmall)
                    Text("来源：${r.source}", style = MaterialTheme.typography.bodySmall)
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("摘要", style = MaterialTheme.typography.titleMedium)
                            MarkdownBlock(content = r.summary)
                        }
                    }
                }
                items(r.sections, key = { it.title }) { section ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(section.title, style = MaterialTheme.typography.titleMedium)
                            MarkdownBlock(content = section.content)
                        }
                    }
                }
                if (r.keyPoints.isNotEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("核心观点", style = MaterialTheme.typography.titleMedium)
                                r.keyPoints.forEachIndexed { i, p ->
                                    ListItem(
                                        headlineContent = { Text(p) },
                                        leadingContent = { Text("${i + 1}") },
                                    )
                                }
                            }
                        }
                    }
                }
                if (r.evidence.isNotEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("关键论据（原文引述）", style = MaterialTheme.typography.titleMedium)
                                r.evidence.forEach { q ->
                                    Text("> $q", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text("历史深度阅读报告") },
            text = {
                if (reports.isEmpty()) {
                    Text("还没有保存的报告。")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(reports, key = { it.id }) { r ->
                            ListItem(
                                headlineContent = { Text(r.title) },
                                supportingContent = { Text(r.source) },
                                leadingContent = { Icon(HugeIcons.BookOpen01, null) },
                                trailingContent = {
                                    IconButton(onClick = { vm.deleteReport(r.id) }) {
                                        Icon(HugeIcons.Delete01, null)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Box(Modifier.fillMaxWidth()) {
                                TextButton(onClick = {
                                    vm.loadReport(r.id)
                                    showHistory = false
                                }) {
                                    Text("查看")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistory = false }) { Text("关闭") }
            },
        )
    }
}
