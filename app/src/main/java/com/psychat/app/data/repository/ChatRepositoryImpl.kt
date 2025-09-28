package com.psychat.app.data.repository

import com.psychat.app.data.local.dao.ConversationDao
import com.psychat.app.data.local.dao.MessageDao
import com.psychat.app.data.remote.api.AnthropicApiService
import com.psychat.app.data.remote.dto.AnthropicMessage
import com.psychat.app.data.remote.dto.AnthropicRequest
import com.psychat.app.domain.model.Conversation
import com.psychat.app.domain.model.Message
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao,
    private val anthropicApi: AnthropicApiService
) : ChatRepository {
    
    override fun observeMessages(conversationId: String): Flow<List<Message>> {
        return messageDao.observeMessages(conversationId)
    }
    
    override fun observeConversations(): Flow<List<Conversation>> {
        return conversationDao.observeActiveConversations()
    }
    
    override suspend fun createConversation(title: String): String {
        val conversationId = UUID.randomUUID().toString()
        val now = LocalDateTime.now()
        
        val conversation = Conversation(
            id = conversationId,
            title = title,
            createdAt = now,
            updatedAt = now
        )
        
        conversationDao.insertConversation(conversation)
        Timber.d("Created conversation: $conversationId")
        
        return conversationId
    }
    
    override suspend fun sendMessage(
        conversationId: String,
        content: String,
        isVoiceInput: Boolean
    ): Result<Message> {
        return try {
            val now = LocalDateTime.now()
            val messageId = UUID.randomUUID().toString()
            
            // Save user message locally
            val userMessage = Message(
                id = messageId,
                conversationId = conversationId,
                content = content,
                isFromUser = true,
                timestamp = now,
                isVoiceInput = isVoiceInput,
                isSynced = false
            )
            
            messageDao.insertMessage(userMessage)
            conversationDao.incrementMessageCount(conversationId, now)
            
            // Get conversation history for context
            val messages = messageDao.getMessages(conversationId)
            val anthropicMessages = messages.map { message ->
                AnthropicMessage(
                    role = if (message.isFromUser) "user" else "assistant",
                    content = message.content
                )
            }
            
            // Call Anthropic API
            val request = AnthropicRequest(messages = anthropicMessages)
            val response = anthropicApi.sendMessage(
                apiKey = com.psychat.app.BuildConfig.ANTHROPIC_API_KEY,
                request = request
            )
            
            if (response.isSuccessful && response.body() != null) {
                val aiResponse = response.body()!!
                val aiContent = aiResponse.content.firstOrNull()?.text ?: "Sorry, I couldn't process that."
                
                // Save AI response locally
                val aiMessage = Message(
                    id = UUID.randomUUID().toString(),
                    conversationId = conversationId,
                    content = aiContent,
                    isFromUser = false,
                    timestamp = LocalDateTime.now(),
                    isVoiceInput = false,
                    isSynced = true
                )
                
                messageDao.insertMessage(aiMessage)
                messageDao.markMessageAsSynced(messageId)
                conversationDao.incrementMessageCount(conversationId, LocalDateTime.now())
                
                Result.success(aiMessage)
            } else {
                val errorMessage = "API Error: ${response.code()}"
                Timber.e(errorMessage)
                Result.failure(Exception(errorMessage))
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to send message")
            Result.failure(e)
        }
    }
    
    override suspend fun getConversation(conversationId: String): Conversation? {
        return conversationDao.getConversation(conversationId)
    }
    
    override suspend fun deleteConversation(conversationId: String) {
        messageDao.deleteMessagesForConversation(conversationId)
        conversationDao.getConversation(conversationId)?.let { conversation ->
            conversationDao.deleteConversation(conversation)
        }
    }
}
