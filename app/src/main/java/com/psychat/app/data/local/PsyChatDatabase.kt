package com.psychat.app.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.psychat.app.data.local.dao.ConversationDao
import com.psychat.app.data.local.dao.MessageDao
import com.psychat.app.domain.model.Conversation
import com.psychat.app.domain.model.Message

@Database(
    entities = [Message::class, Conversation::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class PsyChatDatabase : RoomDatabase() {
    
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
    
    companion object {
        const val DATABASE_NAME = "psychat_database"
    }
}
