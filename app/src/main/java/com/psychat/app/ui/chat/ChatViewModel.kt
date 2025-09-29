package com.psychat.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psychat.app.data.network.NetworkMonitor
import com.psychat.app.data.repository.ChatRepository
import com.psychat.app.data.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    // Step 4: 新增的状态管理
    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()
    
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
                _state.update { it.copy(conversationId = conversationId) }
                observeMessages(conversationId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to create conversation")
                _uiState.update { it.copy(errorMessage = "创建对话失败") }
                _state.update { it.copy(error = "创建对话失败") }
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
                    _state.update { it.copy(error = "加载消息失败") }
                }
                .collect { messages ->
                    _uiState.update { it.copy(messages = messages) }
                    _state.update { it.copy(messages = messages, error = null) }
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
    
    // Step 4: 新增的简化发送方法 - 写本地 → 请求 → 写AI消息 → 更新state
    fun send(text: String) {
        val conversationId = currentConversationId ?: return
        
        if (text.isBlank()) return
        
        viewModelScope.launch {
            try {
                // 1. 更新loading状态
                _state.update { 
                    it.copy(
                        loading = true, 
                        error = null,
                        isTyping = true
                    ) 
                }
                
                // 2. 写本地用户消息
                chatRepository.addLocalUserMessage(text, conversationId)
                Timber.d("Added local user message: $text")
                
                // 3. 请求AI回复
                val aiResponse = chatRepository.sendToModel(text, conversationId)
                Timber.d("Got AI response: $aiResponse")
                
                // 4. 写AI消息到本地
                chatRepository.addLocalAiMessage(aiResponse, conversationId)
                Timber.d("Added local AI message")
                
                // 5. 清除loading状态
                _state.update { 
                    it.copy(
                        loading = false,
                        isTyping = false,
                        error = null
                    ) 
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to send message: $text")
                _state.update { 
                    it.copy(
                        loading = false,
                        isTyping = false,
                        error = "发送消息失败: ${e.message}"
                    ) 
                }
            }
        }
    }
    
    // 清除错误状态
    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
