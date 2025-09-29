package com.psychat.app.ui.chat

import com.psychat.app.domain.model.Message

/**
 * 聊天界面状态
 * 遵循MVVM单向数据流原则
 */
data class ChatState(
    val loading: Boolean = false,
    val messages: List<Message> = emptyList(),
    val error: String? = null,
    val conversationId: String? = null,
    val isTyping: Boolean = false
) {
    /**
     * 是否有错误
     */
    val hasError: Boolean get() = error != null
    
    /**
     * 是否为空对话
     */
    val isEmpty: Boolean get() = messages.isEmpty()
    
    /**
     * 最后一条消息
     */
    val lastMessage: Message? get() = messages.lastOrNull()
}
