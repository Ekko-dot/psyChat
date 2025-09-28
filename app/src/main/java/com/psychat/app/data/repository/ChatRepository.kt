package com.psychat.app.data.repository

import com.psychat.app.domain.model.Conversation
import com.psychat.app.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    
    fun observeMessages(conversationId: String): Flow<List<Message>>
    
    fun observeConversations(): Flow<List<Conversation>>
    
    suspend fun createConversation(title: String): String
    
    suspend fun sendMessage(
        conversationId: String,
        content: String,
        isVoiceInput: Boolean = false
    ): Result<Message>
    
    suspend fun getConversation(conversationId: String): Conversation?
    
    suspend fun deleteConversation(conversationId: String)
}
