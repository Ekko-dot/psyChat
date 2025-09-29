package com.psychat.app.common.di

import android.content.Context
import androidx.room.Room
import com.psychat.app.data.local.PsyChatDatabase
import com.psychat.app.data.local.dao.ConversationDao
import com.psychat.app.data.local.dao.MessageDao
import com.psychat.app.data.local.dao.SyncTaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PsyChatDatabase {
        return Room.databaseBuilder(
            context,
            PsyChatDatabase::class.java,
            PsyChatDatabase.DATABASE_NAME
        )
        .fallbackToDestructiveMigration() // For development only
        .build()
    }
    
    @Provides
    fun provideMessageDao(database: PsyChatDatabase): MessageDao {
        return database.messageDao()
    }
    
    @Provides
    fun provideConversationDao(database: PsyChatDatabase): ConversationDao {
        return database.conversationDao()
    }
    
    @Provides
    fun provideSyncTaskDao(database: PsyChatDatabase): SyncTaskDao {
        return database.syncTaskDao()
    }
}
