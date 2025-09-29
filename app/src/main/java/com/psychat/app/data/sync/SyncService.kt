package com.psychat.app.data.sync

import com.google.gson.Gson
import com.psychat.app.data.local.dao.SyncTaskDao
import com.psychat.app.data.network.NetworkMonitor
import com.psychat.app.data.remote.api.AnthropicApiService
import com.psychat.app.data.remote.dto.BatchSyncRequest
import com.psychat.app.data.remote.dto.BatchItem
import com.psychat.app.domain.model.PayloadType
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import com.psychat.app.domain.model.SyncStatus
import com.psychat.app.domain.model.SyncTask
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import java.time.LocalDateTime
import com.psychat.app.data.sync.SyncScheduler
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 同步服务
 * 负责管理本地数据与服务端的同步
 */
@Singleton
class SyncService @Inject constructor(
    private val syncTaskDao: SyncTaskDao,
    private val anthropicApi: AnthropicApiService,
    private val gson: Gson,
    private val networkMonitor: NetworkMonitor,
    @ApplicationContext private val context: Context
) {
    
    /**
     * 创建同步任务
     * 将需要同步的数据加入队列
     */
    suspend fun createSyncTask(
        payloadType: String,
        payloadData: Any,
        priority: Int = 0
    ): String {
        val taskId = UUID.randomUUID().toString()
        val payloadJson = gson.toJson(payloadData)
        
        val syncTask = SyncTask(
            id = taskId,
            payloadType = payloadType,
            payloadJson = payloadJson,
            status = SyncStatus.PENDING,
            retryCount = 0,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        
        syncTaskDao.insertSyncTask(syncTask)
        Timber.d("Created sync task: $taskId, type: $payloadType")
        
        // 立即触发一次性同步（如果有网络）
        try {
            if (networkMonitor.isNetworkAvailable()) {
                SyncScheduler.scheduleOneTimeSync(context, 0, TimeUnit.SECONDS)
                Timber.d("Triggered immediate sync for task: $taskId")
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to trigger immediate sync, will wait for periodic sync")
        }
        
        return taskId
    }
    
    /**
     * 执行同步任务
     * 处理单个同步任务
     */
    suspend fun executeSyncTask(taskId: String): Boolean {
        val task = syncTaskDao.getSyncTaskById(taskId) ?: return false
        
        try {
            // 更新状态为进行中
            val updatedTask = task.copy(
                status = SyncStatus.IN_PROGRESS,
                updatedAt = LocalDateTime.now()
            )
            syncTaskDao.updateSyncTask(updatedTask)
            
            // 根据类型执行不同的同步逻辑
            val success = when (task.payloadType) {
                PayloadType.MESSAGE -> syncMessage(task)
                PayloadType.CONVERSATION -> syncConversation(task)
                PayloadType.USER_ACTION -> syncUserAction(task)
                else -> {
                    Timber.w("Unknown payload type: ${task.payloadType}")
                    false
                }
            }
            
            if (success) {
                // 同步成功
                val completedTask = task.copy(
                    status = SyncStatus.COMPLETED,
                    updatedAt = LocalDateTime.now()
                )
                syncTaskDao.updateSyncTask(completedTask)
                Timber.d("Sync task completed: $taskId")
                return true
            } else {
                // 同步失败，增加重试次数
                handleSyncFailure(task, "Sync execution failed")
                return false
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error executing sync task: $taskId")
            handleSyncFailure(task, e.message ?: "Unknown error")
            return false
        }
    }
    
    /**
     * 批量执行待同步任务
     */
    suspend fun executePendingTasks(): Int {
        val pendingTasks = syncTaskDao.getPendingSyncTasks()
        var successCount = 0
        
        for (task in pendingTasks) {
            if (executeSyncTask(task.id)) {
                successCount++
            }
        }
        
        Timber.d("Executed ${pendingTasks.size} tasks, $successCount succeeded")
        return successCount
    }
    
    /**
     * 重试失败的任务
     */
    suspend fun retryFailedTasks(): Int {
        val retryableTasks = syncTaskDao.getRetryableTasks()
        var successCount = 0
        
        for (task in retryableTasks) {
            if (executeSyncTask(task.id)) {
                successCount++
            }
        }
        
        Timber.d("Retried ${retryableTasks.size} tasks, $successCount succeeded")
        return successCount
    }
    
    /**
     * 观察待同步任务数量
     */
    fun observePendingTaskCount(): Flow<Int> {
        return syncTaskDao.observePendingTaskCount()
    }
    
    /**
     * 清理已完成的任务
     */
    suspend fun cleanupTasks() {
        syncTaskDao.cleanupCompletedTasks()
        syncTaskDao.cleanupFailedTasks()
        Timber.d("Cleaned up old sync tasks")
    }
    
    /**
     * 处理同步失败
     */
    private suspend fun handleSyncFailure(task: SyncTask, errorMessage: String) {
        val newRetryCount = task.retryCount + 1
        val newStatus = if (newRetryCount >= task.maxRetries) {
            SyncStatus.FAILED
        } else {
            SyncStatus.PENDING  // 可以重试
        }
        
        val failedTask = task.copy(
            status = newStatus,
            retryCount = newRetryCount,
            updatedAt = LocalDateTime.now(),
            errorMessage = errorMessage
        )
        
        syncTaskDao.updateSyncTask(failedTask)
        
        if (newStatus == SyncStatus.FAILED) {
            Timber.e("Sync task permanently failed: ${task.id}, error: $errorMessage")
        } else {
            Timber.w("Sync task failed, will retry: ${task.id}, attempt: $newRetryCount")
        }
    }
    
    /**
     * 同步消息到Cloudflare Workers
     */
    private suspend fun syncMessage(task: SyncTask): Boolean {
        try {
            // 解析消息数据
            val messageData = gson.fromJson(task.payloadJson, Map::class.java)
            
            // 构建批量同步请求
            val batchRequest = BatchSyncRequest(
                user_id = getCurrentUserId(),
                batch = listOf(
                    BatchItem(
                        type = "message",
                        data = messageData
                    )
                ),
                device_info = getDeviceInfo(),
                app_version = getAppVersion()
            )
            
            // 调用新的批量同步API
            val response = anthropicApi.logBatch(batchRequest)
            
            if (response.isSuccessful) {
                val responseBody = response.body()
                Timber.d("Message sync successful: ${responseBody?.message}")
                return responseBody?.success == true
            } else {
                Timber.e("Message sync failed: ${response.code()}")
                return false
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync message")
            return false
        }
    }
    
    /**
     * 同步对话
     */
    private suspend fun syncConversation(task: SyncTask): Boolean {
        // TODO: 实现对话同步逻辑
        Timber.d("Syncing conversation: ${task.id}")
        return true
    }
    
    /**
     * 同步用户行为
     */
    private suspend fun syncUserAction(task: SyncTask): Boolean {
        // TODO: 实现用户行为同步逻辑
        Timber.d("Syncing user action: ${task.id}")
        return true
    }
    
    /**
     * 获取当前用户ID（伪匿名）
     */
    private fun getCurrentUserId(): String {
        val sharedPrefs = context.getSharedPreferences("psychat_prefs", Context.MODE_PRIVATE)
        var userId = sharedPrefs.getString("user_id", null)
        
        if (userId == null) {
            userId = UUID.randomUUID().toString()
            sharedPrefs.edit().putString("user_id", userId).apply()
        }
        
        return userId
    }
    
    /**
     * 获取设备信息（脱敏后）
     */
    private fun getDeviceInfo(): String {
        val deviceInfo = mapOf(
            "platform" to "Android",
            "osVersion" to Build.VERSION.RELEASE,
            "deviceModel" to Build.MODEL,
            "manufacturer" to Build.MANUFACTURER,
            "screenSize" to getScreenSize()
        )
        
        return gson.toJson(deviceInfo)
    }
    
    /**
     * 获取应用版本
     */
    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    /**
     * 获取屏幕尺寸信息
     */
    private fun getScreenSize(): String {
        return try {
            val displayMetrics = context.resources.displayMetrics
            val screenInfo = mapOf(
                "width" to displayMetrics.widthPixels,
                "height" to displayMetrics.heightPixels,
                "density" to displayMetrics.density
            )
            gson.toJson(screenInfo)
        } catch (e: Exception) {
            "unknown"
        }
    }
}
