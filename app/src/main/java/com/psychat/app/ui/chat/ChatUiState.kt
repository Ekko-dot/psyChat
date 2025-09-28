package com.psychat.app.ui.chat

import com.psychat.app.domain.model.Message

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val isListening: Boolean = false,
    val currentInput: String = "",
    val conversationId: String? = null,
    val errorMessage: String? = null
)

sealed class ChatUiEvent {
    data class SendMessage(val content: String, val isVoiceInput: Boolean = false) : ChatUiEvent()
    data class UpdateInput(val input: String) : ChatUiEvent()
    object StartVoiceInput : ChatUiEvent()
    object StopVoiceInput : ChatUiEvent()
    object ClearError : ChatUiEvent()
    data class LoadConversation(val conversationId: String) : ChatUiEvent()
}
