package me.rerere.rikkahub.ui.components.ui

import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas

/**
 * 重组追踪模式。通过 [LocalPerfTraceMode] 控制，默认 [PerfTraceMode.None]（零开销）。
 *
 * - [PerfTraceMode.None]：不追踪，Modifier 直接透传。
 * - [PerfTraceMode.Log]：每次重组向 logcat 打印 `PerfTrace(<tag>) #<count>`。
 * - [PerfTraceMode.Overlay]：在组件右上角叠加红色角标显示累计重组次数。
 */
enum class PerfTraceMode {
    None,
    Log,
    Overlay,
}

val LocalPerfTraceMode = compositionLocalOf { PerfTraceMode.None }

/**
 * 统计重组次数的轻量持有者。
 *
 * [count] 是普通可变字段（**非** Compose State），因此递增不会触发二次重组；
 * 同时它被 [remember] 持有，在跳过（skip）重组时调用方不再执行 [perfTrace]，
 * 计数不会增长，从而能精确反映"该组件被实际重组了多少次"。
 */
private class RecompositionCounter {
    var count: Int = 0
}

/**
 * 可插桩的重组追踪 Modifier，用于定位聊天/列表中的重组热点。
 *
 * 用法：
 * ```kotlin
 * CompositionLocalProvider(LocalPerfTraceMode provides PerfTraceMode.Overlay) {
 *     MessageItem(...) { Modifier.perfTrace("MessageItem") }
 * }
 * ```
 *
 * 注意：调用点必须位于 Composable 函数体内（Modifier 在此创建），这样
 * 每次重组才会重新执行 [perfTrace] 并累加计数；若把 Modifier 提升到
 * 外层记忆化，则无法正确统计。
 */
@Composable
fun Modifier.perfTrace(tag: String): Modifier {
    val mode = LocalPerfTraceMode.current
    if (mode == PerfTraceMode.None) return this

    val density = LocalDensity.current

    val counter = remember(tag) { RecompositionCounter() }
    counter.count++
    val count = counter.count

    if (mode == PerfTraceMode.Log) {
        android.util.Log.d("PerfTrace", "$tag #$count")
        return this
    }

    // Overlay：绘制计数角标
    return this.drawWithContent {
        drawContent()
        drawIntoCanvas { canvas ->
            val text = count.toString()
            val textSizePx = 11f * density.density
            val paint = Paint().apply {
                color = AndroidColor.RED
                this.textSize = textSizePx
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }
            val pad = 3f * density.density
            val tw = paint.measureText(text)
            val badgeW = tw + pad * 2
            val badgeH = textSizePx + pad * 2
            val right = size.width
            val top = 0f
            canvas.nativeCanvas.drawRect(
                right - badgeW,
                top,
                right,
                top + badgeH,
                Paint().apply { color = AndroidColor.argb(160, 0, 0, 0) },
            )
            canvas.nativeCanvas.drawText(
                text,
                right - badgeW + pad,
                top + pad + textSizePx,
                paint,
            )
        }
    }
}
