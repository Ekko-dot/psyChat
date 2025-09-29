package com.psychat.app.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.psychat.app.data.local.dao.SyncTaskDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * WorkManager 同步任务
 * 负责在后台执行数据同步
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncService: SyncService,
    private val syncTaskDao: SyncTaskDao
) : CoroutineWorker(context, workerParams) {
    
    override suspend fun doWork(): Result {
        return try {
            Timber.d("Starting sync work...")
            
            // 执行待同步任务
            val pendingCount = syncService.executePendingTasks()
            
            // 重试失败的任务
            val retryCount = syncService.retryFailedTasks()
            
            // 清理旧任务
            syncService.cleanupTasks()
            
            Timber.d("Sync work completed: $pendingCount pending, $retryCount retried")
            
            // 如果还有待同步的任务，调度下一次执行
            scheduleNextSyncIfNeeded()
            
            Result.success()
            
        } catch (e: Exception) {
            Timber.e(e, "Sync work failed")
            
            // 如果是可重试的错误，返回 retry
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
    
    /**
     * 如果还有待同步任务，调度下一次执行
     */
    private suspend fun scheduleNextSyncIfNeeded() {
        val pendingTasks = syncTaskDao.getPendingSyncTasks()
        val retryableTasks = syncTaskDao.getRetryableTasks()
        
        if (pendingTasks.isNotEmpty() || retryableTasks.isNotEmpty()) {
            // 有待同步任务，调度下一次执行（延迟5分钟）
            SyncScheduler.scheduleOneTimeSync(applicationContext, 5, TimeUnit.MINUTES)
        }
    }
    
    companion object {
        const val WORK_NAME = "sync_work"
        const val TAG_SYNC = "sync"
    }
}

/**
 * 同步调度器
 * 负责调度同步任务
 */
object SyncScheduler {
    
    /**
     * 调度周期性同步任务
     */
    fun schedulePeriodicSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)  // 需要网络连接
            .setRequiresBatteryNotLow(true)                 // 电量不能过低
            .build()
        
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            repeatInterval = 15,  // 每15分钟执行一次
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,  // 指数退避
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag(SyncWorker.TAG_SYNC)
            .build()
        
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                SyncWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,  // 保持现有任务
                syncRequest
            )
        
        Timber.d("Scheduled periodic sync work")
    }
    
    /**
     * 调度一次性同步任务
     */
    fun scheduleOneTimeSync(
        context: Context, 
        delay: Long = 0, 
        timeUnit: TimeUnit = TimeUnit.SECONDS
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setInitialDelay(delay, timeUnit)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag(SyncWorker.TAG_SYNC)
            .build()
        
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "one_time_sync_${System.currentTimeMillis()}",
                ExistingWorkPolicy.REPLACE,
                syncRequest
            )
        
        Timber.d("Scheduled one-time sync work with delay: $delay $timeUnit")
    }
    
    /**
     * 立即执行同步
     */
    fun syncNow(context: Context) {
        scheduleOneTimeSync(context, 0, TimeUnit.SECONDS)
    }
    
    /**
     * 取消所有同步任务
     */
    fun cancelAllSync(context: Context) {
        WorkManager.getInstance(context)
            .cancelAllWorkByTag(SyncWorker.TAG_SYNC)
        
        Timber.d("Cancelled all sync work")
    }
    
    /**
     * 观察同步任务状态
     */
    fun observeSyncWorkInfo(context: Context) = 
        WorkManager.getInstance(context)
            .getWorkInfosByTagLiveData(SyncWorker.TAG_SYNC)
}
