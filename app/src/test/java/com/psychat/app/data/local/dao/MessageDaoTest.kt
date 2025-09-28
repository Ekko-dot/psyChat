package com.psychat.app.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.psychat.app.data.local.PsyChatDatabase
import com.psychat.app.domain.model.Conversation
import com.psychat.app.domain.model.Message
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class MessageDaoTest {
    
    private lateinit var database: PsyChatDatabase
    private lateinit var messageDao: MessageDao
    private lateinit var conversationDao: ConversationDao
    
    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PsyChatDatabase::class.java
        ).allowMainThreadQueries().build()
        
        messageDao = database.messageDao()
        conversationDao = database.conversationDao()
    }
    
    @After
    fun teardown() {
        database.close()
    }
    
    @Test
    fun insertAndRetrieveMessage() = runTest {
        // Given
        val conversationId = "test-conversation-1"
        val conversation = Conversation(
            id = conversationId,
            title = "Test Conversation",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        
        val message = Message(
            id = "test-message-1",
            conversationId = conversationId,
            content = "Hello, this is a test message",
            isFromUser = true,
            timestamp = LocalDateTime.now(),
            isVoiceInput = false,
            isSynced = false
        )
        
        // When
        conversationDao.insertConversation(conversation)
        messageDao.insertMessage(message)
        
        // Then
        val messages = messageDao.observeMessages(conversationId).first()
        assertEquals(1, messages.size)
        assertEquals(message.content, messages[0].content)
        assertEquals(message.isFromUser, messages[0].isFromUser)
    }
    
    @Test
    fun observeMessagesFlow() = runTest {
        // Given
        val conversationId = "test-conversation-2"
        val conversation = Conversation(
            id = conversationId,
            title = "Test Conversation 2",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        
        conversationDao.insertConversation(conversation)
        
        // When - Initially empty
        var messages = messageDao.observeMessages(conversationId).first()
        assertTrue(messages.isEmpty())
        
        // When - Add a message
        val message1 = Message(
            id = "msg-1",
            conversationId = conversationId,
            content = "First message",
            isFromUser = true,
            timestamp = LocalDateTime.now(),
            isVoiceInput = false,
            isSynced = false
        )
        messageDao.insertMessage(message1)
        
        // Then
        messages = messageDao.observeMessages(conversationId).first()
        assertEquals(1, messages.size)
        
        // When - Add another message
        val message2 = Message(
            id = "msg-2",
            conversationId = conversationId,
            content = "Second message",
            isFromUser = false,
            timestamp = LocalDateTime.now().plusMinutes(1),
            isVoiceInput = false,
            isSynced = true
        )
        messageDao.insertMessage(message2)
        
        // Then
        messages = messageDao.observeMessages(conversationId).first()
        assertEquals(2, messages.size)
        // Messages should be ordered by timestamp
        assertEquals("First message", messages[0].content)
        assertEquals("Second message", messages[1].content)
    }
}
