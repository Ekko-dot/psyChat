package com.psychat.app.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * 同步任务实体
 * 用于存储需要同步到服务端的任务
 */
@Entity(tableName = "sync_tasks")
data class SyncTask(
    @PrimaryKey
    val id: String,
    
    /**
     * 负载类型
     * 例如: "message", "conversation", "user_action"
     */
    val payloadType: String,
    
    /**
     * 负载数据（JSON格式）
     * 存储需要同步的具体数据
     */
    val payloadJson: String,
    
    /**
     * 同步状态
     * PENDING: 待同步
     * IN_PROGRESS: 同步中
     * COMPLETED: 已完成
     * FAILED: 失败
     */
    val status: SyncStatus = SyncStatus.PENDING,
    
    /**
     * 重试次数
     */
    val retryCount: Int = 0,
    
    /**
     * 最大重试次数
     */
    val maxRetries: Int = 3,
    
    /**
     * 创建时间
     */
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    /**
     * 最后更新时间
     */
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    
    /**
     * 错误信息
     */
    val errorMessage: String? = null
)

/**
 * 同步状态枚举
 */
enum class SyncStatus {
    PENDING,      // 待同步
    IN_PROGRESS,  // 同步中
    COMPLETED,    // 已完成
    FAILED        // 失败
}

/**
 * 负载类型常量
 */
object PayloadType {
    const val MESSAGE = "message"
    const val CONVERSATION = "conversation"
    const val USER_ACTION = "user_action"
}
