package com.psychat.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psychat.app.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    private var currentConversationId: String? = null
    
    init {
        // Create a default conversation on startup
        createNewConversation()
    }
    
    fun onEvent(event: ChatUiEvent) {
        when (event) {
            is ChatUiEvent.SendMessage -> {
                sendMessage(event.content, event.isVoiceInput)
            }
            is ChatUiEvent.UpdateInput -> {
                _uiState.update { it.copy(currentInput = event.input) }
            }
            is ChatUiEvent.StartVoiceInput -> {
                _uiState.update { it.copy(isListening = true) }
                // TODO: Implement voice recognition
            }
            is ChatUiEvent.StopVoiceInput -> {
                _uiState.update { it.copy(isListening = false) }
                // TODO: Stop voice recognition
            }
            is ChatUiEvent.ClearError -> {
                _uiState.update { it.copy(errorMessage = null) }
            }
            is ChatUiEvent.LoadConversation -> {
                loadConversation(event.conversationId)
            }
        }
    }
    
    private fun createNewConversation() {
        viewModelScope.launch {
            try {
                val conversationId = chatRepository.createConversation("新对话")
                currentConversationId = conversationId
                _uiState.update { it.copy(conversationId = conversationId) }
                observeMessages(conversationId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to create conversation")
                _uiState.update { it.copy(errorMessage = "创建对话失败") }
            }
        }
    }
    
    private fun loadConversation(conversationId: String) {
        currentConversationId = conversationId
        _uiState.update { it.copy(conversationId = conversationId) }
        observeMessages(conversationId)
    }
    
    private fun observeMessages(conversationId: String) {
        viewModelScope.launch {
            chatRepository.observeMessages(conversationId)
                .catch { e ->
                    Timber.e(e, "Error observing messages")
                    _uiState.update { it.copy(errorMessage = "加载消息失败") }
                }
                .collect { messages ->
                    _uiState.update { it.copy(messages = messages) }
                }
        }
    }
    
    private fun sendMessage(content: String, isVoiceInput: Boolean) {
        val conversationId = currentConversationId ?: return
        
        if (content.isBlank()) return
        
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    isLoading = true, 
                    currentInput = "",
                    errorMessage = null
                ) 
            }
            
            try {
                val result = chatRepository.sendMessage(
                    conversationId = conversationId,
                    content = content,
                    isVoiceInput = isVoiceInput
                )
                
                result.fold(
                    onSuccess = {
                        Timber.d("Message sent successfully")
                    },
                    onFailure = { error ->
                        Timber.e(error, "Failed to send message")
                        _uiState.update { 
                            it.copy(errorMessage = "发送消息失败: ${error.message}") 
                        }
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error sending message")
                _uiState.update { 
                    it.copy(errorMessage = "发送消息时出现错误") 
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
