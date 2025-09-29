package com.psychat.app.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.psychat.app.data.local.dao.ConversationDao
import com.psychat.app.data.local.dao.MessageDao
import com.psychat.app.data.local.dao.SyncTaskDao
import com.psychat.app.domain.model.Conversation
import com.psychat.app.domain.model.Message
import com.psychat.app.domain.model.SyncTask

@Database(
    entities = [Message::class, Conversation::class, SyncTask::class],
    version = 2,  // 增加版本号
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class PsyChatDatabase : RoomDatabase() {
    
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
    abstract fun syncTaskDao(): SyncTaskDao
    
    companion object {
        const val DATABASE_NAME = "psychat_database"
    }
}
