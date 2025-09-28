package com.psychat.app.data.local.dao

import androidx.room.*
import com.psychat.app.domain.model.Conversation
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    
    @Query("SELECT * FROM conversations WHERE isArchived = 0 ORDER BY updatedAt DESC")
    fun observeActiveConversations(): Flow<List<Conversation>>
    
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAllConversations(): Flow<List<Conversation>>
    
    @Query("SELECT * FROM conversations WHERE id = :conversationId")
    suspend fun getConversation(conversationId: String): Conversation?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: Conversation)
    
    @Update
    suspend fun updateConversation(conversation: Conversation)
    
    @Delete
    suspend fun deleteConversation(conversation: Conversation)
    
    @Query("UPDATE conversations SET isArchived = 1 WHERE id = :conversationId")
    suspend fun archiveConversation(conversationId: String)
    
    @Query("UPDATE conversations SET messageCount = messageCount + 1, updatedAt = :updatedAt WHERE id = :conversationId")
    suspend fun incrementMessageCount(conversationId: String, updatedAt: java.time.LocalDateTime)
}
