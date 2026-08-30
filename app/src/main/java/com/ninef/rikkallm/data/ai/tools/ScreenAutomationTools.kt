package com.ninef.rikkallm.data.ai.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import com.ninef.rikkallm.service.ScreenAutomationService

private fun notEnabled(): String =
    "屏幕自动化服务未启用。请在系统「设置 → 无障碍」中开启 RikkaLLM 的屏幕自动化服务后重试。"

private fun stringProp(description: String) = buildJsonObject {
    put("type", "string")
    put("description", description)
}

/**
 * 屏幕自动化工具族：把当前屏幕暴露给 Agent，并支持点击、输入、滚动、全局动作。
 *
 * 这些动作会改变设备或 App 状态，默认均需要用户确认（[Tool.needsApproval]）。
 * 读屏（screen_read）为只读，无需确认。
 */
fun createScreenAutomationTools(): List<Tool> {
    val svc: () -> ScreenAutomationService? = { ScreenAutomationService.getInstance() }

    val readScreen = Tool(
        name = "screen_read",
        description = "Read the current on-screen UI tree (controls, texts, descriptions, clickable/editable flags) as text. Use it first to understand what is on screen before tapping or typing. Read-only.",
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {}, required = emptyList())
        },
        needsApproval = { false },
        execute = {
            val service = svc() ?: return@Tool listOf(UIMessagePart.Text(notEnabled()))
            listOf(UIMessagePart.Text(service.readScreen()))
        },
    )

    val tap = Tool(
        name = "screen_tap",
        description = "Tap a UI element on the current screen by its visible text or content description. Requires confirmation.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("text", stringProp("The visible text or content description of the element to tap."))
                    put("substring", stringProp("Optional 'true' (default) to match as substring, or 'false' for exact match."))
                },
                required = listOf("text"),
            )
        },
        needsApproval = { true },
        execute = { args ->
            val obj = args as? JsonObject
            val text = (obj?.get("text") as? JsonPrimitive)?.contentOrNull
                ?: return@Tool listOf(UIMessagePart.Text("[screen_tap] 缺少参数 text"))
            val substring = (obj?.get("substring") as? JsonPrimitive)?.contentOrNull?.equals("false", true) != true
            val service = svc() ?: return@Tool listOf(UIMessagePart.Text(notEnabled()))
            val ok = service.tapByText(text, substring)
            listOf(UIMessagePart.Text(if (ok) "已点击「$text」" else "未找到可点击的「$text」"))
        },
    )

    val type = Tool(
        name = "screen_type",
        description = "Type/input text into the focused or first editable field on the current screen. Requires confirmation.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("text", stringProp("The text to input."))
                },
                required = listOf("text"),
            )
        },
        needsApproval = { true },
        execute = { args ->
            val obj = args as? JsonObject
            val text = (obj?.get("text") as? JsonPrimitive)?.contentOrNull
                ?: return@Tool listOf(UIMessagePart.Text("[screen_type] 缺少参数 text"))
            val service = svc() ?: return@Tool listOf(UIMessagePart.Text(notEnabled()))
            val ok = service.typeText(text)
            listOf(UIMessagePart.Text(if (ok) "已输入文本" else "未找到可输入的目标（请先聚焦输入框）"))
        },
    )

    val scroll = Tool(
        name = "screen_scroll",
        description = "Scroll the current screen. direction: up/down/left/right. Requires confirmation.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("direction", stringProp("Scroll direction: up, down, left or right."))
                },
                required = listOf("direction"),
            )
        },
        needsApproval = { true },
        execute = { args ->
            val obj = args as? JsonObject
            val dir = (obj?.get("direction") as? JsonPrimitive)?.contentOrNull
                ?: return@Tool listOf(UIMessagePart.Text("[screen_scroll] 缺少参数 direction"))
            val service = svc() ?: return@Tool listOf(UIMessagePart.Text(notEnabled()))
            val ok = service.scroll(dir)
            listOf(UIMessagePart.Text(if (ok) "已滚动（$dir）" else "滚动失败或该方向不可滚动"))
        },
    )

    val action = Tool(
        name = "screen_action",
        description = "Perform a global system action: home, back, recents, notifications, quick_settings. Requires confirmation.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("action", stringProp("One of: home, back, recents, notifications, quick_settings."))
                },
                required = listOf("action"),
            )
        },
        needsApproval = { true },
        execute = { args ->
            val obj = args as? JsonObject
            val act = (obj?.get("action") as? JsonPrimitive)?.contentOrNull
                ?: return@Tool listOf(UIMessagePart.Text("[screen_action] 缺少参数 action"))
            val service = svc() ?: return@Tool listOf(UIMessagePart.Text(notEnabled()))
            val ok = service.globalAction(act)
            listOf(UIMessagePart.Text(if (ok) "已执行全局动作（$act）" else "无法执行动作（$act）"))
        },
    )

    return listOf(readScreen, tap, type, scroll, action)
}
