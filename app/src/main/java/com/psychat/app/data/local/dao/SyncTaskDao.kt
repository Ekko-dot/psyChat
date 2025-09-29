package com.psychat.app.data.local.dao

import androidx.room.*
import com.psychat.app.domain.model.SyncTask
import com.psychat.app.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * 同步任务数据访问对象
 */
@Dao
interface SyncTaskDao {
    
    /**
     * 插入同步任务
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncTask(syncTask: SyncTask)
    
    /**
     * 批量插入同步任务
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncTasks(syncTasks: List<SyncTask>)
    
    /**
     * 更新同步任务
     */
    @Update
    suspend fun updateSyncTask(syncTask: SyncTask)
    
    /**
     * 删除同步任务
     */
    @Delete
    suspend fun deleteSyncTask(syncTask: SyncTask)
    
    /**
     * 根据ID获取同步任务
     */
    @Query("SELECT * FROM sync_tasks WHERE id = :id")
    suspend fun getSyncTaskById(id: String): SyncTask?
    
    /**
     * 获取所有待同步的任务
     */
    @Query("SELECT * FROM sync_tasks WHERE status = :status ORDER BY createdAt ASC")
    suspend fun getPendingSyncTasks(status: SyncStatus = SyncStatus.PENDING): List<SyncTask>
    
    /**
     * 获取失败的任务（可重试）
     */
    @Query("SELECT * FROM sync_tasks WHERE status = :status AND retryCount < maxRetries ORDER BY updatedAt ASC")
    suspend fun getRetryableTasks(status: SyncStatus = SyncStatus.FAILED): List<SyncTask>
    
    /**
     * 观察待同步任务数量
     */
    @Query("SELECT COUNT(*) FROM sync_tasks WHERE status IN (:statuses)")
    fun observePendingTaskCount(statuses: List<SyncStatus> = listOf(SyncStatus.PENDING, SyncStatus.IN_PROGRESS)): Flow<Int>
    
    /**
     * 根据负载类型获取任务
     */
    @Query("SELECT * FROM sync_tasks WHERE payloadType = :payloadType AND status = :status")
    suspend fun getTasksByType(payloadType: String, status: SyncStatus): List<SyncTask>
    
    /**
     * 清理已完成的任务（保留最近7天）
     */
    @Query("DELETE FROM sync_tasks WHERE status = :status AND updatedAt < datetime('now', '-7 days')")
    suspend fun cleanupCompletedTasks(status: SyncStatus = SyncStatus.COMPLETED)
    
    /**
     * 清理失败且超过最大重试次数的任务
     */
    @Query("DELETE FROM sync_tasks WHERE status = :status AND retryCount >= maxRetries AND updatedAt < datetime('now', '-1 days')")
    suspend fun cleanupFailedTasks(status: SyncStatus = SyncStatus.FAILED)
    
    /**
     * 获取所有任务（用于调试）
     */
    @Query("SELECT * FROM sync_tasks ORDER BY createdAt DESC")
    suspend fun getAllTasks(): List<SyncTask>
}
