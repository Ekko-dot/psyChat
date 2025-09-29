package com.psychat.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 批量同步请求数据类
 * 用于向Cloudflare Workers发送批量数据
 */
data class BatchSyncRequest(
    @SerializedName("user_id")
    val user_id: String,
    
    @SerializedName("batch")
    val batch: List<BatchItem>,
    
    @SerializedName("device_info")
    val device_info: String? = null,
    
    @SerializedName("app_version")
    val app_version: String? = null
)

/**
 * 批量同步项
 */
data class BatchItem(
    @SerializedName("type")
    val type: String,  // "message", "conversation", "usage_stat", "research_event"
    
    @SerializedName("data")
    val data: Any
)

/**
 * 批量同步响应
 */
data class BatchSyncResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("message")
    val message: String? = null,
    
    @SerializedName("processed")
    val processed: Int? = null,
    
    @SerializedName("errors")
    val errors: Int? = null,
    
    @SerializedName("details")
    val details: List<BatchError>? = null
)

/**
 * 批量处理错误详情
 */
data class BatchError(
    @SerializedName("type")
    val type: String,
    
    @SerializedName("error")
    val error: String
)

/**
 * 消息同步数据
 */
data class MessageSyncData(
    @SerializedName("message_id")
    val message_id: String,
    
    @SerializedName("conversation_id")
    val conversation_id: String,
    
    @SerializedName("content")
    val content: String,
    
    @SerializedName("is_from_user")
    val is_from_user: Boolean,
    
    @SerializedName("timestamp")
    val timestamp: Long,
    
    @SerializedName("tokens_in")
    val tokens_in: Int? = null,
    
    @SerializedName("tokens_out")
    val tokens_out: Int? = null,
    
    @SerializedName("model_name")
    val model_name: String? = null,
    
    @SerializedName("is_voice_input")
    val is_voice_input: Boolean = false,
    
    @SerializedName("asr_audio_path")
    val asr_audio_path: String? = null,
    
    @SerializedName("response_time_ms")
    val response_time_ms: Long? = null,
    
    @SerializedName("error_message")
    val error_message: String? = null
)

/**
 * 对话同步数据
 */
data class ConversationSyncData(
    @SerializedName("conversation_id")
    val conversation_id: String,
    
    @SerializedName("title")
    val title: String? = null,
    
    @SerializedName("message_count")
    val message_count: Int = 0,
    
    @SerializedName("total_tokens_in")
    val total_tokens_in: Int = 0,
    
    @SerializedName("total_tokens_out")
    val total_tokens_out: Int = 0,
    
    @SerializedName("is_archived")
    val is_archived: Boolean = false
)

/**
 * 使用统计同步数据
 */
data class UsageStatSyncData(
    @SerializedName("date")
    val date: String,  // YYYY-MM-DD
    
    @SerializedName("messages_sent")
    val messages_sent: Int = 0,
    
    @SerializedName("tokens_consumed")
    val tokens_consumed: Int = 0,
    
    @SerializedName("api_calls")
    val api_calls: Int = 0,
    
    @SerializedName("errors")
    val errors: Int = 0,
    
    @SerializedName("avg_response_time_ms")
    val avg_response_time_ms: Long = 0
)

/**
 * 研究事件同步数据
 */
data class ResearchEventSyncData(
    @SerializedName("event_id")
    val event_id: String,
    
    @SerializedName("event_type")
    val event_type: String,
    
    @SerializedName("event_data")
    val event_data: Map<String, Any>? = null,
    
    @SerializedName("session_id")
    val session_id: String? = null
)
