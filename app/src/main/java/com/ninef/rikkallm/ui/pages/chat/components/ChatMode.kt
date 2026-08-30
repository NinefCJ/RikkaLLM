package com.ninef.rikkallm.ui.pages.chat.components

/**
 * 聊天界面的两种工作模式：
 * - [AGENT]：原项目的智能体对话模式，界面样式保持不变。
 * - [IDE]：嵌入 VSCodroid 风格的代码编辑与运行能力，用户可通过对话驱动代码生成、修改与执行。
 */
enum class ChatMode {
    AGENT,
    IDE,
}
