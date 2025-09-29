package com.psychat.app.data.repository

import com.psychat.app.data.local.dao.ConversationDao
import com.psychat.app.data.local.dao.MessageDao
import com.psychat.app.data.network.NetworkMonitor
import com.psychat.app.data.remote.api.AnthropicApiService
import com.psychat.app.data.sync.SyncService
import com.psychat.app.data.remote.dto.AnthropicMessage
import com.psychat.app.data.remote.dto.AnthropicRequest
import com.psychat.app.domain.model.PayloadType
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
    private val anthropicApi: AnthropicApiService,
    private val networkMonitor: NetworkMonitor,
    private val syncService: SyncService
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
            
            // Call Proxy API (no API key needed on client side)
            val request = AnthropicRequest(messages = anthropicMessages)
            Timber.d("Sending request to proxy service with ${anthropicMessages.size} messages")
            
            val response = anthropicApi.sendMessage(request)
            
            Timber.d("API Response: ${response.code()}, Success: ${response.isSuccessful}")
            if (!response.isSuccessful) {
                Timber.e("API Error Body: ${response.errorBody()?.string()}")
            }
            
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
    
    // Step 3: 新增的简化方法实现（支持离线排队）
    override suspend fun sendToModel(text: String, conversationId: String): String {
        try {
            // 检查网络状态
            if (!networkMonitor.isNetworkAvailable()) {
                // 无网络时，创建同步任务排队
                val messageData = mapOf(
                    "text" to text,
                    "conversationId" to conversationId,
                    "timestamp" to LocalDateTime.now().toString()
                )
                
                syncService.createSyncTask(
                    payloadType = PayloadType.MESSAGE,
                    payloadData = messageData
                )
                
                Timber.d("Network unavailable, message queued for sync: $text")
                throw Exception("网络不可用，消息已加入同步队列")
            }
            
            // 获取对话历史
            val messages = messageDao.getMessages(conversationId)
            val anthropicMessages = messages.map { message ->
                AnthropicMessage(
                    role = if (message.isFromUser) "user" else "assistant",
                    content = message.content
                )
            }.toMutableList()
            
            // 添加当前用户消息
            anthropicMessages.add(AnthropicMessage(role = "user", content = text))
            
            // 构建请求
            val request = AnthropicRequest(messages = anthropicMessages)
            Timber.d("Sending to model: $text")
            
            // 调用API (使用统一的sendMessage方法)
            val response = anthropicApi.sendMessage(request)
            
            if (response.isSuccessful && response.body() != null) {
                val aiResponse = response.body()!!
                val aiContent = aiResponse.content.firstOrNull()?.text 
                    ?: throw Exception("Empty response from AI")
                
                Timber.d("AI Response: $aiContent")
                
                // 成功后创建同步任务，用于数据收集
                try {
                    // 为用户消息创建数据
                    val userMessageData = mapOf(
                        "message_id" to UUID.randomUUID().toString(),
                        "conversation_id" to conversationId,
                        "content" to text,
                        "is_from_user" to true,
                        "tokens_in" to text.length,
                        "tokens_out" to null,
                        "model_name" to null,
                        "is_voice_input" to false,
                        "asr_audio_path" to null,
                        "response_time_ms" to null,
                        "error_message" to null
                    )
                    
                    // 为AI回复创建数据
                    val aiMessageData = mapOf(
                        "message_id" to UUID.randomUUID().toString(),
                        "conversation_id" to conversationId,
                        "content" to aiContent,
                        "is_from_user" to false,
                        "tokens_in" to null,
                        "tokens_out" to aiContent.length,
                        "model_name" to "claude-3-7-sonnet-20250219",
                        "is_voice_input" to false,
                        "asr_audio_path" to null,
                        "response_time_ms" to null,
                        "error_message" to null
                    )
                    
                    // 创建用户消息同步任务
                    syncService.createSyncTask(
                        payloadType = PayloadType.MESSAGE,
                        payloadData = userMessageData
                    )
                    
                    // 创建AI回复同步任务
                    syncService.createSyncTask(
                        payloadType = PayloadType.MESSAGE,
                        payloadData = aiMessageData
                    )
                    
                    Timber.d("Created sync task for successful conversation")
                } catch (syncError: Exception) {
                    Timber.w(syncError, "Failed to create sync task, but conversation succeeded")
                }
                
                return aiContent
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Timber.e("API Error: ${response.code()}, Body: $errorBody")
                throw Exception("API Error: ${response.code()}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to send to model")
            throw e
        }
    }
    
    override suspend fun addLocalUserMessage(text: String, conversationId: String) {
        val now = LocalDateTime.now()
        val message = Message(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            content = text,
            isFromUser = true,
            timestamp = now,
            isVoiceInput = false,
            isSynced = false,
            // Step 6: 新增字段
            asrAudioPath = null,  // 用户文本消息无音频
            tokensIn = text.length,  // 简单估算输入token
            tokensOut = null,  // 用户消息无输出token
            modelName = null,  // 用户消息不涉及模型
            createdAt = now
        )
        
        messageDao.insertMessage(message)
        conversationDao.incrementMessageCount(conversationId, now)
        Timber.d("Added user message: $text")
    }
    
    override suspend fun addLocalAiMessage(text: String, conversationId: String) {
        val now = LocalDateTime.now()
        val message = Message(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            content = text,
            isFromUser = false,
            timestamp = now,
            isVoiceInput = false,
            isSynced = true,
            // Step 6: 新增字段
            asrAudioPath = null,  // AI消息无音频
            tokensIn = null,  // AI消息的输入token在用户消息中
            tokensOut = text.length,  // 简单估算输出token
            modelName = "claude-3-7-sonnet-20250219",  // 使用的模型名称
            createdAt = now
        )
        
        messageDao.insertMessage(message)
        conversationDao.incrementMessageCount(conversationId, now)
        Timber.d("Added AI message: $text")
    }
}
