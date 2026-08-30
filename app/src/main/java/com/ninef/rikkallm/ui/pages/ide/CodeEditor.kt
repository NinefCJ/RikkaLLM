package com.ninef.rikkallm.ui.pages.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.highlight.CodeHighlighter
import me.rerere.highlight.HighlightToken

val LocalCodeHighlighter = compositionLocalOf { CodeHighlighter() }

/** 高亮配色（与 highlight 模块默认调色板对齐）。 */
private val CODE_PALETTE = mapOf(
    "keyword" to Color(0xFF569CD6),
    "control" to Color(0xFFC586C0),
    "operator" to Color(0xFFD4D4D4),
    "string" to Color(0xFFCE9178),
    "string.escape" to Color(0xFFD7BA7D),
    "comment" to Color(0xFF6A9955),
    "number" to Color(0xFFB5CEA8),
    "constant" to Color(0xFF4FC1FF),
    "function" to Color(0xFFDCDCAA),
    "type" to Color(0xFF4EC9B0),
    "class" to Color(0xFF4EC9B0),
    "variable" to Color(0xFF9CDCFE),
    "property" to Color(0xFF9CDCFE),
    "macro" to Color(0xFFDCDCAA),
    "tag" to Color(0xFF569CD6),
    "attribute" to Color(0xFF9CDCFE),
    "attribute_value" to Color(0xFFCE9178),
    "text" to Color(0xFFD4D4D4),
    "punctuation" to Color(0xFFD4D4D4),
)

private fun colorFor(type: String): Color =
    CODE_PALETTE[type] ?: CODE_PALETTE[type.substringBefore('.')] ?: Color(0xFFD4D4D4)

class HighlightTransformation(
    private val highlighter: CodeHighlighter,
    private val language: String,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (text.text.isEmpty()) {
            return TransformedText(AnnotatedString(""), OffsetMapping.Identity)
        }
        val built = if (text.text.length <= 200_000) {
            val tokens = runCatching { highlighter.highlight(text.text, language) }.getOrDefault(emptyList())
            buildAnnotatedString {
                tokens.forEach { token ->
                    when (token) {
                        is HighlightToken.Plain -> append(token.content)
                        is HighlightToken.Styled -> withStyle(SpanStyle(color = colorFor(token.type))) {
                            append(token.content)
                        }
                    }
                }
            }
        } else {
            AnnotatedString(text.text)
        }
        // 仅当高亮结果长度与原文一致时才应用，避免光标错位。
        val result = if (built.length == text.text.length) built else AnnotatedString(text.text)
        return TransformedText(result, OffsetMapping.Identity)
    }
}

private val GUTTER_WIDTH = 48.dp

@Composable
fun CodeEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    language: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    onCursor: ((line: Int, col: Int) -> Unit)? = null,
    fontSize: Int = 13,
    showLineNumbers: Boolean = true,
    wordWrap: Boolean = false,
) {
    val highlighter = LocalCodeHighlighter.current
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    val lineCount = value.text.count { it == '\n' } + 1

    val textColor = MaterialTheme.colorScheme.onSurface
    val textStyle = remember(language, textColor, fontSize) {
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize.sp,
            lineHeight = (fontSize + 7).sp,
            color = textColor,
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            // 行号 gutter，与编辑器共用同一个 verticalScroll，确保同步滚动。
            if (showLineNumbers) {
                Column(
                    modifier = Modifier
                        .width(GUTTER_WIDTH)
                        .fillMaxHeight()
                        .verticalScroll(vScroll)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                ) {
                    repeat(lineCount) { i ->
                        Text(
                            text = "${i + 1}",
                            style = textStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                VerticalDivider()
            }

            BasicTextField(
                value = value,
                onValueChange = {
                    onValueChange(it)
                    reportCursor(it, onCursor)
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(vScroll)
                    .then(if (wordWrap) Modifier else Modifier.horizontalScroll(hScroll))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                readOnly = readOnly,
                visualTransformation = HighlightTransformation(highlighter, language),
                textStyle = textStyle,
            )
        }

        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        )
    }
}

private fun reportCursor(value: TextFieldValue, onCursor: ((Int, Int) -> Unit)?) {
    onCursor ?: return
    val off = value.selection.start.coerceIn(0, value.text.length)
    val before = value.text.substring(0, off)
    val line = before.count { it == '\n' } + 1
    val lastNewline = before.lastIndexOf('\n')
    val col = if (lastNewline < 0) off + 1 else off - lastNewline
    onCursor(line, col)
}

@Composable
fun ProvideCodeHighlighter(content: @Composable () -> Unit) {
    val highlighter = remember { CodeHighlighter() }
    CompositionLocalProvider(LocalCodeHighlighter provides highlighter) {
        content()
    }
}
