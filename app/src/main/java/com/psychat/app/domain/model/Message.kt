package com.psychat.app.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import java.time.LocalDateTime

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = Conversation::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId"])]
)
data class Message(
    @PrimaryKey
    val id: String,
    val conversationId: String,
    val content: String,
    val isFromUser: Boolean,
    val timestamp: LocalDateTime,
    val isVoiceInput: Boolean = false,
    val isSynced: Boolean = false,
    val errorMessage: String? = null,
    
    // Step 6: 新增字段
    val asrAudioPath: String? = null,  // 语音识别音频文件路径
    val tokensIn: Int? = null,         // 输入token数量
    val tokensOut: Int? = null,        // 输出token数量
    val modelName: String? = null,     // 使用的模型名称
    val createdAt: LocalDateTime = timestamp  // 创建时间（与timestamp保持一致）
)
