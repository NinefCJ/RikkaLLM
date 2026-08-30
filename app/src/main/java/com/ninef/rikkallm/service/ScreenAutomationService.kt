package com.ninef.rikkallm.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

/**
 * 屏幕自动化无障碍服务：把当前屏幕的 UI 树暴露给 Agent，并支持点击、输入、滚动、全局手势等动作。
 *
 * 通过 [getInstance] 提供进程内单例，供 [com.ninef.rikkallm.data.ai.tools.ScreenAutomationTools] 调用。
 * 用户需在系统「无障碍」设置中手动开启本服务（见 [ACTION_ACCESSIBILITY_SETTINGS]）。
 */
class ScreenAutomationService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // 仅按需读取窗口，不常驻处理事件
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance == this) instance = null
        super.onDestroy()
    }

    /** 读取当前窗口 UI 树，序列化为带缩进的文本。 */
    fun readScreen(maxNodes: Int = 400): String {
        val root = rootInActiveWindow ?: return "（无法获取当前窗口，请确认无障碍服务已开启且当前有前台界面）"
        val sb = StringBuilder()
        try {
            traverse(root, sb, 0, maxNodes, intArrayOf(0))
        } finally {
            root.recycle()
        }
        return if (sb.isEmpty()) "（当前窗口无可见节点）" else sb.toString()
    }

    private fun traverse(
        node: AccessibilityNodeInfo,
        sb: StringBuilder,
        depth: Int,
        maxNodes: Int,
        counter: IntArray,
    ) {
        if (counter[0] >= maxNodes) return
        counter[0]++
        val indent = "  ".repeat(depth.coerceAtMost(12))
        val cls = node.className?.toString()?.substringAfterLast('.') ?: "?"
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        val clickable = if (node.isClickable) " clickable" else ""
        val editable = if (node.isEditable) " editable" else ""
        val parts = buildList {
            if (text.isNotBlank()) add("text=\"$text\"")
            if (desc.isNotBlank() && desc != text) add("desc=\"$desc\"")
            if (clickable.isNotBlank()) add("clickable")
            if (editable.isNotBlank()) add("editable")
            node.viewIdResourceName?.let { if (it.isNotBlank()) add("id=$it") }
        }
        sb.appendLine("$indent<$cls${if (parts.isNotEmpty()) " " + parts.joinToString(" ") else ""}>")
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                traverse(child, sb, depth + 1, maxNodes, counter)
                child.recycle()
            }
        }
    }

    /** 按可见文本查找节点并执行点击。返回是否命中。 */
    fun tapByText(text: String, substring: Boolean = true): Boolean {
        val root = rootInActiveWindow ?: return false
        try {
            val target = findNode(root) { n ->
                val t = n.text?.toString().orEmpty()
                val d = n.contentDescription?.toString().orEmpty()
                val hit = if (substring) (t.contains(text, true) || d.contains(text, true))
                else (t.equals(text, true) || d.equals(text, true))
                hit && n.isClickable
            } ?: findNode(root) { n ->
                val t = n.text?.toString().orEmpty()
                val d = n.contentDescription?.toString().orEmpty()
                if (substring) (t.contains(text, true) || d.contains(text, true))
                else (t.equals(text, true) || d.equals(text, true))
            }
            if (target != null) {
                val ok = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                target.recycle()
                return ok
            }
        } finally {
            root.recycle()
        }
        return false
    }

    /** 在可编辑节点中输入文本（优先当前焦点，否则第一个 editable）。 */
    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        try {
            val target = findNode(root) { it.isFocused && it.isEditable }
                ?: findNode(root) { it.isEditable }
                ?: return false
            val args = android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            target.recycle()
            return ok
        } finally {
            root.recycle()
        }
    }

    /** 滚动当前窗口。direction: "up" | "down" | "left" | "right" */
    fun scroll(direction: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val action = when (direction.lowercase()) {
            "up", "left" -> AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD
            "down", "right" -> AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD
            else -> return false
        }
        try {
            val target = findScrollable(root) ?: root
            val ok = target.performAction(action)
            target.recycle()
            return ok
        } finally {
            root.recycle()
        }
    }

    /** 执行全局动作：home / back / recents / notifications */
    fun globalAction(name: String): Boolean {
        val action = when (name.lowercase()) {
            "home" -> GLOBAL_ACTION_HOME
            "back" -> GLOBAL_ACTION_BACK
            "recents", "overview" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> GLOBAL_ACTION_QUICK_SETTINGS
            else -> return false
        }
        return performGlobalAction(action)
    }

    private fun findNode(node: AccessibilityNodeInfo, pred: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (pred(node)) return node
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                val found = findNode(child, pred)
                if (found != null) {
                    child.recycle()
                    return found
                }
                child.recycle()
            }
        }
        return null
    }

    private fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                val found = findScrollable(child)
                if (found != null) {
                    child.recycle()
                    return found
                }
                child.recycle()
            }
        }
        return null
    }

    companion object {
        @Volatile
        private var instance: ScreenAutomationService? = null

        fun getInstance(): ScreenAutomationService? = instance

        fun isEnabled(): Boolean = instance != null
    }
}
